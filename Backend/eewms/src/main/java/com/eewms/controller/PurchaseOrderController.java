package com.eewms.controller;

import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseProductSelectDTO;
import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.User;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.SupplierRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IPurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    // ✅ Hiển thị danh sách đơn hàng nhập
    @GetMapping
    public String listOrders(Model model) {
        List<PurchaseOrderDTO> orders = orderService.findAll();
        model.addAttribute("orders", orders);
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
                        .build())
                .collect(Collectors.toList());
    }
}
