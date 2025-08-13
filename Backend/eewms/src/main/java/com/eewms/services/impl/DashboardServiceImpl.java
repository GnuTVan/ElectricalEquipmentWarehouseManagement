package com.eewms.services.impl;

import com.eewms.dto.dashboard.*;
import com.eewms.entities.GoodIssueNote;
import com.eewms.entities.Product;
import com.eewms.entities.WarehouseReceipt;
import com.eewms.entities.WarehouseReceiptItem;
import com.eewms.repository.*;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements IDashboardService {

    private final WarehouseReceiptRepository receiptRepo;
    private final WarehouseReceiptItemRepository receiptItemRepo;

    private final GoodIssueNoteRepository issueRepo;
    private final ProductRepository productRepo;

    private final SaleOrderRepository saleOrderRepo;
    private final SaleOrderComboRepository saleOrderComboRepo;

    private static final int LOW_STOCK_DEFAULT_LIMIT = 10;

    // ====================== SUMMARY ======================
    @Override
    public DashboardSummaryDTO getSummary(LocalDate from, LocalDate to) {
        LocalDate f = (from != null) ? from : LocalDate.now().minusDays(29);
        LocalDate t = (to   != null) ? to   : LocalDate.now();
        LocalDateTime fromDT = f.atStartOfDay();
        LocalDateTime toDT   = t.atTime(LocalTime.MAX);

        // Nhập kho: lọc theo createdAt (WarehouseReceipt không có receiptDate)
        List<WarehouseReceipt> receiptsInRange = receiptRepo.findAll().stream()
                .filter(r -> {
                    LocalDateTime cat = r.getCreatedAt();
                    return cat != null && !cat.isBefore(fromDT) && !cat.isAfter(toDT);
                })
                .toList();

        long receiptCount = receiptsInRange.size();
        //quantity in : SL nhap trong ky
        long qtyIn = receiptsInRange.stream()
                .mapToLong(r -> receiptItemRepo.findByWarehouseReceipt(r).stream()
                        .mapToLong(this::readItemQuantitySafe)
                        .sum())
                .sum();

        // PXK trong kỳ (đúng tên method repo hiện có)
        List<GoodIssueNote> issues = issueRepo.findByIssueDateBetweenWithDetails(fromDT, toDT);

        long issueCount = issues.size();
        BigDecimal issueAmount = issues.stream()
                .map(g -> g.getTotalAmount() != null ? g.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        //quantity out: tong SL xuat trong ky
        long qtyOut = issues.stream()
                .flatMap(g -> safeList(g.getDetails()).stream())
                .mapToLong(d -> d.getQuantity() != null ? d.getQuantity() : 0L)
                .sum();

        BigDecimal inventoryValue = Optional.ofNullable(productRepo.sumInventoryValue())
                .orElse(BigDecimal.ZERO);
        BigDecimal receiptAmount = receiptsInRange.stream()
                .map(this::readReceiptTotalAmount)             // WR.totalAmount | PO.totalAmount | sum(item.price*qty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //ton <= 5 tinh la sap het hang
        int lowStockCount = (int) productRepo.findTop10ByOrderByQuantityAsc().stream()
                .filter(p -> p.getQuantity() != null && p.getQuantity() <= 5)
                .count();
        //don hang o trang thai pending tinh la don hang chua hoan tat
        int pendingOrders = (int) saleOrderRepo.countPending();

        return DashboardSummaryDTO.builder()
                .receiptCount(receiptCount)
                .receiptAmount(receiptAmount)
                .issueCount(issueCount)
                .issueAmount(issueAmount)
                .qtyIn(qtyIn)
                .qtyOut(qtyOut)
                .netQty(qtyIn - qtyOut)
                .inventoryValue(inventoryValue)
                .lowStockCount(lowStockCount)
                .pendingOrdersCount(pendingOrders)
                .build();
    }


    @Override
    public List<DailyFlowDTO> getDailyFlow(LocalDate from, LocalDate to) {
        LocalDate f = (from != null) ? from : LocalDate.now().minusDays(29);
        LocalDate t = (to   != null) ? to   : LocalDate.now();
        LocalDateTime fromDT = f.atStartOfDay();
        LocalDateTime toDT   = t.atTime(LocalTime.MAX);

        Map<LocalDate, DailyFlowDTO> map = new LinkedHashMap<>();
        for (LocalDate d = f; !d.isAfter(t); d = d.plusDays(1)) {
            map.put(d, DailyFlowDTO.builder()
                    .day(d)
                    .receiptQty(0)
                    .issueQty(0)
                    .receiptAmount(BigDecimal.ZERO)
                    .issueAmount(BigDecimal.ZERO)   // <-- KHỞI TẠO ZERO
                    .build());
        }

        // Nhập: theo createdAt (giữ nguyên)
        receiptRepo.findAll().stream()
                .filter(r -> {
                    LocalDateTime cat = r.getCreatedAt();
                    return cat != null && !cat.isBefore(fromDT) && !cat.isAfter(toDT);
                })
                .forEach(r -> {
                    LocalDate d = r.getCreatedAt().toLocalDate();
                    DailyFlowDTO bin = map.get(d);
                    if (bin == null) return;

                    long qty = receiptItemRepo.findByWarehouseReceipt(r).stream()
                            .mapToLong(this::readItemQuantitySafe).sum();
                    bin.setReceiptQty(bin.getReceiptQty() + qty);

                    // Nếu sau này có tiền nhập, cộng tại đây:
                    // bin.setReceiptAmount(bin.getReceiptAmount().add(readReceiptTotalAmount(r)));
                });

        // Xuất: theo issueDate + details.quantity + TỔNG TIỀN
        for (GoodIssueNote g : issueRepo.findByIssueDateBetweenWithDetails(fromDT, toDT)) {
            LocalDate d = (g.getIssueDate() != null) ? g.getIssueDate().toLocalDate() : null;
            if (d == null) continue;
            DailyFlowDTO bin = map.get(d);
            if (bin == null) continue;

            // Số lượng xuất
            long qty = safeList(g.getDetails()).stream()
                    .mapToLong(it -> it.getQuantity() != null ? it.getQuantity() : 0L)
                    .sum();
            bin.setIssueQty(bin.getIssueQty() + qty);

            // TIỀN xuất: ưu tiên totalAmount, nếu null thì tính từ chi tiết (price * qty)
            BigDecimal amt = (g.getTotalAmount() != null) ? g.getTotalAmount()
                    : safeList(g.getDetails()).stream()
                    .map(it -> {
                        BigDecimal price = (it.getPrice() != null) ? it.getPrice() : BigDecimal.ZERO;
                        long q = (it.getQuantity() != null) ? it.getQuantity() : 0L;
                        return price.multiply(BigDecimal.valueOf(q));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            bin.setIssueAmount(bin.getIssueAmount().add(amt));  // <-- QUAN TRỌNG
        }

        return new ArrayList<>(map.values());
    }


    // ====================== TOP LISTS ======================
    @Override
    public List<TopSupplierDTO> getTopSuppliers(LocalDate from, LocalDate to, int limit) {
        LocalDate f = (from != null) ? from : LocalDate.now().minusDays(29);
        LocalDate t = (to   != null) ? to   : LocalDate.now();
        LocalDateTime fromDT = f.atStartOfDay();
        LocalDateTime toDT   = t.atTime(LocalTime.MAX);

        List<WarehouseReceipt> receipts = receiptRepo.findAll().stream()
                .filter(r -> {
                    LocalDateTime cat = r.getCreatedAt();
                    return cat != null && !cat.isBefore(fromDT) && !cat.isAfter(toDT);
                })
                .toList();

        Map<String, TopSupplierDTO> agg = new HashMap<>();
        for (WarehouseReceipt r : receipts) {
            SupplierKey sup = readSupplierKey(r); // lấy (id,name) từ WR/PO nếu có
            if (sup == null) continue;

            BigDecimal amount = readReceiptTotalAmount(r); // WR.totalAmount | PO.totalAmount | sum(items)
            String key = (sup.id != null ? sup.id : -1L) + "|" + (sup.name != null ? sup.name : "");

            TopSupplierDTO cur = agg.get(key);
            if (cur == null) {
                cur = TopSupplierDTO.builder()
                        .supplierId(sup.id)
                        .supplierName(sup.name)
                        .totalAmount(amount != null ? amount : BigDecimal.ZERO)
                        .build();
            } else {
                cur.setTotalAmount(cur.getTotalAmount().add(amount != null ? amount : BigDecimal.ZERO));
            }
            agg.put(key, cur);
        }

        int n = limit > 0 ? limit : 3;
        return agg.values().stream()
                .sorted(Comparator.comparing(TopSupplierDTO::getTotalAmount, Comparator.nullsLast(BigDecimal::compareTo)).reversed())
                .limit(n)
                .toList();
    }

    @Override
    public List<TopSalespersonDTO> getTopSalespeople(LocalDate from, LocalDate to, int limit) {
        LocalDate f = (from != null) ? from : LocalDate.now().minusDays(29);
        LocalDate t = (to   != null) ? to   : LocalDate.now();
        var rows = saleOrderRepo.topSalespeople(f.atStartOfDay(), t.atTime(LocalTime.MAX));
        int n = limit > 0 ? limit : 3;
        return rows.stream().limit(n).map(a ->
                TopSalespersonDTO.builder()
                        .userId(a[0] == null ? null : ((Number)a[0]).longValue())
                        .userName((String) a[1])
                        .totalSales((BigDecimal) a[2])
                        .orderCount(((Number) a[3]).longValue())
                        .build()
        ).toList();
    }

    @Override
    public List<TopProductDTO> getTopIssued(LocalDate from, LocalDate to, int limit) {
        LocalDate f = (from != null) ? from : LocalDate.now().minusDays(29);
        LocalDate t = (to   != null) ? to   : LocalDate.now();

        List<GoodIssueNote> notes = issueRepo.findByIssueDateBetweenWithDetails(f.atStartOfDay(), t.atTime(LocalTime.MAX));

        Map<Integer, TopProductDTO> agg = new HashMap<>();
        for (GoodIssueNote n : notes) {
            safeList(n.getDetails()).forEach(d -> {
                if (d.getProduct() == null) return;
                Integer pid = d.getProduct().getId();
                String  pname = d.getProduct().getName();
                long    qty = d.getQuantity() != null ? d.getQuantity() : 0L;

                agg.compute(pid, (k, cur) -> {
                    if (cur == null) {
                        return TopProductDTO.builder()
                                .productId(pid)
                                .productName(pname)
                                .totalQuantity(qty)
                                .build();
                    } else {
                        cur.setTotalQuantity(cur.getTotalQuantity() + qty);
                        return cur;
                    }
                });
            });
        }

        int n = (limit > 0) ? limit : 3;
        return agg.values().stream()
                .sorted(Comparator.comparingLong(TopProductDTO::getTotalQuantity).reversed())
                .limit(n)
                .toList();
    }

    @Override
    public List<TopComboDTO> getTopCombos(LocalDate from, LocalDate to, int limit) {
        LocalDate f = (from != null) ? from : LocalDate.now().minusDays(29);
        LocalDate t = (to   != null) ? to   : LocalDate.now();
        var rows = saleOrderComboRepo.topCombos(f.atStartOfDay(), t.atTime(LocalTime.MAX));
        int n = limit > 0 ? limit : 3;
        return rows.stream().limit(n).map(a ->
                TopComboDTO.builder()
                        .comboId(a[0] == null ? null : ((Number) a[0]).longValue())
                        .comboName((String) a[1])
                        .totalQuantity(((Number) a[2]).longValue())
                        .build()
        ).toList();
    }

    @Override
    public List<TopCustomerDTO> getTopCustomers(LocalDate from, LocalDate to, int limit) {
        LocalDate f = (from != null) ? from : LocalDate.now().minusDays(29);
        LocalDate t = (to   != null) ? to   : LocalDate.now();
        var rows = saleOrderRepo.topCustomers(f.atStartOfDay(), t.atTime(LocalTime.MAX));
        int n = limit > 0 ? limit : 3;
        return rows.stream().limit(n).map(a ->
                TopCustomerDTO.builder()
                        .customerId(a[0] == null ? null : ((Number) a[0]).longValue())
                        .customerName((String) a[1])
                        .totalSales((BigDecimal) a[2])
                        .orderCount(((Number) a[3]).longValue())
                        .build()
        ).toList();
    }

    // ====================== LOW STOCK & RECENT ======================
    @Override
    public List<Product> getLowStockTop(int limit) {
        int n = limit > 0 ? limit : LOW_STOCK_DEFAULT_LIMIT;
        return productRepo.findTop10ByOrderByQuantityAsc().stream().limit(n).toList();
    }

    @Override
    public List<RecentActivityDTO> getRecentActivities(int limitEach) {
        final int n = limitEach > 0 ? limitEach : 5;

        // 1) Lấy PXK (ISSUE) gần nhất
        var issues = issueRepo.findRecentWithCustomer(PageRequest.of(0, n));
        List<RecentActivityDTO> out = new ArrayList<>(n * 2);
        issues.forEach(g -> out.add(RecentActivityDTO.builder()
                .type(RecentActivityDTO.Type.ISSUE)
                .code(g.getGinCode())
                .dateTime(g.getIssueDate())
                .partnerName(g.getCustomer() != null ? g.getCustomer().getFullName() : null)
                .amount(g.getTotalAmount() != null ? g.getTotalAmount() : BigDecimal.ZERO)
                .build()));

        // 2) Lấy PNK (RECEIPT) gần nhất
        receiptRepo.findAll().stream()
                .filter(r -> r.getCreatedAt() != null)
                .sorted(Comparator.comparing(WarehouseReceipt::getCreatedAt).reversed())
                .limit(n)
                .forEach(r -> {
                    final String supplierName = Optional.ofNullable(readSupplierKey(r))
                            .map(sk -> sk.name)
                            .orElse(null);
                    final BigDecimal amount = Optional.ofNullable(readReceiptTotalAmount(r))
                            .orElse(BigDecimal.ZERO);

                    out.add(RecentActivityDTO.builder()
                            .type(RecentActivityDTO.Type.RECEIPT)
                            .code(readReceiptCode(r))
                            .dateTime(r.getCreatedAt())
                            .partnerName(supplierName)
                            .amount(amount)
                            .build());
                });

        // 3) Gộp + sort giảm dần + cắt còn n dòng TỔNG (không gán lại 'out')
        List<RecentActivityDTO> result = out.stream()
                .sorted(Comparator.comparing(RecentActivityDTO::getDateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(n)
                .collect(java.util.stream.Collectors.toList());

        return result;
    }

    // ====================== Helpers ======================

    private String readReceiptCode(WarehouseReceipt r) {
        Object v = tryCall(r, "getWrCode");
        if (!(v instanceof String s) || s.isBlank()) v = tryCall(r, "getReceiptCode");
        if (!(v instanceof String s2) || s2.isBlank()) v = tryCall(r, "getCode");
        if (v instanceof String ok && !ok.isBlank()) return ok;
        Object id = tryCall(r, "getId");
        return id != null ? ("WR-" + id) : "WR-?";
    }

    private static <T> List<T> safeList(List<T> l) {
        return l != null ? l : Collections.emptyList();
    }

    private long readItemQuantitySafe(WarehouseReceiptItem it) {
        // ưu tiên getQuantity(), fallback getActualQuantity()
        try {
            Object v = WarehouseReceiptItem.class.getMethod("getQuantity").invoke(it);
            if (v instanceof Integer i) return i;
            if (v instanceof Long l)    return l;
        } catch (Exception ignore) { }
        try {
            Object v = WarehouseReceiptItem.class.getMethod("getActualQuantity").invoke(it);
            if (v instanceof Integer i) return i;
            if (v instanceof Long l)    return l;
        } catch (Exception ignore) { }
        return 0L;
    }

    /**
     * Tổng tiền phiếu nhập:
     * 1) WarehouseReceipt.getTotalAmount() nếu có
     * 2) WarehouseReceipt.getPurchaseOrder().getTotalAmount() nếu có
     * 3) Sum item: quantity * (price|unitPrice|purchasePrice) nếu có
     * 4) Không có gì -> 0
     */
    private BigDecimal readReceiptTotalAmount(WarehouseReceipt r) {
        // 1) WR.totalAmount
        BigDecimal v = (BigDecimal) tryCall(r, "getTotalAmount");
        if (v != null) return v;

        // 2) PO.totalAmount
        Object po = tryCall(r, "getPurchaseOrder");
        if (po != null) {
            BigDecimal poAmt = (BigDecimal) tryCall(po, "getTotalAmount");
            if (poAmt != null) return poAmt;
        }

        // 3) Sum items (qty * unit price nếu có)
        BigDecimal sum = BigDecimal.ZERO;
        for (WarehouseReceiptItem it : receiptItemRepo.findByWarehouseReceipt(r)) {
            long qty = readItemQuantitySafe(it);
            BigDecimal price = readItemPriceSafe(it); // thử price/unitPrice/purchasePrice
            if (price != null) {
                sum = sum.add(price.multiply(BigDecimal.valueOf(qty)));
            }
        }
        return sum;
    }

    private BigDecimal readItemPriceSafe(WarehouseReceiptItem it) {
        for (String m : new String[]{"getPrice", "getUnitPrice", "getPurchasePrice"}) {
            Object v = tryCall(it, m);
            if (v instanceof BigDecimal bd) return bd;
            if (v instanceof Number num)    return BigDecimal.valueOf(num.doubleValue());
        }
        return null;
    }

    private record SupplierKey(Long id, String name) {}
    private SupplierKey readSupplierKey(WarehouseReceipt r) {
        Object sup = tryCall(r, "getSupplier");
        if (sup == null) {
            Object po = tryCall(r, "getPurchaseOrder");
            if (po != null) sup = tryCall(po, "getSupplier");
        }
        if (sup == null) return null;

        Long id = null;
        Object vid = tryCall(sup, "getId");
        if (vid instanceof Number n) id = n.longValue();

        String name = null;
        Object vname = tryCall(sup, "getName");
        if (!(vname instanceof String)) vname = tryCall(sup, "getFullName");
        if (vname instanceof String s) name = s;

        if (id == null && name == null) return null;
        return new SupplierKey(id, name);
    }

    private Object tryCall(Object target, String method) {
        if (target == null) return null;
        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Exception ignore) {
            return null;
        }
    }
}
