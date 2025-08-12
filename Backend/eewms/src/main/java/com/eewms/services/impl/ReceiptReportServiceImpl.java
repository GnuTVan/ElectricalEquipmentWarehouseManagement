package com.eewms.services.impl;

import com.eewms.dto.report.ReceiptReportFilter;
import com.eewms.dto.report.ReceiptReportRowDTO;
import com.eewms.dto.report.ReceiptTotalsDTO;
import com.eewms.entities.PurchaseOrder;
import com.eewms.entities.Supplier;
import com.eewms.entities.Warehouse;
import com.eewms.entities.WarehouseReceipt;
import com.eewms.entities.WarehouseReceiptItem;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IReceiptReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReceiptReportServiceImpl implements IReceiptReportService {

    private final WarehouseReceiptRepository receiptRepo;
    private final WarehouseReceiptItemRepository itemRepo;

    @Override
    public Page<ReceiptReportRowDTO> findReceiptHeaders(ReceiptReportFilter f, Pageable pageable) {
        List<WarehouseReceipt> receipts = receiptRepo.findAll();

        List<WarehouseReceipt> filtered = receipts.stream()
                .filter(r -> testDate(asLocalDate(r.getCreatedAt()), f.getFromDate(), f.getToDate()))
                .filter(r -> f.getWarehouseId() == null
                        || (r.getWarehouse() != null && Objects.equals(r.getWarehouse().getId(), f.getWarehouseId())))
                .filter(r -> f.getSupplierId() == null
                        || (getSupplier(r) != null && Objects.equals(getSupplier(r).getId(), f.getSupplierId())))
                // BỎ lọc userId vì entity chỉ có createdBy (String). Khi có User liên kết thì thêm lại.
                .filter(r -> f.getReceiptCode() == null
                        || (r.getCode() != null && r.getCode().toLowerCase().contains(f.getReceiptCode().toLowerCase())))
                .filter(r -> f.getPoCode() == null
                        || (r.getPurchaseOrder() != null && r.getPurchaseOrder().getCode() != null
                        && r.getPurchaseOrder().getCode().toLowerCase().contains(f.getPoCode().toLowerCase())))
                .filter(r -> {
                    if (f.getProductId() == null) return true;
                    List<WarehouseReceiptItem> items = itemRepo.findByWarehouseReceipt(r);
                    return items.stream().anyMatch(i ->
                            i.getProduct() != null
                                    && i.getProduct().getId() != null
                                    && Objects.equals(Long.valueOf(i.getProduct().getId()), f.getProductId())
                    );
                })
                .toList();

        List<ReceiptReportRowDTO> rows = filtered.stream()
                .map(r -> {
                    List<WarehouseReceiptItem> items = itemRepo.findByWarehouseReceipt(r);
                    int totalQty = items.stream()
                            .mapToInt(it -> it.getActualQuantity() != null ? it.getActualQuantity() : 0)
                            .sum();
                    BigDecimal totalAmt = items.stream()
                            .map(it -> {
                                BigDecimal price = it.getPrice() != null ? it.getPrice() : BigDecimal.ZERO;
                                int qty = it.getActualQuantity() != null ? it.getActualQuantity() : 0;
                                return price.multiply(BigDecimal.valueOf(qty));
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Warehouse w = r.getWarehouse();
                    Supplier s = getSupplier(r);
                    LocalDate receiptDate = asLocalDate(r.getCreatedAt());

                    return new ReceiptReportRowDTO(
                            r.getId(),
                            r.getCode(),
                            receiptDate,
                            w != null ? w.getId() : null,
                            w != null ? w.getName() : null,
                            s != null ? s.getId() : null,
                            s != null ? s.getName() : null,
                            r.getCreatedBy(), // entity là createdBy (String)
                            totalQty,
                            totalAmt
                    );
                })
                .sorted(Comparator.comparing(ReceiptReportRowDTO::getReceiptDate, Comparator.nullsLast(LocalDate::compareTo))
                        .thenComparing(rr -> rr.getReceiptCode() != null ? rr.getReceiptCode() : ""))
                .toList();

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int start = Math.min(page * size, rows.size());
        int end = Math.min(start + size, rows.size());
        return new PageImpl<>(rows.subList(start, end), pageable, rows.size());
    }

    @Override
    public ReceiptTotalsDTO totalsForFilter(ReceiptReportFilter f) {
        List<WarehouseReceipt> receipts = receiptRepo.findAll();

        List<WarehouseReceipt> filtered = receipts.stream()
                .filter(r -> testDate(asLocalDate(r.getCreatedAt()), f.getFromDate(), f.getToDate()))
                .filter(r -> f.getWarehouseId() == null
                        || (r.getWarehouse() != null && Objects.equals(r.getWarehouse().getId(), f.getWarehouseId())))
                .filter(r -> f.getSupplierId() == null
                        || (getSupplier(r) != null && Objects.equals(getSupplier(r).getId(), f.getSupplierId())))
                .toList();

        long count = filtered.size();

        int totalQty = 0;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (WarehouseReceipt r : filtered) {
            List<WarehouseReceiptItem> items = itemRepo.findByWarehouseReceipt(r);
            int qty = items.stream()
                    .mapToInt(it -> it.getActualQuantity() != null ? it.getActualQuantity() : 0)
                    .sum();
            BigDecimal amt = items.stream()
                    .map(it -> {
                        BigDecimal price = it.getPrice() != null ? it.getPrice() : BigDecimal.ZERO;
                        int q = it.getActualQuantity() != null ? it.getActualQuantity() : 0;
                        return price.multiply(BigDecimal.valueOf(q));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            totalQty += qty;
            totalAmt = totalAmt.add(amt);
        }
        return new ReceiptTotalsDTO(count, totalQty, totalAmt);
    }

    private static LocalDate asLocalDate(LocalDateTime dt) {
        return dt != null ? dt.toLocalDate() : null;
    }

    private static boolean testDate(LocalDate d, LocalDate from, LocalDate to) {
        if (d == null) return false;
        if (from != null && d.isBefore(from)) return false;
        if (to != null && d.isAfter(to)) return false;
        return true;
    }

    private static Supplier getSupplier(WarehouseReceipt r) {
        PurchaseOrder po = r.getPurchaseOrder();
        return po != null ? po.getSupplier() : null;
    }
}
