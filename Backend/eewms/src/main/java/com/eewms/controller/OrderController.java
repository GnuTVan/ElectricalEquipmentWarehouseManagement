package com.eewms.controller;

import com.eewms.dto.OrderRequestDTO;
import com.eewms.dto.OrderResponseDTO;
import com.eewms.services.IOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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
public class OrderController {

    private final IOrderService orderService;

    // --- HIỂN THỊ TẤT CẢ ĐƠN ---
    @GetMapping
    public String listOrders(Model model) {
        List<OrderResponseDTO> orders = orderService.getAllOrders();
        model.addAttribute("orders", orders);
        return "orders/list"; // Thymeleaf view
    }

    // --- XEM CHI TIẾT 1 ĐƠN ---
    @GetMapping("/{id}")
    public String viewOrder(@PathVariable Integer id, Model model) {
        OrderResponseDTO order = orderService.getById(id);
        model.addAttribute("order", order);
        return "orders/detail"; // Thymeleaf view
    }


    // --- TẠO MỚI (nếu bạn dùng form tạo đơn) ---
    @PostMapping("/create")
    public String createOrder(@ModelAttribute("orderForm") @Valid OrderRequestDTO dto,
                              BindingResult result,
                              Model model, RedirectAttributes ra) {

        if (result.hasErrors()) {
            model.addAttribute("error", "Dữ liệu không hợp lệ!");
            return "orders/form";
        }

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        orderService.createOrder(dto, currentUsername);
        ra.addFlashAttribute("success", "Tạo đơn hàng thành công");
        return "redirect:/orders";
    }
    // --- DUYỆT ĐƠN (MANAGER Duyệt: Pending → Completed) ---
    @PostMapping("/{id}/approve")
    public String approveOrder(@PathVariable Integer id,
                               Principal principal,
                               RedirectAttributes ra) {
        try {
            String username = principal.getName();
            orderService.updateOrderStatus(id, com.eewms.entities.Order.OrderStatus.COMPLETED, username);
            ra.addFlashAttribute("success", "Duyệt đơn thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders";
    }

    // --- HỦY ĐƠN ---
    @PostMapping("/{id}/cancel")
    public String cancelOrder(@PathVariable Integer id,
                              RedirectAttributes ra) {
        try {
            orderService.cancelOrder(id);
            ra.addFlashAttribute("success", "Hủy đơn thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/orders";
    }

}
