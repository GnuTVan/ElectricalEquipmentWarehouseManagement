package com.eewms.services.impl;

import com.eewms.constant.ItemOrigin;
import com.eewms.dto.SaleOrderDetailDTO;
import com.eewms.dto.SaleOrderMapper;
import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.*;
import com.eewms.repository.*;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.IPayOsService;
import com.eewms.services.ISaleOrderService;
import com.eewms.utils.ComboUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.eewms.exception.InventoryException;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j

@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements ISaleOrderService {


    private final SaleOrderDetailRepository saleOrderDetailRepository;
    private final ProductRepository productRepository;

    private final SaleOrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final GoodIssueNoteRepository goodIssueRepository;
    private final ComboRepository comboRepository;
    //sale order combo

    private final SaleOrderComboRepository saleOrderComboRepository;

    // nạp dịch vụ PayOS
    private final IPayOsService payOsService;

    @Value("${payos.enabled:true}")
    private boolean payOsEnabled;

    @Override
    @Transactional
    public SaleOrderResponseDTO createOrder(SaleOrderRequestDTO dto, String createdByUsername) {

        Customer customer = customerRepo.findById(dto.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        User user = userRepo.findByUsername(createdByUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String orderCode = generateOrderCode();
        SaleOrder saleOrder = new SaleOrder();
        saleOrder.setSoCode(orderCode);
        saleOrder.setCustomer(customer);
        saleOrder.setCreatedByUser(user);
        saleOrder.setStatus(SaleOrder.SaleOrderStatus.PENDING);

        List<SaleOrderDetail> detailList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        StringBuilder warningNote = new StringBuilder();
        boolean hasInsufficientStock = false;

        if (dto.getDetails() == null || dto.getDetails().isEmpty()) {
            throw new RuntimeException("Chi tiết đơn hàng trống");
        }

        for (SaleOrderDetailDTO item : dto.getDetails()) {
            Product product = productRepo.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (product.getQuantity() < item.getOrderedQuantity()) {
                hasInsufficientStock = true;
                warningNote.append(String.format("- Sản phẩm %s thiếu hàng (YC: %d / Tồn: %d)\n",
                        product.getName(), item.getOrderedQuantity(), product.getQuantity()));
            }

            // Mapper hiện tại của bạn
            SaleOrderDetail detail = SaleOrderMapper.toOrderDetail(item, product);
            detail.setSale_order(saleOrder); // giữ nguyên theo entity của bạn

            // 🔹 NEW: gán nguồn + combo cho detail
            if (Boolean.TRUE.equals(item.isFromCombo())) {
                detail.setOrigin(ItemOrigin.COMBO);
                if (item.getComboId() != null) {
                    Combo cb = comboRepository.findById(item.getComboId())
                            .orElse(null); // có thể null, không bắt buộc
                    detail.setCombo(cb);
                } else {
                    detail.setCombo(null);
                }
            } else {
                detail.setOrigin(ItemOrigin.MANUAL);
                detail.setCombo(null);
            }

            detailList.add(detail);

            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getOrderedQuantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        // ===== GHÉP MÔ TẢ (giữ nguyên logic cũ của bạn) =====
        String baseDesc;
        if (hasInsufficientStock) {
            baseDesc = "Đơn hàng thiếu hàng, cần nhập thêm để hoàn thành:\n" + warningNote.toString().trim();
        } else {
            baseDesc = Optional.ofNullable(dto.getDescription()).orElse("").trim();
        }

        if (dto.getComboIds() != null && !dto.getComboIds().isEmpty()) {
            List<Combo> combos = comboRepository.findAllById(dto.getComboIds());
            String comboLabel = combos.stream()
                    .map(Combo::getName)
                    .filter(Objects::nonNull)
                    .distinct()
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

        log.info("[PayOS][switch] enabled={}", payOsEnabled);

        // ===== Tạo paymentNote & gọi PayOS (có fallback) =====
        String paymentNote = String.format("Thanh toan don %s",
                orderCode);
        saleOrder.setPaymentNote(paymentNote);
        saleOrder.setPaymentStatus(SaleOrder.PaymentStatus.NONE_PAYMENT); // luôn khởi tạo PENDING

        String warnPay = null;
        String qr = null, link = null;

        log.info("[PayOS][switch] enabled={}", payOsEnabled);

        if (payOsEnabled) {
            try {
                // Ép kiểu amount an toàn, phát hiện sai sớm nếu có phần lẻ
                long amountVnd = totalAmount.longValueExact();

                // Tạo orderCode dạng SỐ dành riêng cho PayOS (6–9 chữ số, hạn chế trùng)
                long payOrderCode = (System.currentTimeMillis() / 100) % 1_000_000_000L;

                // Log đủ ngữ cảnh trước khi gọi PayOS (ghi số)
                log.info("[PayOS][pre-call] orderCode={} amount={} desc={}", payOrderCode, amountVnd, paymentNote);

                // Gọi PayOS với mã SỐ
                var payRes = payOsService.createOrder(String.valueOf(payOrderCode), amountVnd, paymentNote);

                if (payRes != null && payRes.isSuccess()) {
                    // 1) Mã đơn PayOS
                    if (payRes.getOrderCode() != null) {
                        saleOrder.setPayOsOrderCode(String.valueOf(payRes.getOrderCode())); // chú ý tên setter khớp field!
                    }

                    // 2) Link thanh toán (ưu tiên checkoutUrl)
                    link = payRes.getPaymentLink();
                    qr = link; // FE dùng link để render QR

                    // 3) Lưu link vào cột hiện có (tạm dùng payment_note để hiển thị trên UI)
                    if (link != null && !link.isBlank()) {
                        saleOrder.setPaymentNote(link);
                    }

                    log.info("[PayOS][serviceimpl] success=true orderCode={} link={}", payRes.getOrderCode(), link);
                } else {
                    warnPay = (payRes == null)
                            ? "Không nhận được phản hồi từ PayOS (payRes=null)."
                            : ("PayOS trả lỗi: code=" + payRes.getCode() + " desc=" + payRes.getDesc());
                    log.warn("[PayOS][serviceimpl] {}", warnPay);
                }
            } catch (ArithmeticException ex) {
                // Trường hợp totalAmount có phần lẻ/ngoài biên long
                warnPay = "Số tiền không phù hợp định dạng số nguyên (VND).";
                log.warn("[PayOS][serviceimpl][amount] {} totalAmount={}", warnPay, totalAmount, ex);
            } catch (InventoryException ex) {
                warnPay = ex.getMessage();
                log.warn("[PayOS][serviceimpl][InventoryException] {}", warnPay);
            } catch (RuntimeException ex) {
                warnPay = "Lỗi kết nối PayOS: " + ex.getMessage();
                log.warn("[PayOS][serviceimpl][RuntimeException] {}", warnPay, ex);
            }
        } else {
            warnPay = "PayOS đang tắt ở môi trường hiện tại.";
            log.warn("[PayOS][serviceimpl] {}", warnPay);
        }

        // Lưu SaleOrder trước để có soId, quan hệ details…
        orderRepo.save(saleOrder);
        log.info("[SaleOrder][save] id={} soCode={} payOsOrderCode={}",
                saleOrder.getSoId(), saleOrder.getSoCode(), saleOrder.getPayOsOrderCode());
        log.info("[SaleOrder][after-save] soId={} payOsOrderCode={} paymentLink={}",
                saleOrder.getSoId(), saleOrder.getPayOsOrderCode(), link);


        // 🔹 NEW: Lưu selections combo vào sale_order_combos (để EDIT pre‑select chip ×N)
        if (dto.getComboIds() != null && !dto.getComboIds().isEmpty()) {
            Map<Long, Long> counts = dto.getComboIds().stream()
                    .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

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
        // Nếu có cảnh báo PayOS thì nối gọn vào description để tra cứu nhanh (không bắt buộc)
        if (warnPay != null && !warnPay.isBlank()) {
            String d = Optional.ofNullable(saleOrder.getDescription()).orElse("");
            d = (d.isBlank() ? "" : (d + " | ")) + "[PAYOS] " + warnPay;
            saleOrder.setDescription(d); // ✅ giữ nguyên paymentNote = link
            orderRepo.save(saleOrder);
        }


// Trả response + kèm QR/link nếu có
        SaleOrderResponseDTO resp = SaleOrderMapper.toOrderResponseDTO(saleOrder);
// ⚠️ Đảm bảo DTO có field: private String qrCodeUrl; private String paymentLink;
        resp.setQrCodeUrl(qr);
        resp.setPaymentLink(link);
        return resp;

    }

    // Helper: mở rộng comboIds thành map productId -> SaleOrderDetail (origin COMBO)
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

        // 1) Xoá detail cũ
        saleOrderDetailRepository.deleteByOrderSoId(orderId);

// 2) COMBO trước để biết pid thuộc combo
        Map<Integer, SaleOrderDetail> comboAgg =
                expandCombosFromCounts(form.getComboCounts());

// 3) Manual: loại những pid trùng combo (chặn hoàn toàn)
        List<SaleOrderDetail> lines = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;

// set pidCombo để chặn manual
        Set<Integer> pidCombo = comboAgg.keySet();

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

// 4) add combo lines
        for (SaleOrderDetail cLine : comboAgg.values()) {
            cLine.setSale_order(order);
            lines.add(cLine);
            sum = sum.add(cLine.getPrice().multiply(BigDecimal.valueOf(cLine.getOrderedQuantity())));
        }

        saleOrderDetailRepository.saveAll(lines);

// 5) Đồng bộ bảng sale_order_combos theo counts
        saleOrderComboRepository.deleteBySaleOrder(order);
        if (form.getComboCounts() != null && !form.getComboCounts().isEmpty()) {
            Map<Long, Integer> counts = new LinkedHashMap<>(form.getComboCounts());
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

// 6) Tổng tiền + cảnh báo như cũ...
        order.setTotalAmount(sum);
        order.setDescription(Optional.ofNullable(form.getDescription()).orElse(""));
        StringBuilder warn = new StringBuilder();
        for (SaleOrderDetail l : lines) {
            int stock = Optional.ofNullable(l.getProduct().getQuantity()).orElse(0);
            if (l.getOrderedQuantity() > stock) {
                warn.append("\nThiếu hàng: ").append(l.getProduct().getName())
                        .append(" cần ").append(l.getOrderedQuantity())
                        .append(" tồn ").append(stock);
            }
        }
        if (warn.length() > 0) order.setDescription((order.getDescription() + "\n" + warn).trim());
        orderRepo.save(order);
    }

    private Map<Integer, SaleOrderDetail> expandCombosFromCounts(Map<Long, Integer> counts) {
        Map<Integer, SaleOrderDetail> acc = new LinkedHashMap<>();
        if (counts == null || counts.isEmpty()) return acc;

        List<Long> ids = new ArrayList<>(counts.keySet());
        List<Combo> combos = comboRepository.findAllById(ids);

        // nhân theo count
        List<Combo> expanded = new ArrayList<>();
        for (Combo c : combos) {
            int times = Optional.ofNullable(counts.get(c.getId())).orElse(0);
            for (int i = 0; i < times; i++) expanded.add(c);
        }
        return ComboUtils.expandFromEntities(expanded); // đã set origin=COMBO & price=listingPrice
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
            // Trạng thái hợp lệ
            saleOrder.setStatus(SaleOrder.SaleOrderStatus.DELIVERIED);
        } else if (currentStatus == SaleOrder.SaleOrderStatus.DELIVERIED && newStatus == SaleOrder.SaleOrderStatus.COMPLETED) {
            // Trạng thái hợp lệ
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
        long count = orderRepo.count() + 1;
        return String.format("ORD%05d", count);
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
                .orElseThrow(() -> new EntityNotFoundException("SaleOrder not found: " + orderId));
        if (so.getPaymentStatus() != status) {
            so.setPaymentStatus(status);
            orderRepo.save(so);
        }
    }

    @Override
    public void regeneratePayOsOrder(Integer saleOrderId) {
        SaleOrder so = orderRepo.findById(saleOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));

        if (so.getPaymentStatus() == SaleOrder.PaymentStatus.PAID) {
            throw new IllegalStateException("Đơn đã thanh toán, không thể tạo lại QR");
        }

        // Sinh orderCode mới dạng số (PayOS yêu cầu numeric)
        long newOrderCode = Long.parseLong(
                (System.currentTimeMillis() % 1000000000000L) + "" + (int) (Math.random() * 900 + 100)
        );

        long amount = so.getTotalAmount() != null ? so.getTotalAmount().longValue() : 0L;
        String desc = ("SO#" + so.getSoCode()).length() > 25 ? ("SO#" + so.getSoCode()).substring(0, 25) : ("SO#" + so.getSoCode());

        var resp = payOsService.createOrder(String.valueOf(newOrderCode), amount, desc);
        if (resp == null || !resp.isSuccess()) {
            throw new IllegalStateException("PayOS không trả về liên kết thanh toán hợp lệ");
        }

        // Cập nhật đơn: gắn mã PayOS mới, set PENDING, lưu link/QR
        so.setPayOsOrderCode(String.valueOf(newOrderCode));
        so.setPaymentStatus(SaleOrder.PaymentStatus.PENDING);
        // nếu bạn có field riêng paymentLink/qrCode thì set; nếu chưa, tạm dùng paymentNote
        String link = resp.getCheckoutUrl() != null ? resp.getCheckoutUrl() : resp.getPaymentLink();
        if (link != null) {
            so.setPaymentNote(link);
        }
        orderRepo.save(so);
    }

}
