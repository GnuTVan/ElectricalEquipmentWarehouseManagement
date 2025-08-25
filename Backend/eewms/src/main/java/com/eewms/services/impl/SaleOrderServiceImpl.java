package com.eewms.services.impl;

import com.eewms.constant.ItemOrigin;
import com.eewms.dto.SaleOrderDetailDTO;
import com.eewms.dto.SaleOrderMapper;
import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.*;
import com.eewms.services.ISaleOrderService;
import com.eewms.utils.ComboUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements ISaleOrderService {

    private final SaleOrderDetailRepository saleOrderDetailRepository;
    private final ProductRepository productRepo;

    private final SaleOrderRepository orderRepo;
    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final GoodIssueNoteRepository goodIssueRepository;
    private final ComboRepository comboRepository;
    private final SaleOrderComboRepository saleOrderComboRepository;

    @Override
    public String generateNextCode() {
        return generateOrderCode();
    }

    @Override
    @Transactional
    public SaleOrderResponseDTO createOrder(SaleOrderRequestDTO dto, String createdByUsername) {

        Customer customer = customerRepo.findById(dto.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        User user = userRepo.findByUsername(createdByUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String orderCode = (dto.getSoCode() != null && !dto.getSoCode().isBlank())
                ? dto.getSoCode()
                : generateNextCode();

        SaleOrder.SaleOrderStatus initStatus =
                (dto.getStatus() != null) ? dto.getStatus() : SaleOrder.SaleOrderStatus.PENDING;

        SaleOrder saleOrder = new SaleOrder();
        saleOrder.setSoCode(orderCode);
        saleOrder.setCustomer(customer);
        saleOrder.setCreatedByUser(user);
        saleOrder.setStatus(initStatus);

        List<SaleOrderDetail> detailList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // ===== 1) Manual details (nếu có)
        if (dto.getDetails() != null) {
            for (SaleOrderDetailDTO item : dto.getDetails()) {
                // BỎ QUA những dòng combo do FE render (fromCombo=true) — combo xử lý ở bước 2
                if (item.isFromCombo()) continue;

                Product product = productRepo.findById(item.getProductId())
                        .orElseThrow(() -> new RuntimeException("Product not found"));

                // Cho phép đặt > tồn kho ở bước lưu đơn (logic thiếu hàng xử lý lúc xuất kho)
                SaleOrderDetail detail = SaleOrderMapper.toOrderDetail(item, product);
                detail.setSale_order(saleOrder);
                detail.setOrigin(ItemOrigin.MANUAL);
                detail.setCombo(null);

                detailList.add(detail);
                BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getOrderedQuantity()));
                totalAmount = totalAmount.add(lineTotal);
            }
        }

        // ===== 2) Combo details...
        Map<Integer, SaleOrderDetail> comboLines = expandCombosToDetails(dto.getComboIds());
        for (SaleOrderDetail line : comboLines.values()) {
            Product p = line.getProduct();
            Integer pid = (p != null ? p.getId() : null);
            if (pid == null) {
                throw new IllegalStateException("Combo expand trả về dòng thiếu productId.");
            }
            Product managed = productRepo.getReferenceById(pid);
            line.setProduct(managed);

            if (line.getPrice() == null) {
                line.setPrice(Optional.ofNullable(managed.getListingPrice()).orElse(BigDecimal.ZERO));
            }
            if (line.getOrderedQuantity() == null || line.getOrderedQuantity() <= 0) {
                throw new IllegalStateException("Combo expand trả về dòng có số lượng không hợp lệ.");
            }

            // Cho phép > tồn kho khi lưu đơn
            line.setSale_order(saleOrder);
            line.setOrigin(ItemOrigin.COMBO);
            detailList.add(line);
            totalAmount = totalAmount.add(line.getPrice().multiply(BigDecimal.valueOf(line.getOrderedQuantity())));
        }

        if (detailList.isEmpty()) {
            throw new RuntimeException("Chi tiết đơn hàng trống");
        }

        // ===== 3) Description + nhãn combo (KHÔNG tự chèn cảnh báo thiếu hàng) =====
        // >>> SỬA: chỉ lấy mô tả từ form và nối nhãn combo (nếu có), không chèn “thiếu hàng”
        String baseDesc = Optional.ofNullable(dto.getDescription()).orElse("").trim();

        if (dto.getComboIds() != null && !dto.getComboIds().isEmpty()) {
            List<Combo> combos = comboRepository.findAllById(dto.getComboIds());
            String comboLabel = combos.stream()
                    .map(Combo::getName).filter(Objects::nonNull).distinct()
                    .collect(Collectors.joining(", "));
            if (!comboLabel.isBlank()) {
                String suffix = "Đơn có combo: " + comboLabel;
                baseDesc = baseDesc.isBlank() ? suffix : (baseDesc + " | " + suffix);
            }
        }
        saleOrder.setDescription(baseDesc);
        // ===== HẾT GHÉP MÔ TẢ =====

        saleOrder.setDetails(detailList);
        saleOrder.setTotalAmount(totalAmount);

        // ===== 4) Payment init (KHÔNG dùng PayOS cho SaleOrder) =====
        String paymentNote = String.format("Thanh toan don %s", orderCode);
        saleOrder.setPaymentNote(paymentNote);
        saleOrder.setPaymentStatus(SaleOrder.PaymentStatus.NONE_PAYMENT);
        String qr = null, link = null; // giữ biến để mapper cuối hàm, nhưng luôn null

        // ===== 5) Lưu Order
        orderRepo.save(saleOrder);
        log.info("[SaleOrder][save] id={} soCode={}", saleOrder.getSoId(), saleOrder.getSoCode());

        // Lưu selections combo vào sale_order_combos
        if (dto.getComboIds() != null && !dto.getComboIds().isEmpty()) {
            Map<Long, Long> counts = dto.getComboIds().stream()
                    .collect(Collectors.groupingBy(id -> id, LinkedHashMap::new, Collectors.counting()));

            for (Map.Entry<Long, Long> e : counts.entrySet()) {
                Combo combo = comboRepository.findById(e.getKey())
                        .orElseThrow(() -> new RuntimeException("Combo not found: " + e.getKey()));
                SaleOrderCombo soc = SaleOrderCombo.builder()
                        .saleOrder(saleOrder)
                        .combo(combo)
                        .quantity(e.getValue().intValue())
                        .build();
                saleOrderComboRepository.save(soc);
            }
        }

        SaleOrderResponseDTO resp = SaleOrderMapper.toOrderResponseDTO(saleOrder);
        resp.setQrCodeUrl(null);
        resp.setPaymentLink(null);
        return resp;

    }

    private Map<Integer, SaleOrderDetail> expandCombosToDetails(List<Long> comboIds) {
        Map<Integer, SaleOrderDetail> byPid = new LinkedHashMap<>();
        if (comboIds == null || comboIds.isEmpty()) return byPid;

        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Long id : comboIds) counts.put(id, counts.getOrDefault(id, 0L) + 1);

        List<Combo> base = comboRepository.findAllById(counts.keySet());

        List<Combo> expanded = new ArrayList<>();
        for (Combo c : base) {
            long times = counts.getOrDefault(c.getId(), 0L);
            for (int i = 0; i < times; i++) expanded.add(c);
        }

        return ComboUtils.expandFromEntities(expanded);
    }

    @Override
    public List<Long> getComboIdsExpanded(Integer soId) {
        SaleOrder order = orderRepo.findById(soId)
                .orElseThrow(() -> new EntityNotFoundException("Sale order not found"));
        List<SaleOrderCombo> list = saleOrderComboRepository.findBySaleOrder(order);

        List<Long> ids = new ArrayList<>();
        for (SaleOrderCombo rec : list) {
            int times = Optional.ofNullable(rec.getQuantity()).orElse(1);
            for (int i = 0; i < times; i++) ids.add(rec.getCombo().getId());
        }
        return ids;
    }

    @Transactional
    @Override
    public void updateOrderItems(Integer orderId, SaleOrderRequestDTO form) {
        SaleOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sale order not found"));

        if (order.getStatus() != SaleOrder.SaleOrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING order can be edited");
        }

        saleOrderDetailRepository.deleteByOrderSoId(orderId);

        Map<Long, Integer> counts = Optional.ofNullable(form.getComboCounts())
                .orElseGet(LinkedHashMap::new);
        Map<Integer, SaleOrderDetail> comboAgg = expandCombosFromCounts(counts);

        List<SaleOrderDetail> lines = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;

        if (form.getDetails() != null && !form.getDetails().isEmpty()) {
            Set<Integer> pods = form.getDetails().stream()
                    .map(SaleOrderDetailDTO::getProductId).collect(Collectors.toSet());
            Map<Integer, Product> pmap = productRepo.findAllById(pods).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p));

            for (SaleOrderDetailDTO d : form.getDetails()) {
                int pid = d.getProductId();
                Product p = pmap.get(pid);
                if (p == null || p.getStatus() != Product.ProductStatus.ACTIVE) {
                    throw new IllegalArgumentException("Invalid/inactive product: " + pid);
                }
                int qty = Optional.ofNullable(d.getOrderedQuantity()).orElse(0);
                if (qty <= 0) throw new IllegalArgumentException("Số lượng phải > 0");
                BigDecimal price = Optional.ofNullable(p.getListingPrice()).orElse(BigDecimal.ZERO);

                SaleOrderDetail line = new SaleOrderDetail();
                line.setSale_order(order);
                line.setProduct(p);
                line.setOrderedQuantity(qty);
                line.setPrice(price);
                line.setOrigin(ItemOrigin.MANUAL);
                lines.add(line);

                sum = sum.add(price.multiply(BigDecimal.valueOf(qty)));
            }
        }

        for (SaleOrderDetail cLine : comboAgg.values()) {
            // chuẩn hoá entity Product ở đây
            Integer pid = cLine.getProduct() != null ? cLine.getProduct().getId() : null;
            if (pid == null) throw new IllegalStateException("Combo line missing productId");
            Product managed = productRepo.getReferenceById(pid);
            cLine.setProduct(managed);

            cLine.setSale_order(order);
            lines.add(cLine);
            sum = sum.add(cLine.getPrice().multiply(BigDecimal.valueOf(cLine.getOrderedQuantity())));
        }

        saleOrderDetailRepository.saveAll(lines);

        saleOrderComboRepository.deleteBySaleOrder(order);
        if (!counts.isEmpty()) {
            List<Combo> base = comboRepository.findAllById(counts.keySet());
            Map<Long, Combo> byId = base.stream().collect(Collectors.toMap(Combo::getId, c -> c));
            for (Map.Entry<Long, Integer> e : counts.entrySet()) {
                Combo combo = byId.get(e.getKey());
                if (combo == null) throw new RuntimeException("Combo not found: " + e.getKey());
                saleOrderComboRepository.save(SaleOrderCombo.builder()
                        .saleOrder(order)
                        .combo(combo)
                        .quantity(Optional.ofNullable(e.getValue()).orElse(0))
                        .build());
            }
        }

        order.setTotalAmount(sum);
        // >>> SỬA: Chỉ set mô tả theo form, KHÔNG auto-append “Thiếu hàng: …”
        order.setDescription(Optional.ofNullable(form.getDescription()).orElse(""));
        orderRepo.save(order);
    }

    private Map<Integer, SaleOrderDetail> expandCombosFromCounts(Map<Long, Integer> counts) {
        Map<Integer, SaleOrderDetail> acc = new LinkedHashMap<>();
        if (counts == null || counts.isEmpty()) return acc;

        List<Long> ids = new ArrayList<>(counts.keySet());
        List<Combo> combos = comboRepository.findAllById(ids);

        List<Combo> expanded = new ArrayList<>();
        for (Combo c : combos) {
            int times = Optional.ofNullable(counts.get(c.getId())).orElse(0);
            for (int i = 0; i < times; i++) expanded.add(c);
        }
        return ComboUtils.expandFromEntities(expanded);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleOrderResponseDTO> getAllOrders() {
        // Dùng paging + EntityGraph (customer, createdByUser) để tránh LAZY ngoài TX.
        Pageable pageable = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "soId"));
        return orderRepo.findAllByOrderBySoIdDesc(pageable)
                .map(SaleOrderMapper::toOrderListDTO) // LITE: không truy cập details
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public SaleOrderResponseDTO getById(Integer orderId) {
        // Fetch-join details + product để mapper chi tiết không bị LAZY
        SaleOrder saleOrder = orderRepo.findByIdWithDetails(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        SaleOrderResponseDTO dto = SaleOrderMapper.toOrderResponseDTO(saleOrder);

        boolean exported = goodIssueRepository.existsBySaleOrder_SoId(saleOrder.getSoId());
        boolean stillMissing = !exported && saleOrder.getDetails().stream()
                .anyMatch(d -> d.getProduct().getQuantity() < d.getOrderedQuantity());

        dto.setHasInsufficientStock(stillMissing);
        dto.setAlreadyExported(exported);

        // đảm bảo có thông tin thanh toán cho UI
        dto.setPaymentStatus(saleOrder.getPaymentStatus());
        dto.setPaymentNote(saleOrder.getPaymentNote());

        return dto;
    }

    @Transactional
    @Override
    public void updateOrderStatus(Integer orderId, SaleOrder.SaleOrderStatus newStatus) {
        SaleOrder saleOrder = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        SaleOrder.SaleOrderStatus currentStatus = saleOrder.getStatus();

        if (currentStatus == SaleOrder.SaleOrderStatus.COMPLETED) {
            throw new RuntimeException("Đơn hàng đã hoàn thành không thể cập nhật.");
        }

        if (currentStatus == SaleOrder.SaleOrderStatus.PENDING && newStatus == SaleOrder.SaleOrderStatus.DELIVERIED) {
            saleOrder.setStatus(SaleOrder.SaleOrderStatus.DELIVERIED);
        } else if (currentStatus == SaleOrder.SaleOrderStatus.DELIVERIED && newStatus == SaleOrder.SaleOrderStatus.COMPLETED) {
            saleOrder.setStatus(SaleOrder.SaleOrderStatus.COMPLETED);
        } else {
            throw new RuntimeException("Không thể cập nhật từ " + currentStatus + " sang " + newStatus);
        }

        orderRepo.save(saleOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaleOrderResponseDTO> searchByKeyword(String keyword) {
        // Tìm kiếm cho trang list → dùng LITE để tránh LAZY
        return orderRepo.searchByKeyword(keyword).stream()
                .map(SaleOrderMapper::toOrderListDTO)
                .collect(Collectors.toList());
    }

    private String generateOrderCode() {
        long next = Optional.ofNullable(orderRepo.findMaxSoCodeNumber()).orElse(0L) + 1L;
        return String.format("ORD%05d", next);
    }

    @Override
    public SaleOrder getOrderEntityById(Integer id) {
        return orderRepo.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }

    @Override
    @Transactional
    public void updatePaymentStatus(Integer orderId, SaleOrder.PaymentStatus status) {
        SaleOrder so = orderRepo.findById(orderId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("SaleOrder not found: " + orderId));
        if (so.getPaymentStatus() != status) {
            so.setPaymentStatus(status);
            orderRepo.save(so);
        }
    }

    /* ========================== NEW: deleteIfPending ========================== */
    @Transactional
    public void deleteIfPending(Integer id) {
        SaleOrder so = orderRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng #" + id));

        if (so.getStatus() != SaleOrder.SaleOrderStatus.PENDING) {
            throw new IllegalStateException("Chỉ được xoá đơn ở trạng thái 'Chờ lấy hàng'.");
        }

        // Không cho xoá nếu đã có phiếu xuất (phòng thủ)
        if (goodIssueRepository.existsBySaleOrder_SoId(id)) {
            throw new IllegalStateException("Đơn đã có phiếu xuất, không thể xoá.");
        }

        // Xoá chi tiết & combos trước để tránh ràng buộc khoá ngoại
        saleOrderDetailRepository.deleteByOrderSoId(id);
        saleOrderComboRepository.deleteBySaleOrder(so);

        orderRepo.delete(so);
        log.info("[SaleOrder][delete] id={} code={}", id, so.getSoCode());
    }

    @Override
    public Page<SaleOrder> searchWithFilters(
            String keyword,
            SaleOrder.SaleOrderStatus status,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        return orderRepo.searchWithFilters(
                (keyword != null && !keyword.isBlank()) ? keyword.trim() : null,
                status,
                from,
                to,
                PageRequest.of(page, size)
        );
    }
}
