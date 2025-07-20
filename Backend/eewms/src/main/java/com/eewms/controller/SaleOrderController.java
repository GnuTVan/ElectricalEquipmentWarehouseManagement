package com.eewms.controller;

import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.SaleOrder;
import com.eewms.services.ICustomerService;
import com.eewms.services.IProductServices;
import com.eewms.services.ISaleOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class SaleOrderController {

    private final ISaleOrderService saleOrderService;
    private final ICustomerService customerService;
    private final IProductServices productService;

    // --- HIỂN THỊ TẤT CẢ ĐƠN ---
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
        return "sale_orders/list";
    }



    // --- TẠO MỚI (nếu bạn dùng form tạo đơn) ---
    @PostMapping("/create")
    public String createOrder(@ModelAttribute("orderForm") @Valid SaleOrderRequestDTO dto,
                              BindingResult result,
                              Model model,
                              RedirectAttributes ra) {

        if (result.hasErrors()) {
            model.addAttribute("saleOrderForm", dto);
            model.addAttribute("hasFormError", true);
            model.addAttribute("sale_orders", saleOrderService.getAllOrders());
            model.addAttribute("customers", customerService.findAll());
            model.addAttribute("products", productService.getAll());
            return "sale_orders/list";
        }

        try {
            String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            saleOrderService.createOrder(dto, currentUsername);
            ra.addFlashAttribute("success", "Tạo đơn hàng thành công");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Lỗi khi tạo đơn hàng: " + ex.getMessage());
        }

        return "redirect:/sale_orders";
    }

    // --- DUYỆT ĐƠN (MANAGER Duyệt: Pending → Completed) ---
    @PostMapping("/{id}/approve")
    public String approveOrder(@PathVariable Integer id,
                               Principal principal,
                               RedirectAttributes ra) {
        try {
            String username = principal.getName();
            saleOrderService.updateOrderStatus(id, SaleOrder.SaleOrderStatus.COMPLETED, username);
            ra.addFlashAttribute("success", "Duyệt đơn thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/sale_orders";
    }

    // --- HỦY ĐƠN ---
    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Integer id,
                              RedirectAttributes ra) {
        try {
            saleOrderService.cancelOrder(id);
            ra.addFlashAttribute("success", "Hủy đơn thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/sale_orders";
    }

    // --- XEM CHI TIẾT 1 ĐƠN ---
    @GetMapping("/{id}")
    public String viewOrder(@PathVariable Integer id, Model model) {
        SaleOrderResponseDTO saleOrder = saleOrderService.getById(id);
        model.addAttribute("saleOrder", saleOrder);
        return "sale_orders/detail"; // Thymeleaf view
    }
}
