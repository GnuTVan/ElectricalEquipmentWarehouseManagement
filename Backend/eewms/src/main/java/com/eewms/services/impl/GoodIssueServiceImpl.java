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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eewms.exception.NoIssueableStockException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

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
        // Giữ lại API cũ – KHÔNG dùng cho luồng mới vì không cho xuất thiếu
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        GoodIssueNote note = new GoodIssueNote();
        note.setGinCode(generateGINCode());
        note.setCustomer(order.getCustomer());
        note.setDescription("Phiếu xuất từ đơn hàng #" + order.getSoCode());
        note.setIssueDate(LocalDateTime.now());
        note.setCreatedBy(currentUser);
        note.setSaleOrder(order);

        List<GoodIssueDetail> details = order.getDetails().stream().map(orderDetail -> {
            Product product = orderDetail.getProduct();
            int orderedQty = orderDetail.getOrderedQuantity();

            if (product.getQuantity() < orderedQty) {
                throw new RuntimeException("Không đủ hàng trong kho cho sản phẩm: " + product.getName());
            }

            product.setQuantity(product.getQuantity() - orderedQty);
            productRepository.save(product);

            GoodIssueDetail detail = GoodIssueDetail.builder()
                    .product(product)
                    .price(orderDetail.getPrice())
                    .quantity(orderedQty)
                    .build();
            detail.setGoodIssueNote(note);
            return detail;
        }).toList();

        BigDecimal total = details.stream()
                .map(d -> d.getPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        note.setDetails(details);
        note.setTotalAmount(total);

        order.setStatus(SaleOrder.SaleOrderStatus.DELIVERIED);
        saleOrderRepository.save(order);

        productRepository.saveAll(details.stream().map(GoodIssueDetail::getProduct).toList());
        goodIssueRepository.save(note);

        return note;
    }

    @Override
    @Transactional(readOnly = true)
    public GoodIssueNoteDTO getById(Long id) {
        GoodIssueNote gin = goodIssueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu xuất kho"));
        GoodIssueNoteDTO dto = GoodIssueMapper.toNoteDTO(gin);
        dto.setStatus(resolvePaymentStatus(gin));
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
                    dto.setStatus(resolvePaymentStatus(gin));
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

    private String resolvePaymentStatus(GoodIssueNote gin) {
        Long docId = (gin.getSaleOrder() != null) ? gin.getSaleOrder().getId() : null;
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
        Long saleOrderId = (gin.getSaleOrder() != null) ? gin.getSaleOrder().getId() : null;
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

    // ===== LUỒNG MỚI: Xuất phần còn trong kho, cập nhật PARTLY_DELIVERED =====
    @Override
    @Transactional
    public GoodIssueNote saveFromSaleOrderWithPartial(GoodIssueNoteDTO form, String username) {
        User current = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        if (form.getSaleOrderId() == null) {
            throw new RuntimeException("Thiếu saleOrderId");
        }

        SaleOrder order = saleOrderRepository.findById(form.getSaleOrderId().intValue())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn bán"));

        GoodIssueNote note = GoodIssueNote.builder()
                .ginCode(generateGINCode())
                .customer(order.getCustomer())
                .description((form.getDescription() == null || form.getDescription().isBlank())
                        ? "Phiếu xuất từ đơn #" + order.getSoCode()
                        : form.getDescription())
                .issueDate(LocalDateTime.now())
                .createdBy(current)
                .saleOrder(order)
                .build();

        Map<Integer, Integer> orderedByPid = new HashMap<>();
        Map<Integer, Integer> issuedByPid = new HashMap<>();
        for (var d : order.getDetails()) {
            orderedByPid.merge(d.getProduct().getId(), d.getOrderedQuantity(), Integer::sum);
            Integer issuedBefore = goodIssueRepository
                    .sumIssuedQtyBySaleOrderAndProduct(order.getSoId(), d.getProduct().getId());
            issuedByPid.put(d.getProduct().getId(), issuedBefore == null ? 0 : issuedBefore);
        }

        BigDecimal total = BigDecimal.ZERO;
        List<GoodIssueDetail> details = new ArrayList<>();

        // Xuất tối đa theo: MIN(requested, remaining, onHand).
        if (form.getDetails() != null) {
            for (GoodIssueDetailDTO line : form.getDetails()) {
                if (line == null || line.getProductId() == null) continue;
                Product p = productRepository.findById(line.getProductId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm ID=" + line.getProductId()));

                int ordered = orderedByPid.getOrDefault(p.getId(), 0);
                int issued = issuedByPid.getOrDefault(p.getId(), 0);
                int remaining = Math.max(0, ordered - issued);

                int req = Optional.ofNullable(line.getQuantity()).orElse(0);
                if (req <= 0) continue;

                int onHand = Optional.ofNullable(p.getQuantity()).orElse(0);
                int canIssue = Math.min(Math.min(req, remaining), onHand);
                if (canIssue <= 0) continue;

                p.setQuantity(onHand - canIssue);
                productRepository.save(p);

                BigDecimal price = Optional.ofNullable(line.getPrice())
                        .orElse(Optional.ofNullable(p.getListingPrice()).orElse(BigDecimal.ZERO));

                GoodIssueDetail det = GoodIssueDetail.builder()
                        .goodIssueNote(note)
                        .product(p)
                        .quantity(canIssue)
                        .price(price)
                        .build();

                details.add(det);
                total = total.add(price.multiply(BigDecimal.valueOf(canIssue)));
            }
        }

        // ✅ NEW: nếu KHÔNG có dòng nào có thể xuất → KHÔNG lưu phiếu, ném exception
        if (details.isEmpty()) {
            // Không đổi tồn kho, không đổi trạng thái đơn.
            throw new NoIssueableStockException("Kho hết sạch cho tất cả mặt hàng trong đơn. Không thể tạo phiếu xuất.");
        }

        // === giữ nguyên luồng cũ bên dưới ===
        note.setDetails(details);
        note.setTotalAmount(total);
        goodIssueRepository.save(note);

        Integer issuedAfter = goodIssueRepository.sumIssuedQtyBySaleOrder(order.getSoId());
        int totalOrdered = order.getDetails().stream().mapToInt(d -> d.getOrderedQuantity()).sum();
        int issuedVal = (issuedAfter == null ? 0 : issuedAfter);

        order.setStatus(issuedVal >= totalOrdered
                ? SaleOrder.SaleOrderStatus.DELIVERIED
                : SaleOrder.SaleOrderStatus.PARTLY_DELIVERED);
        saleOrderRepository.save(order);

        return note;
    }
}
