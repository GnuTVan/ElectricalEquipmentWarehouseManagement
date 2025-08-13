package com.eewms.controller;

import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.GoodIssueNote;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.User;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.ISaleOrderService;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/good-issue")
@RequiredArgsConstructor
public class GoodIssueController {

    private final ISaleOrderService saleOrderService;
    private final IGoodIssueService goodIssueService;

    // ✅ 1. Danh sách phiếu xuất
    @GetMapping
    public String listGoodIssues(Model model) {
        List<GoodIssueNoteDTO> list = goodIssueService.getAllNotes();
        model.addAttribute("good_issues", list);
        return "good-issue-list";
    }

    // ✅ 2. Form tạo phiếu xuất từ đơn hàng
    @GetMapping("/create-from-order/{orderId}")
    public String showCreateForm(@PathVariable("orderId") Integer orderId,
                                 Model model, RedirectAttributes ra) {
        // dùng DTO để lấy cờ thiếu hàng đã tính sẵn
        var dto = saleOrderService.getById(orderId);
        if (dto.isHasInsufficientStock()) {
            ra.addFlashAttribute("error", "Đơn hàng đang thiếu hàng. Không thể tạo phiếu xuất.");
            return "redirect:/sale-orders/" + orderId + "/edit";
        }
        SaleOrder saleOrder = saleOrderService.getOrderEntityById(orderId);
        model.addAttribute("saleOrder", saleOrder);
        return "good-issue-form";
    }

    // ✅ 3. Submit tạo phiếu xuất
    @PostMapping("/create")
    public String createGoodIssue(@RequestParam("orderId") Integer orderId,
                                  HttpServletRequest request,
                                  RedirectAttributes ra) {
        try {
            String username = request.getUserPrincipal().getName();
            SaleOrder order = saleOrderService.getOrderEntityById(orderId);
            GoodIssueNote gin = goodIssueService.createFromOrder(order, username);

//            saleOrderService.updateOrderStatus(orderId, SaleOrder.SaleOrderStatus.DELIVERIED);

            ra.addFlashAttribute("success", "✅ Tạo phiếu xuất kho thành công. Mã phiếu: " + gin.getGinCode());
            return "redirect:/good-issue/view/" + gin.getGinId(); // chuyển sang chi tiết phiếu
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi khi tạo phiếu xuất: " + e.getMessage());
            return "redirect:/sale-orders/" + orderId + "/edit"; // QUAN TRỌNG: quay về đơn
        }
    }

    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        GoodIssueNoteDTO dto = goodIssueService.getById(id);

        model.addAttribute("note", dto);
        model.addAttribute("items", dto.getDetails());

        return "good-issue-detail";
    }
}