package com.eewms.controller;

import com.eewms.dto.GoodIssueDetailDTO;
import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.Debt;
import com.eewms.entities.GoodIssueNote;
import com.eewms.entities.SaleOrder;
import com.eewms.entities.SaleOrderDetail;
import com.eewms.repository.CustomerRefundRepository;
import com.eewms.repository.DebtPaymentRepository;
import com.eewms.repository.DebtRepository;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.ISaleOrderService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.eewms.utils.GoodIssuePdfExporter;
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
    private final CustomerRefundRepository customerRefundRepository;

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

        var ginOpt = goodIssueRepository.findById(id);
        if (ginOpt.isPresent()) {
            var gin = ginOpt.get();
            Integer soId = (gin.getSaleOrder()!=null) ? gin.getSaleOrder().getSoId() : null;

            // (giữ nguyên) Nạp công nợ & payments
            java.util.Optional<com.eewms.entities.Debt> debtOpt = java.util.Optional.empty();
            if (soId != null) {
                debtOpt = debtRepository.findByDocumentTypeAndDocumentId(
                        com.eewms.entities.Debt.DocumentType.SALES_INVOICE,
                        soId.longValue()
                );
            }
            if (debtOpt.isEmpty()) {
                debtOpt = debtRepository.findByDocumentTypeAndDocumentId(
                        com.eewms.entities.Debt.DocumentType.GOOD_ISSUE,
                        id
                );
            }
            debtOpt.ifPresent(debt -> {
                model.addAttribute("debt", debt);
                var total = debt.getTotalAmount()==null?java.math.BigDecimal.ZERO:debt.getTotalAmount();
                var paid  = debt.getPaidAmount()==null?java.math.BigDecimal.ZERO:debt.getPaidAmount();
                var remaining = total.subtract(paid);
                if (remaining.signum()<0) remaining = java.math.BigDecimal.ZERO;

                model.addAttribute("total", total);
                model.addAttribute("paid", paid);
                model.addAttribute("remaining", remaining);
                model.addAttribute("payments", debtPaymentRepository.findByDebtId(debt.getId()));
                model.addAttribute("termsOptions", java.util.List.of(0,10,20,30));
            });

            // NEW: Lấy danh sách hoàn tiền theo SO
            if (soId != null) {
                var refunds = customerRefundRepository.findBySaleOrderSoIdOrderByIdDesc(soId);
                model.addAttribute("refunds", refunds);
            }
        }

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
                ra.addFlashAttribute("success", "Đã lưu phiếu xuất kho thành công: " + note.getGinCode());
                // ⬇️ ĐỔI: quay về danh sách thay vì trang chi tiết
                return "redirect:/good-issue";
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
                ra.addFlashAttribute("success", "Đã lưu phiếu xuất kho thành công: " + note.getGinCode());
                // ⬇️ ĐỔI: quay về danh sách thay vì trang chi tiết
                return "redirect:/good-issue";
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



    @GetMapping("/export/{id}")
    public void exportPdf(@PathVariable Long id, HttpServletResponse response) {
        var dto = goodIssueService.getById(id); // đã có DTO + details
        if (dto == null) throw new RuntimeException("Không tìm thấy phiếu xuất: " + id);
        try {
            String filename = "phieu-xuat-" + (dto.getCode() != null ? dto.getCode() : id) + ".pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");
            com.eewms.utils.GoodIssuePdfExporter.export(dto, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            throw new RuntimeException("Xuất PDF lỗi: " + e.getMessage(), e);
        }
    }
}
