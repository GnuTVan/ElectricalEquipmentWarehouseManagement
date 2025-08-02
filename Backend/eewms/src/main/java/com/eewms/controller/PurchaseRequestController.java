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
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    public String listRequests(Model model) {
        List<PurchaseRequestDTO> requests = prService.findAll();
        model.addAttribute("requests", requests);
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