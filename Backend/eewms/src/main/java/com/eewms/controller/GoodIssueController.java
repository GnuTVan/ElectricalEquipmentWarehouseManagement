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
    public String showCreateForm(@PathVariable("orderId") Integer orderId, Model model) {
        SaleOrder saleOrder = saleOrderService.getOrderEntityById(orderId);
        model.addAttribute("saleOrder", saleOrder);
        return "good-issue-form"; // 📄 Tạo file good-issue-form.html
    }

    // ✅ 3. Submit tạo phiếu xuất
    @PostMapping("/create")
    public String createGoodIssue(
            @RequestParam("orderId") Integer orderId,
            HttpServletRequest request,
            RedirectAttributes ra
    ) {
        try {
            String username = request.getUserPrincipal().getName();
            SaleOrder order = saleOrderService.getOrderEntityById(orderId);
            GoodIssueNote gin = goodIssueService.createFromOrder(order, username);

            saleOrderService.updateOrderStatus(orderId, SaleOrder.SaleOrderStatus.DELIVERIED);

            ra.addFlashAttribute("success", "✅ Tạo phiếu xuất kho thành công. Mã phiếu: " + gin.getGinCode());
            ra.addFlashAttribute("info", "📦 Trạng thái đơn hàng đã chuyển sang: Đã xuất kho");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi khi tạo phiếu xuất: " + e.getMessage());
        }

        return "redirect:/good-issue";
    }

    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model) {
        GoodIssueNoteDTO dto = goodIssueService.getById(id);

        model.addAttribute("note", dto);
        model.addAttribute("items", dto.getDetails());

        return "good-issue-detail";
    }
}