package com.eewms.services.impl;

import com.eewms.dto.GoodIssueDetailDTO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodIssueServiceImpl implements IGoodIssueService {

    private final GoodIssueNoteRepository goodIssueRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final DebtRepository debtRepository;

    /* ============ NEW ============ */
    /** Chuẩn bị dữ liệu xem trước phiếu xuất từ đơn bán (không lưu DB). */
    @Override
    public GoodIssueNoteDTO prepareFromSaleOrder(SaleOrder order) {
        if (order == null) throw new RuntimeException("Không tìm thấy đơn hàng để lập phiếu xuất.");
        GoodIssueNoteDTO dto = new GoodIssueNoteDTO();
        dto.setSaleOrderId(order.getSoId() != null ? order.getSoId().longValue() : null);
        dto.setSaleOrderCode(order.getSoCode());
        dto.setCustomerName(order.getCustomer() != null ? order.getCustomer().getFullName() : null);
        dto.setIssueDate(LocalDateTime.now());
        dto.setDescription(order.getDescription());

        List<GoodIssueDetailDTO> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (order.getDetails() != null) {
            for (SaleOrderDetail d : order.getDetails()) {
                BigDecimal price = Optional.ofNullable(d.getPrice()).orElse(BigDecimal.ZERO);
                int qty = Optional.ofNullable(d.getOrderedQuantity()).orElse(0);
                BigDecimal line = price.multiply(BigDecimal.valueOf(qty));
                items.add(GoodIssueDetailDTO.builder()
                        .productName(d.getProduct() != null ? d.getProduct().getName() : null)
                        .price(price)
                        .quantity(qty)
                        .total(line)
                        .build());
                total = total.add(line);
            }
        }
        dto.setDetails(items);
        dto.setTotalAmount(total);
        return dto;
    }
    /* ============ /NEW ============ */

    @Override
    @Transactional
    public GoodIssueNote createFromOrder(SaleOrder order, String username) {
        if (order == null) throw new RuntimeException("Thiếu entity đơn bán (order=null).");
        if (order.getSoId() == null) throw new RuntimeException("Thiếu khóa đơn bán (soId=null).");

        final Integer soId = order.getSoId();

        // Re-load order (managed & fetch details)
        final SaleOrder managedOrder = saleOrderRepository.findById(soId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn bán: soId=" + soId));

        // Validate dữ liệu "bắt buộc" (không kiểm tồn ở đây để cho phép xuất một phần)
        baseValidate(managedOrder);

        // Chặn lập trùng phiếu xuất cho cùng đơn (nếu business của bạn cấm)
        if (goodIssueRepository.existsBySaleOrder_SoId(soId)) {
            throw new RuntimeException("Đơn này đã được lập phiếu xuất trước đó.");
        }

        // Người tạo
        final User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + username));

        final GoodIssueNote note = new GoodIssueNote();
        note.setGinCode(generateGINCode());
        note.setCustomer(managedOrder.getCustomer());
        note.setDescription("Phiếu xuất từ đơn hàng #" + managedOrder.getSoCode());
        note.setIssueDate(LocalDateTime.now());
        note.setCreatedBy(currentUser);
        note.setSaleOrder(managedOrder);

        final List<GoodIssueDetail> details = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        boolean isPartial = false;      // có xuất thiếu so với đặt không?
        boolean hasAnyExport = false;   // có xuất được dòng nào không?

        for (SaleOrderDetail od : managedOrder.getDetails()) {
            final Product p = od.getProduct();
            final int orderedQty = Optional.ofNullable(od.getOrderedQuantity()).orElse(0);
            final int stock = Optional.ofNullable(p.getQuantity()).orElse(0);
            if (orderedQty <= 0) continue;

            // Cho phép xuất một phần
            final int exportQty = Math.min(orderedQty, stock);

            if (exportQty <= 0) {
                isPartial = true; // dòng này không xuất được
                continue;
            }
            if (exportQty < orderedQty) isPartial = true;

            // Trừ kho theo số thực xuất
            int updated = productRepository.decrementStock(p.getId(), exportQty);
            if (updated == 0) {
                throw new RuntimeException("Không đủ hàng cho sản phẩm: " + p.getName());
            }

            final BigDecimal price = Optional.ofNullable(od.getPrice()).orElse(BigDecimal.ZERO);

            GoodIssueDetail d = GoodIssueDetail.builder()
                    .product(p)
                    .price(price)
                    .quantity(exportQty)
                    .build();
            d.setGoodIssueNote(note);
            details.add(d);

            total = total.add(price.multiply(BigDecimal.valueOf(exportQty)));
            hasAnyExport = true;
        }

        if (!hasAnyExport) {
            throw new RuntimeException("Không có tồn kho để xuất cho bất kỳ sản phẩm nào trong đơn.");
        }

        note.setDetails(details);
        note.setTotalAmount(total);

        // Cập nhật trạng thái đơn theo kết quả xuất
        managedOrder.setStatus(isPartial
                ? SaleOrder.SaleOrderStatus.PARTLY_DELIVERED   // <-- SỬA TÊN ENUM Ở ĐÂY NẾU DỰ ÁN CỦA BẠN KHÁC
                : SaleOrder.SaleOrderStatus.DELIVERIED);
        saleOrderRepository.save(managedOrder);

        try {
            GoodIssueNote saved = goodIssueRepository.save(note);
            goodIssueRepository.flush();
            log.info("[GIN][create] soId={} ginCode={} total={} status={}",
                    soId, saved.getGinCode(), saved.getTotalAmount(), managedOrder.getStatus());
            return saved;
        } catch (DataIntegrityViolationException dive) {
            throw new RuntimeException("Lưu phiếu xuất thất bại: "
                    + (dive.getMostSpecificCause() != null ? dive.getMostSpecificCause().getMessage()
                    : "Vi phạm ràng buộc dữ liệu"));
        } catch (Exception ex) {
            throw new RuntimeException("Lỗi hệ thống khi lưu phiếu xuất: "
                    + (ex.getMessage() != null ? ex.getMessage() : "Xem log server"));
        }
    }

    /** Validate bắt buộc cho đơn: có KH và chi tiết, số lượng đặt > 0 */
    private void baseValidate(SaleOrder so) {
        if (so.getCustomer() == null) {
            throw new RuntimeException("Đơn hàng chưa chọn khách hàng.");
        }
        if (so.getDetails() == null || so.getDetails().isEmpty()) {
            throw new RuntimeException("Đơn hàng không có chi tiết.");
        }
        int i = 0;
        for (SaleOrderDetail d : so.getDetails()) {
            i++;
            if (d.getProduct() == null)             throw new RuntimeException("Dòng " + i + " thiếu product.");
            if (d.getProduct().getId() == null)     throw new RuntimeException("Dòng " + i + " thiếu productId.");
            if (d.getOrderedQuantity() == null || d.getOrderedQuantity() <= 0)
                throw new RuntimeException("Dòng " + i + " số lượng không hợp lệ.");
        }
    }

    @Override
    public GoodIssueNoteDTO getById(Long id) {
        GoodIssueNote gin = goodIssueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu xuất kho"));
        GoodIssueNoteDTO dto = GoodIssueMapper.toNoteDTO(gin);
        dto.setStatus(resolvePaymentStatus(gin));
        fillDebtInfo(dto, gin);
        return dto;
    }

    @Override
    public List<GoodIssueNoteDTO> getAllNotes() {
        List<GoodIssueNote> notes = goodIssueRepository.findAll();
        List<GoodIssueNoteDTO> out = new ArrayList<>(notes.size());
        for (GoodIssueNote gin : notes) {
            GoodIssueNoteDTO dto = GoodIssueMapper.toNoteDTO(gin);
            dto.setStatus(resolvePaymentStatus(gin));
            fillDebtInfo(dto, gin);
            out.add(dto);
        }
        return out;
    }

    private String generateGINCode() {
        return "GIN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmssSSS"));
    }

    private String resolvePaymentStatus(GoodIssueNote gin) {
        Long docId = (gin.getSaleOrder() != null && gin.getSaleOrder().getSoId() != null)
                ? gin.getSaleOrder().getSoId().longValue()
                : null;
        if (docId == null) return "Chưa thanh toán";
        return getPaymentStatusForDocument(Debt.DocumentType.SALES_INVOICE, docId);
    }

    private String getPaymentStatusForDocument(Debt.DocumentType type, Long documentId) {
        return debtRepository.findByDocumentTypeAndDocumentId(type, documentId)
                .map(debt -> debt.getPaidAmount().compareTo(debt.getTotalAmount()) >= 0
                        ? "Đã thanh toán" : "Chưa thanh toán")
                .orElse("Chưa thanh toán");
    }

    private void fillDebtInfo(GoodIssueNoteDTO dto, GoodIssueNote gin) {
        Long saleOrderId = (gin.getSaleOrder() != null && gin.getSaleOrder().getSoId() != null)
                ? gin.getSaleOrder().getSoId().longValue()
                : null;

        if (saleOrderId == null) {
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
                    dto.setHasDebt(remaining.compareTo(BigDecimal.ZERO) > 0);
                }, () -> {
                    dto.setDebtId(null);
                    dto.setRemainingAmount(gin.getTotalAmount() != null ? gin.getTotalAmount() : BigDecimal.ZERO);
                    dto.setHasDebt(false);
                });
    }
}
