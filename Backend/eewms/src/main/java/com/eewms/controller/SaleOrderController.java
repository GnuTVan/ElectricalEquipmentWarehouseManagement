package com.eewms.controller;

import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.SaleOrder;
import com.eewms.services.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/sale-orders")
@RequiredArgsConstructor
public class SaleOrderController {

    private final ISaleOrderService saleOrderService;
    private final ICustomerService customerService;
    private final IProductServices productService;
    private final IGoodIssueService goodIssueService;
    private final IComboService comboService;

    // --- HIỂN THỊ DANH SÁCH ĐƠN ---
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
        model.addAttribute("products", productService.getAll());
        model.addAttribute("keyword", keyword);
        return "sale-order/sale-order-list";
    }

    // --- HIỂN THỊ FORM TẠO ĐƠN ---
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        model.addAttribute("saleOrderForm", new SaleOrderRequestDTO());
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", productService.getAllActiveProducts());
        model.addAttribute("combos", comboService.getAllActive());
        return "sale-order/sale-order-form";
    }

    // --- XỬ LÝ TẠO ĐƠN ---
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
            if (createdOrder.getDescription() != null && createdOrder.getDescription().contains("thiếu hàng")) {
                ra.addFlashAttribute("warning", "Đơn hàng đã tạo, tuy nhiên có sản phẩm thiếu hàng. Vui lòng nhập thêm để hoàn thành.");
            } else {
                ra.addFlashAttribute("success", "Tạo đơn hàng thành công. Mã đơn: " + createdOrder.getOrderCode());
            }
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Lỗi khi tạo đơn hàng: " + ex.getMessage());
        }
        return "redirect:/sale-orders";
    }

    // --- HIỂN THỊ FORM SỬA ĐƠN ---
    @GetMapping("/{id}/edit")
    public String editOrder(@PathVariable Integer id, Model model) {
        SaleOrderResponseDTO dto = saleOrderService.getById(id);
        if (dto.getStatus() == SaleOrder.SaleOrderStatus.COMPLETED) {
            model.addAttribute("error", "Đơn hàng đã hoàn thành, không thể chỉnh sửa.");
            return "redirect:/sale-orders";
        }
        model.addAttribute("saleOrder", dto);
        model.addAttribute("statusOptions", SaleOrder.SaleOrderStatus.values());
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("products", productService.getAll());
        return "sale-order/sale-order-edit";
    }

    // --- XỬ LÝ FORM SỬA ĐƠN ---
    @PostMapping("/{id}/edit")
    public String updateOrder(@PathVariable Integer id,
                              @RequestParam SaleOrder.SaleOrderStatus status,
                              RedirectAttributes ra) {
        try {
            saleOrderService.updateOrderStatus(id, status);
            ra.addFlashAttribute("success", "Cập nhật đơn hàng thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/sale-orders";
    }
    @GetMapping("/{id}/view")
    public String viewOrderDetails(@PathVariable Integer id , Model model) {
        // Lấy đơn hàng từ cơ sở dữ liệu
        SaleOrder saleOrder = saleOrderService.getOrderEntityById(id);

        // Kiểm tra nếu trạng thái đơn hàng là "COMPLETED"
        if (saleOrder.getStatus() == SaleOrder.SaleOrderStatus.COMPLETED) {
            model.addAttribute("saleOrder", saleOrder);
            return "sale-order/sale-order-detail";  // Chuyển tới trang chi tiết đơn hàng
        } else {
            // Trường hợp khác, có thể redirect đến trang khác hoặc thông báo lỗi
            return "redirect:/sale-orders";  // Quay lại danh sách đơn hàng
        }
    }
}
