package com.eewms.controller;

import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.GoodIssueNote;
import com.eewms.entities.SaleOrder;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.ISaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/good-issue")
@RequiredArgsConstructor
public class GoodIssueController {

    private final IGoodIssueService goodIssueService;
    private final ISaleOrderService saleOrderService;

    /** Danh sách phiếu xuất */
    @GetMapping
    public String listGoodIssues(Model model) {
        List<GoodIssueNoteDTO> list = goodIssueService.getAllNotes();
        model.addAttribute("good_issues", list);
        // view nằm trực tiếp dưới templates/
        return "good-issue-list";
    }

    /** Xem chi tiết phiếu xuất */
    @GetMapping("/view/{id}")
    public String view(@PathVariable Long id, Model model, RedirectAttributes ra) {
        GoodIssueNoteDTO dto = goodIssueService.getById(id);
        if (dto == null) {
            ra.addFlashAttribute("error", "Không tìm thấy phiếu xuất.");
            return "redirect:/good-issue";
        }
        model.addAttribute("note", dto);
        model.addAttribute("items", dto.getDetails());
        model.addAttribute("showPrint", true); // chỉ hiện nút In PDF khi đã lưu
        // view nằm trực tiếp dưới templates/
        return "good-issue-detail";
    }

    /** Trang xem trước phiếu xuất (chưa lưu) khi bấm Tạo phiếu xuất từ đơn - bản cũ theo 'order' */
    @GetMapping("/create-from-order/{orderId}")
    public String previewFromOrder(@PathVariable Integer orderId,
                                   Model model,
                                   RedirectAttributes ra) {
        SaleOrder order = saleOrderService.getOrderEntityById(orderId);
        if (order == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }
        model.addAttribute("saleOrder", order);
        model.addAttribute("showPrint", false); // ẩn nút In PDF ở màn xem trước
        // view nằm trực tiếp dưới templates/
        return "good-issue-form";
    }

    /** Lưu phiếu xuất thật sự từ đơn hàng (từ màn xem trước) - bản cũ theo 'order' */
    @PostMapping("/create-from-order")
    public String createGoodIssue(@RequestParam("orderId") Integer orderId,
                                  RedirectAttributes ra) {
        SaleOrder order = saleOrderService.getOrderEntityById(orderId);
        if (order == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }

        String currentUsername =
                SecurityContextHolder.getContext().getAuthentication().getName();
        GoodIssueNote gin = goodIssueService.createFromOrder(order, currentUsername);

        ra.addFlashAttribute("success", "Đã lưu phiếu xuất: " + gin.getGinCode());
        // LƯU Ý: dùng đúng getter id của entity
        return "redirect:/good-issue/view/" + gin.getGinId();
    }

    // ===========================
    //  BỔ SUNG ENDPOINT THEO YÊU CẦU
    // ===========================

    /** Trang xem trước phiếu xuất khi bấm Tạo phiếu xuất từ đơn bán hàng (đường dẫn 'create-from-sale') */
    @GetMapping("/create-from-sale/{orderId}")
    public String previewFromSale(@PathVariable("orderId") Integer orderId,
                                  Model model,
                                  RedirectAttributes ra) {
        SaleOrder order = saleOrderService.getOrderEntityById(orderId);
        if (order == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }
        model.addAttribute("saleOrder", order);
        model.addAttribute("showPrint", false);
        return "good-issue-form";
    }

    /** Lưu phiếu xuất tạo từ đơn bán hàng (đường dẫn 'create-from-sale') */
    @PostMapping("/create-from-sale")
    public String saveFromSale(@RequestParam("orderId") Integer orderId,
                               RedirectAttributes ra) {
        SaleOrder order = saleOrderService.getOrderEntityById(orderId);
        if (order == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }

        String currentUsername =
                SecurityContextHolder.getContext().getAuthentication().getName();
        GoodIssueNote gin = goodIssueService.createFromOrder(order, currentUsername);

        ra.addFlashAttribute("success", "Đã lưu phiếu xuất: " + gin.getGinCode());
        return "redirect:/good-issue/view/" + gin.getGinId();
    }
}
