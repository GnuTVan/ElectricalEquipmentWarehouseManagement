package com.eewms.controller;

import com.eewms.constant.ItemOrigin;
import com.eewms.dto.SaleOrderDetailDTO;
import com.eewms.dto.SaleOrderMapper;
import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.SaleOrderCombo;
import com.eewms.repository.ComboRepository;
import com.eewms.repository.SaleOrderComboRepository;
import com.eewms.repository.purchaseRequest.PurchaseRequestRepository; // ✅ đúng package bạn đưa
import com.eewms.services.*;
import com.eewms.utils.ComboJsonHelper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/sale-orders")
@RequiredArgsConstructor
public class SaleOrderController {

    private final ISaleOrderService saleOrderService;
    private final ICustomerService customerService;
    private final IProductServices productService;
    private final IGoodIssueService goodIssueService;
    private final IComboService comboService;
    private final ComboJsonHelper comboJsonHelper;

    // ✅ thêm repo PR để check tồn tại
    private final SaleOrderComboRepository saleOrderComboRepository;
    private final PurchaseRequestRepository prRepo;
    private final ComboRepository cbRepo;
    private final IPayOsService payOsService;

    @Value("${payos.enabled:false}")
    private boolean payOsEnabled;

    // ========== LIST ==========
    @GetMapping
    public String listOrders(@RequestParam(value = "keyword", required = false) String keyword,
                             Model model) {
        if (!model.containsAttribute("saleOrderForm")) {
            model.addAttribute("saleOrderForm", new SaleOrderRequestDTO());
        }
        if (keyword != null && !keyword.isBlank()) {
            model.addAttribute("sale_orders", saleOrderService.searchByKeyword(keyword));
        } else {
            model.addAttribute("sale_orders", saleOrderService.getAllOrders());
        }
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", productService.getAll()); // dùng cho list
        model.addAttribute("keyword", keyword);
        return "sale-order/sale-order-list";
    }

    // ========== CREATE ==========
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("saleOrderForm", new SaleOrderRequestDTO());
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("combos", comboService.getAllActive());
        return "sale-order/sale-order-form";
    }

    @PostMapping("/create")
    public String createOrder(@ModelAttribute("saleOrderForm") @Valid SaleOrderRequestDTO dto,
                              BindingResult result,
                              Model model,
                              RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("saleOrderForm", dto);
            model.addAttribute("customers", customerService.findAll());
            model.addAttribute("products", productService.getAllActiveProducts());
            return "sale-order/sale-order-form";
        }
        try {
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            SaleOrderResponseDTO createdOrder = saleOrderService.createOrder(dto, currentUsername);

            if (createdOrder.getDescription() != null
                    && createdOrder.getDescription().toLowerCase().contains("thiếu hàng")) {
                ra.addFlashAttribute("warning", "Đơn hàng đã tạo, tuy nhiên có sản phẩm thiếu hàng. Vui lòng nhập thêm để hoàn thành.");
            } else {
                ra.addFlashAttribute("success", "Tạo đơn hàng thành công. Mã đơn: " + createdOrder.getOrderCode());
            }
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Lỗi khi tạo đơn hàng: " + ex.getMessage());
        }
        return "redirect:/sale-orders";
    }

    // ========== EDIT (hợp nhất Status + Items) ==========
    @GetMapping("/{id}/edit")
    public String editOrder(@PathVariable Integer id, Model model, RedirectAttributes ra) {
        SaleOrderResponseDTO dto = saleOrderService.getById(id);
        if (dto == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }

        // !== PENDING → render readonly (detail-like) NGAY TRÊN ROUTE NÀY
        if (dto.getStatus() != SaleOrder.SaleOrderStatus.PENDING) {
            SaleOrder orderEntity = saleOrderService.getOrderEntityById(id);
            model.addAttribute("saleOrder", orderEntity); // entity để view detail đọc soId/soCode/details
            return "sale-order/sale-order-detail";
        }

        // ===== PENDING: render form edit như cũ =====
        SaleOrder orderEntity = saleOrderService.getOrderEntityById(id);

        var manualDetails = orderEntity.getDetails().stream()
                .filter(d -> d.getOrigin() == ItemOrigin.MANUAL)
                .map(SaleOrderMapper::toDetailDTO)
                .toList();

        var expandedComboIds = saleOrderService.getComboIdsExpanded(id);
        Map<Long, Integer> comboCounts = new LinkedHashMap<>();
        for (Long cid : expandedComboIds) comboCounts.merge(cid, 1, Integer::sum);

        var form = SaleOrderRequestDTO.builder()
                .customerId(orderEntity.getCustomer() != null ? orderEntity.getCustomer().getId() : null)
                .description(orderEntity.getDescription())
                .details(manualDetails)
                .comboCounts(comboCounts)
                .build();

        model.addAttribute("saleOrder", dto); // DTO cho header/nút
        model.addAttribute("saleOrderForm", form);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("combos", cbRepo.findAll());
        model.addAttribute("prExists", prRepo.existsBySaleOrder_SoId(id));

        // (MỚI) — hiển thị QR ở màn EDIT khi đơn còn PENDING (có refresh từ PayOS nếu thiếu)
        if (dto.getStatus() == SaleOrder.SaleOrderStatus.PENDING) {
            // Lấy entity để đọc note & mã order PayOS
            SaleOrder ent = saleOrderService.getOrderEntityById(id);

            // Lấy từ DTO trước (nếu bạn đã đổ sẵn khi tạo đơn)
            String qr   = dto.getQrCodeUrl();
            String link = dto.getPaymentLink();

            // Nếu DTO chưa có QR/link, nhưng đã có mã PayOS → gọi PayOS để refresh
            if (payOsEnabled && (qr == null || link == null) && ent.getPayOsOrderCode() != null) {
                try {
                    var pr = payOsService.getOrder(ent.getPayOsOrderCode());
                    if (pr != null) {
                        if (qr == null)   qr   = pr.getQrCode();
                        if (link == null) link = pr.getPaymentLink();
                    }
                } catch (Exception e) {
                    // Không chặn UI; có thể log nếu cần
                    // log.warn("[PayOS][edit] {}", e.getMessage());
                }
            }

            model.addAttribute("qrCodeUrl", qr);
            model.addAttribute("paymentLink", link);
            model.addAttribute("paymentStatus", dto.getPaymentStatus()); // hoặc ent.getPaymentStatus().name()
            model.addAttribute("paymentNote", ent.getPaymentNote());
        }

        return "sale-order/sale-order-edit";
    }


    // POST: lưu lại items (manual + comboIds)
    @PostMapping("/{id}/items/edit")
    public String updateOrderItems(@PathVariable Integer id,
                                   @ModelAttribute("saleOrderForm") @Valid SaleOrderRequestDTO form,
                                   BindingResult br,
                                   Model model,
                                   RedirectAttributes ra) {
        SaleOrder orderEntity = saleOrderService.getOrderEntityById(id);
        if (orderEntity == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }

        // KHÔNG cho lưu khi không còn PENDING
        if (orderEntity.getStatus() != SaleOrder.SaleOrderStatus.PENDING) {
            ra.addFlashAttribute("error", "Đơn đã ở trạng thái " + orderEntity.getStatus() + ", không thể chỉnh sửa.");
            return "redirect:/sale-orders/" + id + "/edit"; // quay về route edit (render readonly)
        }

        if (br.hasErrors()) {
            SaleOrderResponseDTO dto = saleOrderService.getById(id);
            model.addAttribute("saleOrder", dto);
            model.addAttribute("customers", customerService.findAll());
            model.addAttribute("products", productService.getAllActiveProducts());
            model.addAttribute("combos", cbRepo.findAll());
            model.addAttribute("prExists", prRepo.existsBySaleOrder_SoId(id));
            return "sale-order/sale-order-edit";
        }

        try {
            saleOrderService.updateOrderItems(id, form);
            ra.addFlashAttribute("success", "Cập nhật chi tiết đơn hàng thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/sale-orders/" + id + "/edit"; // giữ một route
    }


    // Lưu trạng thái
//    @PostMapping("/{id}/edit")
//    public String updateOrderStatusAction(@PathVariable Integer id,
//                                          @RequestParam SaleOrder.SaleOrderStatus status,
//                                          RedirectAttributes ra) {
//        try {
//            saleOrderService.updateOrderStatus(id, status);
//            ra.addFlashAttribute("success", "Cập nhật đơn hàng thành công.");
//        } catch (Exception e) {
//            ra.addFlashAttribute("error", e.getMessage());
//        }
//        return "redirect:/sale-orders";
//    }

    @PostMapping("/{id}/update")
    public String updateFinishedSaleOrder(@PathVariable Integer id, RedirectAttributes ra) {
        try {
            saleOrderService.updateOrderStatus(id, SaleOrder.SaleOrderStatus.COMPLETED);
            ra.addFlashAttribute("success", "Đơn hàng đã hoàn tất.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/sale-orders";
    }

    // View completed
    @GetMapping("/{id}/view")
    public String viewOrderDetails(@PathVariable Integer id, Model model) {
        SaleOrder saleOrder = saleOrderService.getOrderEntityById(id);
        if (saleOrder.getStatus() == SaleOrder.SaleOrderStatus.COMPLETED) {
            model.addAttribute("saleOrder", saleOrder);
            return "sale-order/sale-order-detail";
        } else {
            return "redirect:/sale-orders";
        }
    }
    @PostMapping("/{id}/actions/mark-unpaid") // ✅ đúng
    public String markUnpaid(@PathVariable Integer id, RedirectAttributes ra) {
        saleOrderService.updatePaymentStatus(id, SaleOrder.PaymentStatus.PENDING);
        ra.addFlashAttribute("success", "Đã gán trạng thái thanh toán: UNPAID cho đơn hàng.");
        return "redirect:/sale-orders/" + id + "/edit";
    }
}
