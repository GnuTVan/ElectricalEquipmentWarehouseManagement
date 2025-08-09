package com.eewms.controller;

import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseOrderItemDTO;
import com.eewms.dto.purchase.PurchaseOrderMapper;
import com.eewms.dto.purchase.PurchaseProductSelectDTO;
import com.eewms.entities.Product;
import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.PurchaseOrderItem;
import com.eewms.entities.User;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.SupplierRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IPurchaseOrderService;

import com.eewms.services.ImageUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final IPurchaseOrderService orderService;
    private final SupplierRepository supplierRepo;
    private final UserRepository userRepo;
    private final ProductRepository productRepository;
    private final ImageUploadService uploadService;
    private final ProductRepository productRepo;
    private final PurchaseOrderRepository orderRepo;

    // ✅ Hiển thị danh sách đơn hàng nhập
    @GetMapping
    public String listOrders(
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

    // ✅ Hiển thị form tạo đơn hàng
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("orderDTO", new PurchaseOrderDTO());
        model.addAttribute("suppliers", supplierRepo.findAll());
        model.addAttribute("products", getPurchaseProductDTOs());
        return "purchase-order-form";
    }

    // ✅ Xử lý tạo đơn hàng
    @PostMapping
    public String createOrder(@ModelAttribute("orderDTO") PurchaseOrderDTO dto,
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

            //thêm lọc dòng trống
            //  Lọc bỏ dòng trống (không có productId)
            if (dto.getItems() != null) {
                dto.setItems(
                        dto.getItems().stream()
                                .filter(i -> i != null && i.getProductId() != null)
                                .collect(Collectors.toList())
                );
            }

            // Nếu không còn item nào -> báo lỗi
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

    // ✅ Cập nhật trạng thái đơn hàng
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam("status") PurchaseOrderStatus status,
                               RedirectAttributes redirect) {
        try {
            orderService.updateStatus(id, status, null);
            redirect.addFlashAttribute("message", "Cập nhật trạng thái đơn hàng thành công.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/purchase-orders";
    }

    // ✅ (Tuỳ chọn) Hiển thị chi tiết đơn hàng
    @GetMapping("/{id}")
    public String viewDetail(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        Optional<PurchaseOrder> optional = orderService.findById(id);
        if (optional.isEmpty()) {
            redirect.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/admin/purchase-orders";
        }
        model.addAttribute("order", optional.get());
        return "purchase-order-detail";
    }

    // ✅ Hàm private để map product → DTO
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

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        PurchaseOrder order = orderService.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

        // Nếu đơn đã hoàn thành hoặc huỷ → chỉ xem, không sửa
        boolean readOnly = order.getStatus() == PurchaseOrderStatus.HOAN_THANH
                || order.getStatus() == PurchaseOrderStatus.HUY;

        // Map sang DTO
        PurchaseOrderDTO dto = PurchaseOrderMapper.toDTO(order);

        // Map các item của đơn hàng
        List<PurchaseOrderItemDTO> itemDTOs = order.getItems().stream()
                .map(item -> PurchaseOrderItemDTO.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .contractQuantity(item.getContractQuantity())
                        .actualQuantity(item.getActualQuantity())
                        .price(item.getPrice())
                        .build())
                .toList();

        dto.setItems(itemDTOs);

        var st = dto.getStatus(); // có thể null
        boolean isLockedByStatus = (st == null)
                || (st != PurchaseOrderStatus.DA_GIAO_MOT_PHAN && st != PurchaseOrderStatus.HOAN_THANH);

        // Truyền dữ liệu sang view
        model.addAttribute("orderDTO", dto);
        model.addAttribute("products", getPurchaseProductDTOs()); // Để lấy tên sản phẩm từ ID
        model.addAttribute("readOnly", readOnly);
        model.addAttribute("isLockedByStatus", isLockedByStatus);

        return "purchase-order-edit";
    }


    @PostMapping("/update")
    public String updateOrder(@ModelAttribute("orderDTO") PurchaseOrderDTO dto,
                              BindingResult result,
                              RedirectAttributes redirect,
                              Model model) {
        try {
            if (result.hasErrors()) {
                model.addAttribute("products", getPurchaseProductDTOs());
                model.addAttribute("readOnly", false);

                var st = dto.getStatus();
                boolean isLockedByStatus = (st == null)
                        || (st != PurchaseOrderStatus.DA_GIAO_MOT_PHAN && st != PurchaseOrderStatus.HOAN_THANH);
                model.addAttribute("isLockedByStatus", isLockedByStatus);

                return "purchase-order-edit";
            }

            PurchaseOrder order = orderService.findById(dto.getId())
                    .orElseThrow(() -> new InventoryException("Không tìm thấy đơn hàng"));

            // Không cho sửa đơn đã hoàn thành hoặc huỷ
            if (order.getStatus() == PurchaseOrderStatus.HOAN_THANH) {
                redirect.addFlashAttribute("message", "Đơn hàng đã hoàn thành. Không thể chỉnh sửa.");
                return "redirect:/admin/purchase-orders";
            }

            if (order.getStatus() == PurchaseOrderStatus.HUY) {
                redirect.addFlashAttribute("error", "Đơn hàng đã bị huỷ. Không thể chỉnh sửa.");
                return "redirect:/admin/purchase-orders";
            }

            // ✅ Upload lại file nếu có
            if (dto.getAttachmentFile() != null && !dto.getAttachmentFile().isEmpty()) {
                String newUrl = uploadService.uploadImage(dto.getAttachmentFile());
                order.setAttachmentUrl(newUrl);
            }

            // ✅ Cập nhật ghi chú và trạng thái
            order.setNote(dto.getNote());
            order.setStatus(dto.getStatus());

            // ✅ Cập nhật actualQuantity và cộng tồn kho nếu cần
            List<PurchaseOrderItem> items = order.getItems();
            for (int i = 0; i < items.size(); i++) {
                PurchaseOrderItem entityItem = items.get(i);
                PurchaseOrderItemDTO dtoItem = dto.getItems().get(i);

                Integer deliveryQty = dtoItem.getDeliveryQuantity() != null ? dtoItem.getDeliveryQuantity() : 0;
                Integer currentActual = entityItem.getActualQuantity() != null ? entityItem.getActualQuantity() : 0;
                int contract = entityItem.getContractQuantity();

                int newActual = currentActual + deliveryQty;

                // ✅ Không cho giao vượt hợp đồng
                if (newActual > contract) {
                    model.addAttribute("error", "Sản phẩm \"" + entityItem.getProduct().getName()
                            + "\": Tổng số lượng đã giao vượt quá số lượng hợp đồng (" + contract + ").");
                    model.addAttribute("products", getPurchaseProductDTOs());
                    model.addAttribute("readOnly", false);

                    var st = dto.getStatus();
                    boolean isLockedByStatus = (st == null)
                            || (st != PurchaseOrderStatus.DA_GIAO_MOT_PHAN && st != PurchaseOrderStatus.HOAN_THANH);
                    model.addAttribute("isLockedByStatus", isLockedByStatus);

                    return "purchase-order-edit";
                }

                // ✅ Cập nhật actual
                entityItem.setActualQuantity(newActual);

                // ✅ Cộng tồn kho nếu có giao hàng
                if ((dto.getStatus() == PurchaseOrderStatus.DA_GIAO_MOT_PHAN || dto.getStatus() == PurchaseOrderStatus.HOAN_THANH)
                        && deliveryQty > 0) {
                    Product product = entityItem.getProduct();
                    product.setQuantity(product.getQuantity() + deliveryQty);
                    productRepo.save(product);
                }
            }

            // ✅ Sau khi cập nhật xong → kiểm tra nếu trạng thái là HOÀN THÀNH thì phải giao đủ
            if (dto.getStatus() == PurchaseOrderStatus.HOAN_THANH) {
                boolean giaoThieu = order.getItems().stream().anyMatch(item ->
                        item.getActualQuantity() == null || item.getActualQuantity() < item.getContractQuantity());

                if (giaoThieu) {
                    model.addAttribute("error", "Không thể hoàn thành đơn hàng khi chưa giao đủ tất cả sản phẩm.");
                    model.addAttribute("products", getPurchaseProductDTOs());
                    model.addAttribute("readOnly", false);

                    var st = dto.getStatus();
                    boolean isLockedByStatus = (st == null)
                            || (st != PurchaseOrderStatus.DA_GIAO_MOT_PHAN && st != PurchaseOrderStatus.HOAN_THANH);
                    model.addAttribute("isLockedByStatus", isLockedByStatus);

                    return "purchase-order-edit";
                }
            }

            orderRepo.save(order); // cascade ALL sẽ lưu item
            redirect.addFlashAttribute("message", "Cập nhật đơn hàng thành công.");
            return "redirect:/admin/purchase-orders";

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("products", getPurchaseProductDTOs());
            model.addAttribute("readOnly", false);

            var st = dto.getStatus();
            boolean isLockedByStatus = (st == null)
                    || (st != PurchaseOrderStatus.DA_GIAO_MOT_PHAN && st != PurchaseOrderStatus.HOAN_THANH);
            model.addAttribute("isLockedByStatus", isLockedByStatus);

            return "purchase-order-edit";
        }
    }





}
