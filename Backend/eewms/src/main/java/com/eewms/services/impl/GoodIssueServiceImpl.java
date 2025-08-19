package com.eewms.services.impl;

import com.eewms.dto.GoodIssueMapper;
import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.*;
import com.eewms.repository.DebtRepository;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.SaleOrderRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IGoodIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoodIssueServiceImpl implements IGoodIssueService {

    private final GoodIssueNoteRepository goodIssueRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final DebtRepository debtRepository; // tra công nợ

    @Override
    public GoodIssueNote createFromOrder(SaleOrder order, String username) {
        // Tìm user đang đăng nhập
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Tạo phiếu xuất kho
        GoodIssueNote note = new GoodIssueNote();
        note.setGinCode(generateGINCode());
        note.setCustomer(order.getCustomer());
        note.setDescription("Phiếu xuất từ đơn hàng #" + order.getSoCode());
        note.setIssueDate(LocalDateTime.now());
        note.setCreatedBy(currentUser);
        note.setSaleOrder(order); // Gán đơn hàng gốc

        // Tạo chi tiết phiếu xuất và trừ kho
        List<GoodIssueDetail> details = order.getDetails().stream().map(orderDetail -> {
            Product product = orderDetail.getProduct();
            int orderedQty = orderDetail.getOrderedQuantity();

            if (product.getQuantity() < orderedQty) {
                throw new RuntimeException("Không đủ hàng trong kho cho sản phẩm: " + product.getName());
            }

            // Trừ kho
            product.setQuantity(product.getQuantity() - orderedQty);
            productRepository.save(product);

            // Tạo chi tiết xuất kho
            GoodIssueDetail detail = GoodIssueDetail.builder()
                    .product(product)
                    .price(orderDetail.getPrice())
                    .quantity(orderedQty)
                    .build();
            detail.setGoodIssueNote(note); // Gán ngược về phiếu xuất
            return detail;
        }).toList();

        // Tính tổng tiền
        BigDecimal total = details.stream()
                .map(d -> d.getPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        note.setDetails(details);
        note.setTotalAmount(total);

        order.setStatus(SaleOrder.SaleOrderStatus.DELIVERIED);
        saleOrderRepository.save(order);

        productRepository.saveAll(details.stream().map(GoodIssueDetail::getProduct).toList()); // lưu lại tồn kho
        goodIssueRepository.save(note);

        return note;
    }

    @Override
    @Transactional(readOnly = true)
    public GoodIssueNoteDTO getById(Long id) {
        GoodIssueNote gin = goodIssueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu xuất kho"));
        GoodIssueNoteDTO dto = GoodIssueMapper.toNoteDTO(gin);

        // ==== Trạng thái thanh toán theo ĐƠN BÁN (SALES_INVOICE) ====
        dto.setStatus(resolvePaymentStatus(gin));

        // ==== Gán thông tin công nợ cho UI (debtId, remainingAmount, hasDebt) ====
        fillDebtInfo(dto, gin);

        return dto;
    }

    @Transactional(readOnly = true)
    @Override
    public List<GoodIssueNoteDTO> getAllNotes() {
        List<GoodIssueNote> notes = goodIssueRepository.findAllWithDetails();
        return notes.stream()
                .map(gin -> {
                    GoodIssueNoteDTO dto = GoodIssueMapper.toNoteDTO(gin);
                    // Trạng thái thanh toán
                    dto.setStatus(resolvePaymentStatus(gin));
                    // Thông tin công nợ
                    fillDebtInfo(dto, gin);
                    return dto;
                })
                .toList();
    }

    @Override
    public GoodIssueNoteDTO prepareFromSaleOrder(SaleOrder order) {
        return null;
    }

    private String generateGINCode() {
        long count = goodIssueRepository.count() + 1;
        return String.format("GIN%05d", count);
    }

    // ===== Xác định trạng thái thanh toán =====
    private String resolvePaymentStatus(GoodIssueNote gin) {
        // Ưu tiên liên kết qua đơn bán: documentType = SALES_INVOICE, documentId = saleOrder.id
        Long docId = (gin.getSaleOrder() != null) ? gin.getSaleOrder().getId() : null;

        if (docId == null) {
            // Không có đơn bán => chưa cấu hình công nợ cho GIN này
            return "Chưa thanh toán";
        }

        return getPaymentStatusForDocument(Debt.DocumentType.SALES_INVOICE, docId);
    }

    private String getPaymentStatusForDocument(Debt.DocumentType type, Long documentId) {
        return debtRepository.findByDocumentTypeAndDocumentId(type, documentId)
                .map(debt -> debt.getPaidAmount().compareTo(debt.getTotalAmount()) >= 0
                        ? "Đã thanh toán" : "Chưa thanh toán")
                .orElse("Chưa thanh toán");
    }

    // ===== Gán dữ liệu công nợ cho DTO (cột "Công nợ") =====
    private void fillDebtInfo(GoodIssueNoteDTO dto, GoodIssueNote gin) {
        // Lấy id đơn bán để tra công nợ
        Long saleOrderId = (gin.getSaleOrder() != null) ? gin.getSaleOrder().getId() : null;

        if (saleOrderId == null) {
            // Không có đơn bán → không có thông tin công nợ
            dto.setDebtId(null);
            dto.setRemainingAmount(BigDecimal.ZERO);
            dto.setHasDebt(false);
            return;
        }

        debtRepository.findByDocumentTypeAndDocumentId(Debt.DocumentType.SALES_INVOICE, saleOrderId)
                .ifPresentOrElse(debt -> {
                    BigDecimal remaining = debt.getTotalAmount().subtract(debt.getPaidAmount());
                    if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

                    dto.setDebtId(debt.getId());
                    dto.setRemainingAmount(remaining);
                    dto.setHasDebt(remaining.compareTo(BigDecimal.ZERO) > 0); // còn nợ thì true
                }, () -> {
                    // Chưa có record Debt → có thể là đơn bán công nợ nhưng chưa sinh công nợ
                    dto.setDebtId(null);
                    dto.setRemainingAmount(gin.getTotalAmount() != null ? gin.getTotalAmount() : BigDecimal.ZERO);
                    dto.setHasDebt(false);
                });
    }
}
