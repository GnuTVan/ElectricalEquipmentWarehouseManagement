package com.eewms.controller;

import com.eewms.repository.DebtPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PayOsReturnController {

    private final DebtPaymentRepository debtPaymentRepository;

    @GetMapping({"/payos/return", "/payos/return/"})
    public RedirectView payosReturn(
            @RequestParam(name = "orderCode", required = false) String orderCode,
            @RequestParam(name = "status", required = false) String status,
            RedirectAttributes ra
    ) {
        // Cố gắng đoán GIN để quay lại đúng trang
        Long ginId = null;
        if (orderCode != null) {
            debtPaymentRepository.findByPayosOrderCode(orderCode).ifPresent(p -> {
                // nhúng ginId nếu bạn có lưu vào payment.note; nếu không thì từ Debt -> documentType / documentId
            });
        }
        ra.addFlashAttribute("message",
                "Đã kết thúc phiên thanh toán. Trạng thái sẽ cập nhật sau vài giây khi webhook tới.");
        ra.addFlashAttribute("messageType", "info");

        // fallback trang công nợ / GIN
        return new RedirectView("/good-issue"); // hoặc "/good-issue/view/" + ginId nếu xác định được
    }


    @GetMapping({"/payos/cancel", "/payos/cancel/"})
    public RedirectView payosCancel(
            @RequestParam(name = "orderCode", required = false) String orderCode,
            RedirectAttributes ra
    ) {
        if (orderCode != null) {
            try {
                // Không bắt buộc, có thể chỉ hiển thị toast.
                // debtService.cancelPayOsPayment(orderCode); // nếu bạn muốn cancel ngay khi user bấm Hủy trên trang PayOS
            } catch (Exception ignored) {
            }
        }
        ra.addFlashAttribute("message", "Bạn đã huỷ giao dịch trên PayOS.");
        ra.addFlashAttribute("messageType", "warning");
        return new RedirectView("/good-issue");
    }
}