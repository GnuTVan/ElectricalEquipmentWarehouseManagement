package com.eewms.controller;

import com.eewms.dto.GoodIssueDetailDTO;
import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.Debt;
import com.eewms.entities.GoodIssueNote;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.SaleOrderDetail;
import com.eewms.repository.DebtPaymentRepository;
import com.eewms.repository.DebtRepository;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.ISaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/good-issue")
@RequiredArgsConstructor
public class GoodIssueController {

    private final IGoodIssueService goodIssueService;
    private final ISaleOrderService saleOrderService;
    private final GoodIssueNoteRepository goodIssueRepository;
    private final DebtRepository debtRepository;
    private final DebtPaymentRepository debtPaymentRepository;

    /** Danh sách phiếu xuất */
    @GetMapping
    public String listGoodIssues(Model model) {
        List<GoodIssueNoteDTO> list = goodIssueService.getAllNotes();
        model.addAttribute("good_issues", list);
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
        model.addAttribute("showPrint", true);

        debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.GOOD_ISSUE, id)
                .ifPresent(debt -> {
                    model.addAttribute("debt", debt);
                    BigDecimal total = debt.getTotalAmount() == null ? BigDecimal.ZERO : debt.getTotalAmount();
                    BigDecimal paid  = debt.getPaidAmount()  == null ? BigDecimal.ZERO : debt.getPaidAmount();
                    BigDecimal remaining = total.subtract(paid);

                    model.addAttribute("total", total);
                    model.addAttribute("paid", paid);
                    model.addAttribute("remaining", remaining);
                    model.addAttribute("payments", debtPaymentRepository.findByDebtId(debt.getId()));
                    model.addAttribute("termsOptions", List.of(0, 10, 20, 30));
                });

        return "good-issue-detail";
    }

    /** Trang xem trước phiếu xuất (chưa lưu) */
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
        model.addAttribute("showPrint", false);
        return "good-issue-form";
    }

    /** Lưu phiếu xuất (cho phép xuất thiếu) – dùng cho nút “Lưu phiếu xuất” ở màn preview */
    @PostMapping("/create-from-order")
    public String createGoodIssue(@RequestParam("orderId") Integer orderId,
                                  RedirectAttributes ra) {
        SaleOrder order = saleOrderService.getOrderEntityById(orderId);
        if (order == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        GoodIssueNoteDTO form = buildPartialFormFromOrder(order);
        try {
            GoodIssueNote note = goodIssueService.saveFromSaleOrderWithPartial(form, username);

            int totalOrdered = order.getDetails().stream().mapToInt(SaleOrderDetail::getOrderedQuantity).sum();
            Integer issuedAll = goodIssueRepository.sumIssuedQtyBySaleOrder(order.getSoId());
            int issued = issuedAll == null ? 0 : issuedAll;

            if (issued < totalOrdered) {
                ra.addFlashAttribute("message",
                        "Đã tạo phiếu xuất " + note.getGinCode() + " (" + issued + "/" + totalOrdered +
                                "). Phần còn lại thiếu hàng. Bấm 'Tạo yêu cầu mua'");
                return "redirect:/admin/purchase-requests";
            } else {
                ra.addFlashAttribute("success", "Đã lưu phiếu xuất: " + note.getGinCode());
                return "redirect:/good-issue/view/" + note.getGinId();
            }
        } catch (com.eewms.exception.NoIssueableStockException ex) {
            ra.addFlashAttribute("warning",
                    "Kho hiện tại hết sạch cho tất cả mặt hàng trong đơn. Vui lòng tạo yêu cầu mua.");
            return "redirect:/admin/purchase-requests?customerId=" + order.getCustomer().getId();
        }
    }

    /** Trang xem trước khi bấm Tạo phiếu xuất từ trang đơn bán */
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

    /** Lưu phiếu xuất từ trang đơn bán – cũng xuất thiếu nếu cần */
    @PostMapping("/create-from-sale")
    public String saveFromSale(@RequestParam("orderId") Integer orderId,
                               RedirectAttributes ra) {
        SaleOrder order = saleOrderService.getOrderEntityById(orderId);
        if (order == null) {
            ra.addFlashAttribute("error", "Không tìm thấy đơn hàng.");
            return "redirect:/sale-orders";
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        GoodIssueNoteDTO form = buildPartialFormFromOrder(order);
        try {
            GoodIssueNote note = goodIssueService.saveFromSaleOrderWithPartial(form, username);

            int totalOrdered = order.getDetails().stream().mapToInt(SaleOrderDetail::getOrderedQuantity).sum();
            Integer issuedAll = goodIssueRepository.sumIssuedQtyBySaleOrder(order.getSoId());
            int issued = issuedAll == null ? 0 : issuedAll;

            if (issued < totalOrdered) {
                ra.addFlashAttribute("message",
                        "Đã tạo phiếu xuất " + note.getGinCode() + " (" + issued + "/" + totalOrdered + "). " +
                                "Phần còn lại chưa đủ hàng, vui lòng tạo yêu cầu mua.");
                return "redirect:/admin/purchase-requests?customerId=" + order.getCustomer().getId();
            } else {
                ra.addFlashAttribute("success", "Đã lưu phiếu xuất: " + note.getGinCode());
                return "redirect:/good-issue/view/" + note.getGinId();
            }
        } catch (com.eewms.exception.NoIssueableStockException ex) {
            ra.addFlashAttribute("warning",
                    "Kho hiện tại hết sạch cho tất cả mặt hàng trong đơn. Vui lòng tạo yêu cầu mua.");
            return "redirect:/admin/purchase-requests?customerId=" + order.getCustomer().getId();
        }
    }

    /** Helper: build DTO để service partial tính phần còn thiếu cho từng sản phẩm */
    private GoodIssueNoteDTO buildPartialFormFromOrder(SaleOrder order) {
        List<GoodIssueDetailDTO> lines = new ArrayList<>();
        order.getDetails().forEach(d -> {
            int ordered = d.getOrderedQuantity();
            Integer issuedBefore = goodIssueRepository
                    .sumIssuedQtyBySaleOrderAndProduct(order.getSoId(), d.getProduct().getId());
            int remaining = ordered - (issuedBefore == null ? 0 : issuedBefore);
            if (remaining > 0) {
                lines.add(GoodIssueDetailDTO.builder()
                        .productId(d.getProduct().getId())
                        .price(d.getPrice())
                        .quantity(remaining)
                        .build());
            }
        });

        return GoodIssueNoteDTO.builder()
                .saleOrderId(order.getId())
                .description("Phiếu xuất từ đơn #" + order.getSoCode())
                .details(lines)
                .build();
    }
}
