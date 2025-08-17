package com.eewms.controller;

import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseOrderItemDTO;
import com.eewms.dto.purchase.PurchaseOrderMapper;
import com.eewms.dto.purchase.PurchaseProductSelectDTO;
import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.PurchaseOrderItem;
import com.eewms.entities.User;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderItemRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.SupplierRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IPurchaseOrderService;
import com.eewms.services.ImageUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final IPurchaseOrderService orderService;
    private final SupplierRepository supplierRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository orderRepo;
    private final PurchaseOrderItemRepository poItemRepo;
    private final ImageUploadService uploadService;

    /* ====== LIST ====== */
    @GetMapping
    public String list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) PurchaseOrderStatus status,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "8") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("code").ascending());
        Page<PurchaseOrderDTO> orderPage = orderService.searchWithFilters(keyword, status, from, to, pageable);

        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("totalPages", orderPage.getTotalPages());
        model.addAttribute("currentPage", page);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "purchase-order-list";
    }

    /* ====== CREATE FORM ====== */
    @GetMapping("/create")
    public String showCreate(Model model, @AuthenticationPrincipal UserDetails ud) {
        PurchaseOrderDTO dto = new PurchaseOrderDTO();
        if (ud != null) { // ← thêm null-check
            userRepo.findByUsername(ud.getUsername())
                    .ifPresent(u -> dto.setCreatedByName(u.getFullName()));
        }
        model.addAttribute("orderDTO", dto);
        model.addAttribute("suppliers", supplierRepo.findAll());
        model.addAttribute("products", getPurchaseProductDTOs());
        return "purchase-order-form";
    }

    /* ====== CREATE (status set theo ROLE trong service) ====== */
    @PostMapping
    public String create(@ModelAttribute("orderDTO") @Valid PurchaseOrderDTO dto,
                         BindingResult result,
                         @AuthenticationPrincipal UserDetails userDetails,
                         RedirectAttributes redirect,
                         Model model) {
        if (result.hasErrors()) {
            model.addAttribute("suppliers", supplierRepo.findAll());
            model.addAttribute("products", getPurchaseProductDTOs());
            return "purchase-order-form";
        }

        try {
            Optional<User> userOpt = userRepo.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) throw new IllegalArgumentException("Không tìm thấy người dùng");
            dto.setCreatedByName(userOpt.get().getFullName());

            // lọc dòng trống
            if (dto.getItems() != null) {
                dto.setItems(dto.getItems().stream()
                        .filter(i -> i != null && i.getProductId() != null)
                        .collect(Collectors.toList()));
            }
            if (dto.getItems() == null || dto.getItems().isEmpty()) {
                result.reject("items.empty", "Vui lòng chọn ít nhất 1 sản phẩm.");
                model.addAttribute("suppliers", supplierRepo.findAll());
                model.addAttribute("products", getPurchaseProductDTOs());
                return "purchase-order-form";
            }

            PurchaseOrder created = orderService.create(dto);
            redirect.addFlashAttribute("message", "Tạo đơn hàng thành công với mã: " + created.getCode());
            return "redirect:/admin/purchase-orders";
        } catch (Exception e) {
            model.addAttribute("suppliers", supplierRepo.findAll());
            model.addAttribute("products", getPurchaseProductDTOs());
            model.addAttribute("error", e.getMessage());
            return "purchase-order-form";
        }
    }

    /* ====== APPROVE (Manager) ====== */
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id,
                          @AuthenticationPrincipal UserDetails userDetails,
                          RedirectAttributes ra) {
        try {
            String approver = userDetails != null ? userDetails.getUsername() : "SYSTEM";
            orderService.approve(id, approver);
            ra.addFlashAttribute("message", "Đã duyệt đơn hàng.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchase-orders";
    }

    /* ====== CANCEL (có lý do) ====== */
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam("reason") String reason,
                         @AuthenticationPrincipal UserDetails userDetails,
                         RedirectAttributes ra) {
        try {
            String actor = userDetails != null ? userDetails.getUsername() : "SYSTEM";
            orderService.cancel(id, reason, actor);
            ra.addFlashAttribute("message", "Đã huỷ đơn hàng.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchase-orders";
    }

    /* ====== EDIT (hiển thị & nhận đợt) ====== */
    @GetMapping("/edit/{id}")
    public String showEdit(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        PurchaseOrder order = orderService.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        if (order.getStatus() == PurchaseOrderStatus.HOAN_THANH) {
            return "redirect:/admin/purchase-orders/" + id;
        }

        boolean readOnly = order.getStatus() == PurchaseOrderStatus.HOAN_THANH
                || order.getStatus() == PurchaseOrderStatus.HUY;

        PurchaseOrderDTO dto = PurchaseOrderMapper.toDTO(order);
        List<PurchaseOrderItemDTO> itemDTOs = order.getItems().stream()
                .map(item -> PurchaseOrderItemDTO.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .contractQuantity(item.getContractQuantity())
                        .actualQuantity(item.getActualQuantity())
                        .price(item.getPrice())
                        .deliveryQuantity(0)
                        .build())
                .toList();
        dto.setItems(itemDTOs);

        // dữ liệu cho view
        List<PurchaseProductSelectDTO> productDtos = getPurchaseProductDTOs();
        Map<Integer, String> productNameById = productDtos.stream()
                .collect(Collectors.toMap(PurchaseProductSelectDTO::getId, PurchaseProductSelectDTO::getName));

        model.addAttribute("orderDTO", dto);
        model.addAttribute("products", productDtos);
        model.addAttribute("productNameById", productNameById);
        model.addAttribute("suppliers", supplierRepo.findAll()); // ← THÊM DÒNG NÀY
        model.addAttribute("readOnly", readOnly);

        boolean isLockedByStatus = !(order.getStatus() == PurchaseOrderStatus.DA_GIAO_MOT_PHAN
                || order.getStatus() == PurchaseOrderStatus.CHO_GIAO_HANG);
        model.addAttribute("isLockedByStatus", isLockedByStatus);

        return "purchase-order-edit";
    }

    /* ====== RECEIVE (một đợt giao) -> tự sinh GRN ====== */
    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id,
                          @ModelAttribute("orderDTO") PurchaseOrderDTO dto,
                          @AuthenticationPrincipal UserDetails userDetails,
                          RedirectAttributes ra) {
        try {
            String actor = userDetails != null ? userDetails.getUsername() : "SYSTEM";

            // chỉ lấy các dòng có deliveryQuantity > 0
            List<PurchaseOrderItemDTO> lines = Optional.ofNullable(dto.getItems()).orElse(List.of())
                    .stream()
                    .filter(i -> i != null && i.getProductId() != null
                            && i.getDeliveryQuantity() != null && i.getDeliveryQuantity() > 0)
                    .toList();

            if (lines.isEmpty()) {
                ra.addFlashAttribute("error", "Vui lòng nhập 'Giao lần này' (>0) cho ít nhất 1 dòng.");
                return "redirect:/admin/purchase-orders/edit/" + id;
            }

            String requestId = java.util.UUID.randomUUID().toString();
            orderService.receiveDelivery(id, lines, actor, requestId);

            ra.addFlashAttribute("message", "Đã ghi nhận đợt giao hàng & tạo phiếu nhập kho.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchase-orders/edit/" + id;
    }

    /* ====== DETAIL ====== */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        Optional<PurchaseOrder> optional = orderService.findById(id);
        if (optional.isEmpty()) {
            redirect.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/admin/purchase-orders";
        }
        model.addAttribute("order", optional.get());
        return "purchase-order-detail";
    }

    /* ====== HELPERS ====== */
    private List<PurchaseProductSelectDTO> getPurchaseProductDTOs() {
        return productRepository.findAll().stream()
                .map(p -> PurchaseProductSelectDTO.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .originPrice(p.getOriginPrice())
                        .supplierIds(p.getSupplierIds())
                        .build())
                .collect(Collectors.toList());
    }
    //chuyen trang thai nhanh
    @PostMapping("/{id}/fast-complete")
    public String fastComplete(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails userDetails,
                               RedirectAttributes ra) {
        try {
            String actor = userDetails != null ? userDetails.getUsername() : "SYSTEM";
            String requestId = java.util.UUID.randomUUID().toString();
            orderService.fastComplete(id, actor, requestId);
            ra.addFlashAttribute("message", "Đã nhập đủ phần còn lại và hoàn tất đơn hàng.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchase-orders/edit/" + id;
    }

    @PostMapping("/{id}/update-before-approve")
    public String updateBeforeApprove(@PathVariable Long id,
                                      @ModelAttribute("orderDTO") @Valid PurchaseOrderDTO dto,
                                      BindingResult result,
                                      RedirectAttributes ra,
                                      Model model) {
        try {
            dto.setId(id); // đảm bảo đúng id
            // lọc dòng rỗng đề phòng
            if (dto.getItems() != null) {
                dto.setItems(dto.getItems().stream()
                        .filter(i -> i != null && i.getProductId() != null)
                        .collect(Collectors.toList()));
            }
            orderService.updateBeforeApprove(dto);
            ra.addFlashAttribute("message", "Đã cập nhật đơn hàng (trước khi duyệt).");
            return "redirect:/admin/purchase-orders/edit/" + id;
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/purchase-orders/edit/" + id;
        }
    }
}
