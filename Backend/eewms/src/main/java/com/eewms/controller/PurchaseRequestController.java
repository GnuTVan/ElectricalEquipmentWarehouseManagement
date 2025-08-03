package com.eewms.controller;

import com.eewms.constant.PRStatus;
import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.entities.SaleOrder;
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
import java.util.List;

@Controller
@RequestMapping("/admin/purchase-requests")
@RequiredArgsConstructor
public class PurchaseRequestController {

    private final IPurchaseRequestService prService;
    private final ISaleOrderService saleOrderService;
    private final IProductServices productService;
    private final ISupplierService supplierService;
    private final IPurchaseRequestService purchaseRequestService;

    @GetMapping
    public String listRequests(Model model,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "8") int size,
                               @RequestParam(required = false) String creator,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("code").ascending());

        // Convert ngày sang LocalDateTime
        LocalDateTime start = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime end = (endDate != null) ? endDate.atTime(LocalTime.MAX) : null;

        Page<PurchaseRequestDTO> requestPage = prService.filter(creator, start, end, pageable);

        model.addAttribute("requestPage", requestPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", requestPage.getTotalPages());

        // Đưa filter về lại view
        model.addAttribute("creator", creator);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "purchase-request-list";
    }

    @GetMapping("/create-from-sale-order/{saleOrderId}")
    public String createFromSaleOrder(@PathVariable Integer saleOrderId, Model model, RedirectAttributes redirect) {
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

//        // ✅ Cập nhật trạng thái đơn bán hàng từ PENDING → DELIVERIED
//        if (order.getStatus() == SaleOrder.SaleOrderStatus.PENDING) {
//            saleOrderService.updateOrderStatus(order.getSoId(), SaleOrder.SaleOrderStatus.DELIVERIED);
//        }

        PurchaseRequestDTO dto = PurchaseRequestDTO.builder()
                .createdByName(order.getCreatedByUser().getFullName())
                .saleOrderId(order.getSoId())
                .items(items)
                .build();

        model.addAttribute("requestDTO", dto);
        model.addAttribute("suppliers", supplierService.findAll());
        return "purchase-request-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("requestDTO") PurchaseRequestDTO dto,
                         BindingResult result,
                         RedirectAttributes redirect,
                         Model model) {
        if (result.hasErrors()) {
            model.addAttribute("suppliers", supplierService.findAll());
            return "purchase-request-form";
        }

        prService.create(dto);
        redirect.addFlashAttribute("message", "Tạo yêu cầu mua hàng thành công");
        return "redirect:/admin/purchase-requests";
    }

    @GetMapping("/{id}")
    public String viewDetail(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        var opt = prService.findDtoById(id);
        if (opt.isEmpty()) {
            redirect.addFlashAttribute("error", "Không tìm thấy yêu cầu mua hàng");
            return "redirect:/admin/purchase-requests";
        }
        model.addAttribute("request", opt.get());
        model.addAttribute("suppliers", supplierService.findAll()); // ✅ Thêm supplier vào model
        return "purchase-request-detail";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam PRStatus status,
                               RedirectAttributes redirect) {
        prService.updateStatus(id, status);
        redirect.addFlashAttribute("message", "Cập nhật trạng thái yêu cầu thành công");
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