package com.eewms.services.impl;

import com.eewms.constant.ReturnSettlementOption;
import com.eewms.constant.ReturnStatus;
import com.eewms.dto.returning.SalesReturnDTO;
import com.eewms.dto.returning.SalesReturnMapper;
import com.eewms.entities.*;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.SaleOrderRepository;
import com.eewms.repository.UserRepository;
import com.eewms.repository.returning.SalesReturnRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IDebtService;
import com.eewms.services.ISalesReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SalesReturnServiceImpl implements ISalesReturnService {

    private final SalesReturnRepository salesReturnRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final IDebtService debtService;
    private final com.eewms.services.IWarehouseReceiptService warehouseReceiptService;
    private final com.eewms.repository.returning.SalesReturnItemRepository salesReturnItemRepository;
    private final WarehouseReceiptRepository warehouseReceiptRepository;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;
    private final GoodIssueNoteRepository goodIssueNoteRepository;
    private final com.eewms.repository.CustomerRefundRepository customerRefundRepository;

    private String genCode() {
        long count = salesReturnRepository.count() + 1;
        return String.format("SRN%05d", count);
    }

    private BigDecimal computeTotal(SalesReturn e) {
        return e.getItems() == null ? BigDecimal.ZERO :
                e.getItems().stream()
                        .map(it -> it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional
    public SalesReturnDTO createDraft(SalesReturnDTO dto, String username) {
        if (dto.getSaleOrderId() == null) throw new IllegalArgumentException("Thiếu saleOrderId");
        SaleOrder so = saleOrderRepository.findByIdWithDetails(dto.getSaleOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn bán"));

        if (so.getStatus() != SaleOrder.SaleOrderStatus.DELIVERIED) {
            throw new IllegalStateException("Chỉ tạo hoàn hàng từ đơn đã hoàn thành.");
        }

        User creator = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));

        List<Integer> pids = dto.getItems() == null ? List.of() :
                dto.getItems().stream().filter(l -> l.getProductId() != null)
                        .map(l -> l.getProductId().intValue()).toList();
        List<Product> products = pids.isEmpty() ? List.of() : productRepository.findAllById(pids);

        dto.setCode(genCode());
        java.util.Map<Integer, java.math.BigDecimal> priceByPid =
                so.getDetails().stream().collect(java.util.stream.Collectors.toMap(
                        d -> d.getProduct().getId(),
                        com.eewms.entities.SaleOrderDetail::getPrice,
                        (a, b) -> a));
        SalesReturn sr = SalesReturnMapper.toEntity(dto, so, creator, products);
        for (var it : sr.getItems()) {
            java.math.BigDecimal soPrice = priceByPid.get(it.getProduct().getId());
            if (soPrice != null) {
                it.setUnitPrice(soPrice);
                it.setLineAmount(soPrice.multiply(java.math.BigDecimal.valueOf(it.getQuantity())));
            }
        }

        sr.setCode(dto.getCode());
        sr.setStatus(com.eewms.constant.ReturnStatus.MOI_TAO);
        sr = salesReturnRepository.save(sr);
        return com.eewms.dto.returning.SalesReturnMapper.toDTO(sr);
    }

    @Override
    @Transactional
    public void submit(Long id) {
        SalesReturn sr = salesReturnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu hoàn hàng"));
        if (sr.getStatus() != ReturnStatus.MOI_TAO) {
            throw new IllegalStateException("Chỉ submit phiếu ở trạng thái MỚI TẠO.");
        }
        sr.setStatus(ReturnStatus.CHO_DUYET);
        salesReturnRepository.save(sr);
    }

    @Override
    @Transactional
    public void approve(Long id, String managerNote) {
        SalesReturn sr = salesReturnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu hoàn hàng"));
        if (sr.getStatus() != ReturnStatus.CHO_DUYET) {
            throw new IllegalStateException("Chỉ duyệt phiếu ở trạng thái CHỜ DUYỆT.");
        }

        // ---- VALIDATE: không vượt SL đặt ----
        var so = sr.getSaleOrder();

        // Tổng SL đặt theo từng product
        java.util.Map<Integer, Integer> ordered = new java.util.HashMap<>();
        for (var d : so.getDetails()) {
            ordered.merge(d.getProduct().getId(), d.getOrderedQuantity(), Integer::sum);
        }

        // Tổng SL đang yêu cầu hoàn trong phiếu hiện tại theo product
        java.util.Map<Integer, Integer> currentReq = new java.util.HashMap<>();
        for (var it : sr.getItems()) {
            currentReq.merge(it.getProduct().getId(), it.getQuantity(), Integer::sum);
        }

        // Đã hoàn ở các phiếu khác (không tính draft & từ chối)
        var statuses = java.util.List.of(
                ReturnStatus.CHO_DUYET,
                ReturnStatus.DA_DUYET,
                ReturnStatus.DA_NHAP_KHO,
                ReturnStatus.HOAN_TAT
        );

        for (var pid : currentReq.keySet()) {
            Integer productId = pid;

            Long alreadyInc = salesReturnItemRepository
                    .sumReturnedBySoAndProduct(so.getSoId(), productId, statuses);
            int doneInc = alreadyInc == null ? 0 : alreadyInc.intValue();

            int ask = currentReq.getOrDefault(productId, 0);

            // Loại phần "đang duyệt" (phiếu hiện tại) khỏi doneInc
            int doneExcludingCurrent = Math.max(0, doneInc - ask);

            int orderedQty = ordered.getOrDefault(productId, 0);
            int allow = orderedQty - doneExcludingCurrent;

            if (ask > allow) {
                String pName = sr.getItems().stream()
                        .filter(x -> x.getProduct().getId().equals(productId))
                        .findFirst().map(x -> x.getProduct().getName()).orElse("Sản phẩm");
                throw new IllegalStateException(
                        "SL hoàn vượt giới hạn cho " + pName + ". Còn cho phép: " + Math.max(0, allow)
                );
            }
        }
        // ---- END VALIDATE ----

        // ✅ Chỉ ghi nhận tổng & ghi chú, KHÔNG khấu trừ công nợ tại bước DUYỆT
        java.math.BigDecimal total = computeTotal(sr);
        sr.setTotalAmount(total);
        sr.setManagerNote(managerNote);

        // Chuyển trạng thái sang ĐÃ DUYỆT (chờ nhận hàng)
        sr.setStatus(ReturnStatus.DA_DUYET);
        salesReturnRepository.save(sr);
    }


    @Override
    @Transactional
    public void reject(Long id, String reason) {
        SalesReturn sr = salesReturnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu hoàn hàng"));
        if (sr.getStatus() != ReturnStatus.CHO_DUYET) {
            throw new IllegalStateException("Chỉ từ chối phiếu ở trạng thái CHỜ DUYỆT.");
        }
        sr.setManagerNote(reason);
        sr.setStatus(ReturnStatus.TU_CHOI);
        salesReturnRepository.save(sr);
    }

    @Override
    @Transactional
    public void receive(Long id, String username, com.eewms.constant.ReturnSettlementOption opt) {
        // 1) Tải phiếu & validate
        SalesReturn sr = salesReturnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu hoàn: " + id));
        if (sr.getStatus() != com.eewms.constant.ReturnStatus.DA_DUYET) {
            throw new IllegalStateException("Chỉ nhận hàng khi phiếu đang ở trạng thái ĐÃ DUYỆT.");
        }

        // 2) Xác định mode xử lý (ưu tiên theo tham số từ form)
        final com.eewms.constant.ReturnSettlementOption mode =
                (opt != null) ? opt :
                        (sr.getSettlementOption() != null ? sr.getSettlementOption()
                                : com.eewms.constant.ReturnSettlementOption.REPLACE_ONLY);
        sr.setSettlementOption(mode);

        // 3) Tổng tiền hoàn
        java.math.BigDecimal total = computeTotal(sr);
        sr.setTotalAmount(total);

        java.math.BigDecimal appliedVnd = java.math.BigDecimal.ZERO; // đã khấu trừ vào công nợ
        java.math.BigDecimal refundVnd = java.math.BigDecimal.ZERO; // số tiền phải hoàn cho KH (nếu có)

        if (mode == com.eewms.constant.ReturnSettlementOption.OFFSET_THEN_REPLACE) {
            // === HOÀN TIỀN: trừ công nợ tối đa, nếu còn dư thì trả khách ===
            if (total.signum() > 0) {
                appliedVnd = debtService.adjustCustomerDebtForSaleOrderPreferGIN(
                        sr.getSaleOrder().getSoId().longValue(),
                        total,
                        "[RETURN] " + sr.getCode());
                if (appliedVnd == null) appliedVnd = java.math.BigDecimal.ZERO;

                refundVnd = total.subtract(appliedVnd);
                //TH số tiền hoàn âm (do dư nợ) -> coi như không hoàn
                if (refundVnd.signum() < 0) refundVnd = java.math.BigDecimal.ZERO;

                //TH số tiền hoàn dương -> tạo phiếu hoàn tiền
                if (refundVnd.signum() > 0) {
                    final SaleOrder soRef = sr.getSaleOrder();
                    final Integer soIdKey = (soRef != null ? soRef.getSoId() : null);
                    final String srCode = (sr.getCode() != null ? sr.getCode() : ("SR-" + sr.getId()));

                    // Idempotent: nếu đã có refund cho phiếu này thì bỏ qua
                    boolean refundedExists = customerRefundRepository.existsByReturnCode(srCode);
                    if (!refundedExists) {
                        CustomerRefund refund = CustomerRefund.builder()
                                .saleOrderSoId(soIdKey)                // khóa nhóm theo SO
                                .returnCode(srCode)                    // tra cứu ngược theo phiếu hoàn
                                .amount(refundVnd)                     // số tiền hoàn dương
                                .method(CustomerRefund.Method.CASH)    // mặc định; PrePersist cũng sẽ set CASH nếu null
                                .referenceNo(null)                     // nếu có số UNC/phiếu chi thì set ở UI/flow thanh toán
                                .note("Hoàn tiền phần dư từ phiếu hoàn " + srCode
                                        + (soRef != null && soRef.getSoCode() != null ? " (đơn " + soRef.getSoCode() + ")" : ""))
                                .build();

                        customerRefundRepository.save(refund);
                    }
                }

                // TODO: nếu bạn muốn ghi lịch sử hoàn tiền cho khách để hiển thị ở màn phiếu xuất,
                // hãy lưu một bản ghi refund riêng (CustomerRefund) hoặc nơi bạn mong muốn hiển thị.
                // Ví dụ: customerRefundRepository.save(...);
            }

            // Không tạo yêu cầu/phiếu đổi hàng
            sr.setReplacementAmount(java.math.BigDecimal.ZERO);
            sr.setNeedsReplacement(false);

        } else {
            // === HOÀN HÀNG: KHÔNG trừ nợ, KHÔNG hoàn tiền ===
            appliedVnd = java.math.BigDecimal.ZERO;
            refundVnd = java.math.BigDecimal.ZERO;

            // Đánh dấu sẽ đổi hàng
            sr.setReplacementAmount(java.math.BigDecimal.ZERO);
            sr.setNeedsReplacement(true);
        }

        // 4) NHẬP KHO HÀNG HOÀN (giữ nguyên logic cũ, idempotent theo requestId)
        final String requestId = "SR-RECV-" + sr.getId();
        java.util.Optional<WarehouseReceipt> existed = warehouseReceiptRepository.findByRequestId(requestId);
        WarehouseReceipt receipt;
        if (existed.isPresent()) {
            receipt = existed.get();
        } else {
            com.eewms.entities.User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user: " + username));

            String soCode = (sr.getSaleOrder() != null && sr.getSaleOrder().getSoCode() != null)
                    ? sr.getSaleOrder().getSoCode() : "N/A";
            String srCode = (sr.getCode() != null) ? sr.getCode() : ("SR-" + sr.getId());
            String note = "Hàng hoàn từ đơn " + soCode + " (phiếu hoàn " + srCode + ")";

            String rnCode = String.format("RN%05d", warehouseReceiptRepository.count() + 1);

            receipt = WarehouseReceipt.builder()
                    .code(rnCode)
                    .createdAt(java.time.LocalDateTime.now())
                    .createdBy(user.getFullName() != null ? user.getFullName() : user.getUsername())
                    .purchaseOrder(null)
                    .warehouse(null)
                    .note(note)
                    .requestId(requestId)
                    .build();
            warehouseReceiptRepository.save(receipt);

            for (SalesReturnItem it : (sr.getItems() == null ? java.util.List.<SalesReturnItem>of() : sr.getItems())) {
                if (it == null || it.getProduct() == null || it.getProduct().getId() == null) continue;
                com.eewms.entities.Product p = productRepository.findById(it.getProduct().getId())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm id=" + it.getProduct().getId()));
                int qty = Math.max(0, it.getQuantity() == null ? 0 : it.getQuantity());
                java.math.BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : java.math.BigDecimal.ZERO;

                WarehouseReceiptItem gri = WarehouseReceiptItem.builder()
                        .warehouseReceipt(receipt)
                        .product(p)
                        .quantity(qty)
                        .actualQuantity(qty)
                        .price(price)
                        .condition(com.eewms.constant.ProductCondition.RETURNED)
                        .build();
                warehouseReceiptItemRepository.save(gri);

                Integer onHand = p.getQuantity() == null ? 0 : p.getQuantity();
                p.setQuantity(onHand + qty);
                productRepository.save(p);
            }
        }

        // 5) Nếu chọn HOÀN HÀNG (REPLACE_ONLY) -> tạo GIN RPL xuất trả TẤT CẢ số lượng hoàn, đơn giá = 0
        if (mode == com.eewms.constant.ReturnSettlementOption.REPLACE_ONLY) {
            final String replCode = String.format("RPL%05d", sr.getId());
            java.util.Optional<GoodIssueNote> existedRpl = goodIssueNoteRepository.findByGinCode(replCode);

            GoodIssueNote gin;
            if (existedRpl.isPresent()) {
                gin = existedRpl.get();
                if (gin.getDetails() != null) gin.getDetails().clear();
            } else {
                com.eewms.entities.User user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user: " + username));
                String soCode = (sr.getSaleOrder() != null && sr.getSaleOrder().getSoCode() != null)
                        ? sr.getSaleOrder().getSoCode() : "N/A";
                String desc = "Đổi hàng từ phiếu hoàn " + sr.getCode() + " (đơn " + soCode + ")";

                gin = GoodIssueNote.builder()
                        .ginCode(replCode)
                        .issueDate(java.time.LocalDateTime.now())
                        .createdBy(user)
                        .customer(sr.getSaleOrder() != null ? sr.getSaleOrder().getCustomer() : null)
                        .saleOrder(sr.getSaleOrder())
                        .description(desc)
                        .totalAmount(java.math.BigDecimal.ZERO)
                        .build();
            }

            java.util.List<com.eewms.entities.GoodIssueDetail> details = new java.util.ArrayList<>();
            for (SalesReturnItem rit : (sr.getItems() == null ? java.util.List.<SalesReturnItem>of() : sr.getItems())) {
                if (rit == null || rit.getProduct() == null) continue;
                com.eewms.entities.GoodIssueDetail d = com.eewms.entities.GoodIssueDetail.builder()
                        .goodIssueNote(gin)
                        .product(rit.getProduct())
                        .quantity(Math.max(0, rit.getQuantity() == null ? 0 : rit.getQuantity()))
                        .price(java.math.BigDecimal.ZERO) // KHÔNG phát sinh công nợ
                        .build();
                details.add(d);
            }
            gin.setDetails(details);
            gin.setTotalAmount(java.math.BigDecimal.ZERO);
            goodIssueNoteRepository.saveAndFlush(gin);

            // Đã tạo RPL → ẩn nút lần sau
            sr.setNeedsReplacement(false);
        }

        // 6) Cập nhật trạng thái phiếu hoàn
        sr.setStatus(com.eewms.constant.ReturnStatus.DA_NHAP_KHO);
        salesReturnRepository.save(sr);

        // (tùy chọn) bạn có thể lưu refundVnd vào ghi chú để dễ truy vết nếu chưa có bảng lịch sử hoàn tiền
        if (refundVnd.signum() > 0) {
            String note = (sr.getManagerNote() == null ? "" : sr.getManagerNote() + " | ");
            sr.setManagerNote(note + "Refund khách: " + refundVnd);
            salesReturnRepository.save(sr);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SalesReturnDTO getById(Long id) {
        SalesReturn sr = salesReturnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu hoàn hàng"));
        return SalesReturnMapper.toDTO(sr);
    }

    @Override
    @Transactional
    public void complete(Long id) {
        SalesReturn sr = salesReturnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu hoàn hàng"));
        if (sr.getStatus() != ReturnStatus.DA_NHAP_KHO) {
            throw new IllegalStateException("Chỉ hoàn tất sau khi đã nhập hàng.");
        }
        sr.setStatus(ReturnStatus.HOAN_TAT);
        salesReturnRepository.save(sr);
    }

    @Override
    @Transactional
    public void createReplacementRequest(Long id, String username) {
        SalesReturn sr = salesReturnRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu hoàn: " + id));

        if (sr.getStatus() != ReturnStatus.DA_NHAP_KHO) {
            throw new IllegalStateException("Chỉ tạo yêu cầu đổi hàng sau khi đã nhập hàng hoàn.");
        }
        if (!sr.isNeedsReplacement()) {
            throw new IllegalStateException("Phiếu này không có (hoặc đã xử lý) phần đổi hàng.");
        }

        final String replCode = String.format("RPL%05d", sr.getId());

        // ĐÃ TỒN TẠI -> ẩn nút & báo rõ
        if (goodIssueNoteRepository.findByGinCode(replCode).isPresent()) {
            sr.setNeedsReplacement(false);
            salesReturnRepository.saveAndFlush(sr);
            throw new IllegalStateException("Yêu cầu đổi hàng đã tồn tại (mã " + replCode + ").");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user: " + username));

        String soCode = (sr.getSaleOrder() != null && sr.getSaleOrder().getSoCode() != null)
                ? sr.getSaleOrder().getSoCode() : "N/A";
        String desc = "Đổi hàng từ phiếu hoàn " + sr.getCode() + " (đơn " + soCode + ")";

        GoodIssueNote gin = GoodIssueNote.builder()
                .ginCode(replCode)
                .issueDate(java.time.LocalDateTime.now())
                .createdBy(user)                                // User (NOT NULL)
                .customer(sr.getSaleOrder() != null ? sr.getSaleOrder().getCustomer() : null)
                .saleOrder(sr.getSaleOrder())                   // so_id (NOT NULL)
                .description(desc)
                .totalAmount(java.math.BigDecimal.ZERO)
                .build();

        goodIssueNoteRepository.saveAndFlush(gin);         // ép INSERT ngay

        // Ẩn nút cho lần sau
        sr.setNeedsReplacement(false);

        List<GoodIssueDetail> details = new java.util.ArrayList<>();
        for (SalesReturnItem rit : (sr.getItems() == null ? java.util.List.<SalesReturnItem>of() : sr.getItems())) {
            if (rit == null || rit.getProduct() == null) continue;

            GoodIssueDetail d = GoodIssueDetail.builder()
                    .goodIssueNote(gin)
                    .product(rit.getProduct())
                    .quantity(Math.max(0, rit.getQuantity() == null ? 0 : rit.getQuantity()))
                    // Nếu field tên là 'price':
                    .price(java.math.BigDecimal.ZERO)             // hoặc rit.getUnitPrice()
                    // Nếu field tên là 'unitPrice', dùng .unitPrice(...)
                    .build();
            details.add(d);
        }
        gin.setDetails(details);

        // Nếu bạn muốn tổng = 0:
        gin.setTotalAmount(java.math.BigDecimal.ZERO);
        // (Nếu muốn tổng = sum, thì tính theo price ở trên)

        // LƯU Ý: thêm details TRƯỚC khi flush
        goodIssueNoteRepository.saveAndFlush(gin);

        sr.setNeedsReplacement(false);
        salesReturnRepository.saveAndFlush(sr);
    }

}
