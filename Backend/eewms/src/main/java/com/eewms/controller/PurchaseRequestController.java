package com.eewms.controller;

import com.eewms.constant.PRStatus;
import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.Product;
import com.eewms.entities.Supplier;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.purchaseRequest.PurchaseRequestRepository;
import com.eewms.services.IProductServices;
import com.eewms.services.IPurchaseRequestService;
import com.eewms.services.ISaleOrderService;
import com.eewms.services.ISupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@Controller
@RequestMapping("/admin/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final IPurchaseRequestService prService;
    private final ISaleOrderService saleOrderService;
    private final IProductServices productService;
    private final ISupplierService supplierService;
    private final IPurchaseRequestService purchaseRequestService;
    private final ProductRepository productRepository; //  thêm
    private final PurchaseRequestRepository prRepo;

    // helper: map productId -> allowed suppliers
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

    @GetMapping
    public String listRequests(Model model,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "8") int size,
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

    @GetMapping("/create-from-sale-order/{saleOrderId}")
    public String createFromSaleOrder(@PathVariable Integer saleOrderId, Model model, RedirectAttributes redirect) {

        if (prRepo.existsBySaleOrder_SoId(saleOrderId)) {
            redirect.addFlashAttribute("error", "Đơn bán đã có yêu cầu mua.");
            return "redirect:/sale-orders/" + saleOrderId + "/edit";
        }
        SaleOrder order = saleOrderService.getOrderEntityById(saleOrderId);

        List<PurchaseRequestItemDTO> items = order.getDetails().stream()
                .filter(d -> d.getProduct().getQuantity() < d.getOrderedQuantity())
                .map(d -> PurchaseRequestItemDTO.builder()
                        .productId(Long.valueOf(d.getProduct().getId()))
                        .productName(d.getProduct().getName())
                        .quantityNeeded(d.getOrderedQuantity() - d.getProduct().getQuantity())
                        .build())
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
        // bỏ suppliers toàn hệ thống
        // model.addAttribute("suppliers", supplierService.findAll());
        //  chỉ NCC thuộc từng sản phẩm
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
        // Staff không có quyền xem PR -> quay về SO detail
        if (!hasAnyRole("ADMIN", "MANAGER")) {
            redirect.addFlashAttribute("message", "Đã gửi yêu cầu mua. Vui lòng chờ phê duyệt.");
            return "redirect:/sale-orders/" + dto.getSaleOrderId() + "/edit";
        }
        // Manager/Admin -> vào chi tiết PR
        return "redirect:/admin/purchase-requests/" + saved.getId();
    }

    private boolean hasAnyRole(String... roles) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        var authorities = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        for (String r : roles) {
            if (authorities.contains("ROLE_" + r)) return true;
        }
        return false;
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
        // model.addAttribute("suppliers", supplierService.findAll());
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

    @PostMapping("/{id}/generate-po")
    public String generatePOFromPR(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            purchaseRequestService.generatePurchaseOrdersFromRequest(id);
            redirectAttributes.addFlashAttribute("message", "Đã tạo phiếu mua hàng từ yêu cầu thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi khi tạo phiếu mua hàng: " + e.getMessage());
        }
        return "redirect:/admin/purchase-requests/" + id;
    }
}
