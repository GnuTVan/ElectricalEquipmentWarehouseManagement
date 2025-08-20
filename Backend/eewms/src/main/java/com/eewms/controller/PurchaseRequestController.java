package com.eewms.controller;

import com.eewms.constant.PRStatus;
import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.entities.Product;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.Supplier;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.purchaseRequest.PurchaseRequestRepository;
import com.eewms.services.IProductServices;
import com.eewms.services.IPurchaseRequestService;
import com.eewms.services.ISaleOrderService;
import com.eewms.services.ISupplierService;
// >>> NEW: nạp danh sách khách cho form "Tạo yêu cầu cho khách"
import com.eewms.services.ICustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final IPurchaseRequestService prService;
    private final ISaleOrderService saleOrderService;
    private final IProductServices productService;
    private final ISupplierService supplierService;
    private final ProductRepository productRepository;
    private final PurchaseRequestRepository prRepo;
    private final GoodIssueNoteRepository goodIssueNoteRepository;

    // >>> NEW
    private final ICustomerService customerService;

    private Map<Long, List<Supplier>> buildAllowedSuppliersMap(List<PurchaseRequestItemDTO> items) {
        Map<Long, List<Supplier>> map = new HashMap<>();
        for (PurchaseRequestItemDTO it : items) {
            Product p = productRepository.findById(it.getProductId().intValue()).orElse(null);
            List<Supplier> allowed = (p == null || p.getSuppliers() == null)
                    ? List.of()
                    : p.getSuppliers().stream().collect(Collectors.toList());
            map.put(it.getProductId(), allowed);
        }
        return map;
    }

    // ==================== LIST ====================
    @GetMapping
    public String listRequests(Model model,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "1000") int size,
                               @RequestParam(required = false) String creator,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("code").ascending());

        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

        Page<PurchaseRequestDTO> requestPage = prService.filter(creator, start, end, pageable);

        model.addAttribute("requestPage", requestPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", requestPage.getTotalPages());
        model.addAttribute("creator", creator);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "purchase-request-list";
    }

    // === Tạo PR từ một SO (giữ để dùng khi cần) – tính thiếu theo "đã xuất", không dựa tồn ===
    @GetMapping("/create-from-sale-order/{saleOrderId}")
    public String createFromSaleOrder(@PathVariable Integer saleOrderId, Model model, RedirectAttributes redirect) {
        if (prRepo.existsBySaleOrder_SoId(saleOrderId)) {
            redirect.addFlashAttribute("error", "Đơn bán đã có yêu cầu mua.");
            return "redirect:/sale-orders/" + saleOrderId + "/edit";
        }
        SaleOrder order = saleOrderService.getOrderEntityById(saleOrderId);

        List<PurchaseRequestItemDTO> items = order.getDetails().stream()
                .map(d -> {
                    Integer issued = goodIssueNoteRepository
                            .sumIssuedQtyBySaleOrderAndProduct(order.getSoId(), d.getProduct().getId());
                    int shortage = d.getOrderedQuantity() - (issued == null ? 0 : issued);
                    if (shortage <= 0) return null;
                    return PurchaseRequestItemDTO.builder()
                            .productId(Long.valueOf(d.getProduct().getId()))
                            .productName(d.getProduct().getName())
                            .quantityNeeded(shortage)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        if (items.isEmpty()) {
            redirect.addFlashAttribute("error", "Không có sản phẩm nào thiếu trong đơn hàng.");
            return "redirect:/sale-orders/" + saleOrderId;
        }

        PurchaseRequestDTO dto = PurchaseRequestDTO.builder()
                .createdByName(order.getCreatedByUser().getFullName())
                .saleOrderId(order.getSoId())
                .items(items)
                .build();

        model.addAttribute("requestDTO", dto);
        model.addAttribute("allowedSuppliers", buildAllowedSuppliersMap(items));
        return "purchase-request-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("requestDTO") PurchaseRequestDTO dto,
                         BindingResult result,
                         RedirectAttributes redirect,
                         Model model) {
        if (result.hasErrors()) {
            model.addAttribute("allowedSuppliers", buildAllowedSuppliersMap(dto.getItems()));
            return "purchase-request-form";
        }

        if (dto.getSaleOrderId() == null) {
            redirect.addFlashAttribute("error", "Thiếu thông tin đơn bán. Vui lòng tạo yêu cầu từ màn hình đơn bán.");
            return "redirect:/sale-orders";
        }

        var saved = prService.create(dto);
        if (!hasAnyRole("ADMIN", "MANAGER")) {
            redirect.addFlashAttribute("message", "Đã gửi yêu cầu mua. Vui lòng chờ phê duyệt.");
            return "redirect:/sale-orders/" + dto.getSaleOrderId() + "/edit";
        }
        return "redirect:/admin/purchase-requests/" + saved.getId();
    }


    @GetMapping("/{id}")
    public String viewDetail(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        var opt = prService.findDtoById(id);
        if (opt.isEmpty()) {
            redirect.addFlashAttribute("error", "Không tìm thấy yêu cầu mua hàng");
            return "redirect:/admin/purchase-requests";
        }
        PurchaseRequestDTO pr = opt.get();
        model.addAttribute("request", pr);
        model.addAttribute("allowedSuppliers", buildAllowedSuppliersMap(pr.getItems()));
        return "purchase-request-detail";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam PRStatus status,
                               RedirectAttributes redirect) {
        try {
            prService.updateStatus(id, status);
            redirect.addFlashAttribute("message", "Cập nhật trạng thái yêu cầu thành công");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchase-requests/" + id;
    }

    @PostMapping("/{id}/update")
    public String updateItems(@PathVariable Long id,
                              @ModelAttribute("request") PurchaseRequestDTO dto,
                              RedirectAttributes redirect) {
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            prService.updateItems(id, dto.getItems());
            redirect.addFlashAttribute("message", "Cập nhật danh sách sản phẩm thành công");
        } else {
            redirect.addFlashAttribute("error", "Danh sách sản phẩm trống hoặc không hợp lệ");
        }
        return "redirect:/admin/purchase-requests/" + id;
    }

    // ==== Hủy PR kèm lý do ====
    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @RequestParam String reason,
                         RedirectAttributes redirect) {
        try {
            prService.cancel(id, reason);
            redirect.addFlashAttribute("message", "Đã hủy yêu cầu mua.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchase-requests/" + id;
    }

    // ==== B8: Tạo PO theo nhà cung cấp đã chọn ====
    @PostMapping("/{id}/generate-po") // >>> NEW
    public String generatePO(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            prService.generatePurchaseOrdersFromRequest(id);
            redirect.addFlashAttribute("message", "Đã tạo các đơn mua hàng theo nhà cung cấp.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/purchase-requests/" + id;
    }

    // ========= helpers =========
    private boolean hasAnyRole(String... roles) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        var authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        for (String r : roles) if (authorities.contains("ROLE_" + r)) return true;
        return false;
    }

    @GetMapping("/collect")
    public String collectAllOpen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Model model,
            RedirectAttributes ra) {

        var items = prService.collectShortagesForAllOpen(start, end);
        if (items.isEmpty()) {
            ra.addFlashAttribute("message", "Không có sản phẩm thiếu ở các đơn đang mở.");
            return "redirect:/admin/purchase-requests";
        }
        model.addAttribute("collectedItems", items);
        model.addAttribute("allowedSuppliers", buildAllowedSuppliersMap(items));
        model.addAttribute("start", start);
        model.addAttribute("end", end);
        return "purchase-request-collect"; // NEW view
    }

    // Xác nhận tạo PR từ danh sách đã thu thập trên preview
    @PostMapping("/create-from-collected")
    public String createFromCollected(@ModelAttribute("items") PurchaseRequestDTO wrapper,
                                      RedirectAttributes redirect,
                                      java.security.Principal principal) {
        try {
            String createdBy = principal != null ? principal.getName() : "system";
            var pr = prService.createFromCollected(wrapper.getItems(), createdBy);
            redirect.addFlashAttribute("message", "Đã tạo yêu cầu mua tổng hợp.");
            return "redirect:/admin/purchase-requests/" + pr.getId();
        } catch (Exception e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/purchase-requests";
        }
    }

}
