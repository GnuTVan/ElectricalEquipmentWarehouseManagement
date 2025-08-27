package com.eewms.services.impl;

import com.eewms.dto.report.IssueReportFilter;
import com.eewms.dto.report.IssueReportRowDTO;
import com.eewms.dto.report.IssueTotalsDTO;
import com.eewms.entities.GoodIssueDetail;
import com.eewms.entities.GoodIssueNote;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.services.IIssueReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class IssueReportServiceImpl implements IIssueReportService {

    private final GoodIssueNoteRepository noteRepo;

    @Transactional(readOnly = true)
    @Override
    public Page<IssueReportRowDTO> findIssueHeaders(IssueReportFilter f, Pageable pageable) {
        // ★ CHANGED: fetch đủ saleOrder, customer, details, product để tránh lazy
        List<GoodIssueNote> notes = noteRepo.findAllWithDetails(); // ★ CHANGED

        List<GoodIssueNote> filtered = notes.stream()
                .filter(n -> testDate(asLocalDate(n.getIssueDate()), f.getFromDate(), f.getToDate()))
                .filter(n -> f.getCustomerId() == null ||
                        (n.getCustomer() != null && Objects.equals(n.getCustomer().getId(), f.getCustomerId())))
                .filter(n -> f.getUserId() == null ||
                        (n.getCreatedBy() != null && Objects.equals(n.getCreatedBy().getId(), f.getUserId())))
                .filter(n -> f.getIssueCode() == null ||
                        (n.getGinCode() != null && n.getGinCode().toLowerCase().contains(f.getIssueCode().toLowerCase())))
                .filter(n -> f.getSaleOrderCode() == null ||
                        (n.getSaleOrder() != null && n.getSaleOrder().getSoCode() != null &&
                                n.getSaleOrder().getSoCode().toLowerCase().contains(f.getSaleOrderCode().toLowerCase())))
                .toList();

        List<IssueReportRowDTO> rows = filtered.stream()
                .map(n -> {
                    int totalQty = safeList(n.getDetails()).stream()
                            .mapToInt(d -> d.getQuantity() != null ? d.getQuantity() : 0)
                            .sum();
                    BigDecimal totalAmt = n.getTotalAmount() != null ? n.getTotalAmount() : BigDecimal.ZERO;
                    int comboCount = 0;
                    if (n.getSaleOrder() != null && n.getSaleOrder().getCombos() != null) {
                        comboCount = n.getSaleOrder().getCombos().stream()
                                .mapToInt(c -> c.getQuantity() != null ? c.getQuantity() : 1)
                                .sum();
                    }

                    return new IssueReportRowDTO(
                            n.getGinId(),
                            n.getGinCode(),
                            asLocalDate(n.getIssueDate()),
                            n.getCustomer() != null ? n.getCustomer().getId() : null,
                            n.getCustomer() != null ? n.getCustomer().getFullName() : null,
                            n.getCreatedBy() != null ? n.getCreatedBy().getId() : null,
                            n.getCreatedBy() != null ? n.getCreatedBy().getUsername() : null,
                            n.getSaleOrder() != null ? n.getSaleOrder().getSoCode() : null,
                            totalQty,
                            totalAmt,
                            comboCount
                    );
                })
                .sorted(Comparator
                        .comparing(IssueReportRowDTO::getIssueDate, Comparator.nullsLast(LocalDate::compareTo)).reversed()
                        .thenComparing(r -> r.getIssueCode() != null ? r.getIssueCode() : ""))
                .toList();

        int start = Math.min(pageable.getPageNumber() * pageable.getPageSize(), rows.size());
        int end = Math.min(start + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(start, end), pageable, rows.size());
    }

    @Transactional(readOnly = true)
    @Override
    public IssueTotalsDTO totalsForFilter(IssueReportFilter f) {
        // ★ CHANGED: fetch đủ để tránh lazy khi tính tổng
        List<GoodIssueNote> notes = noteRepo.findAllWithDetails(); // ★ CHANGED
        int totalCombos = 0;

        List<GoodIssueNote> filtered = notes.stream()
                .filter(n -> testDate(asLocalDate(n.getIssueDate()), f.getFromDate(), f.getToDate()))
                .filter(n -> f.getCustomerId() == null ||
                        (n.getCustomer() != null && Objects.equals(n.getCustomer().getId(), f.getCustomerId())))
                .filter(n -> f.getUserId() == null ||
                        (n.getCreatedBy() != null && Objects.equals(n.getCreatedBy().getId(), f.getUserId())))
                .toList();

        int totalQty = 0;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (GoodIssueNote n : filtered) {
            int qty = safeList(n.getDetails()).stream()
                    .mapToInt(d -> d.getQuantity() != null ? d.getQuantity() : 0)
                    .sum();
            totalQty += qty;
            totalAmt = totalAmt.add(n.getTotalAmount() != null ? n.getTotalAmount() : BigDecimal.ZERO);
            int comboCount = 0;
            if (n.getSaleOrder() != null && n.getSaleOrder().getCombos() != null) {
                comboCount = n.getSaleOrder().getCombos().stream()
                        .mapToInt(c -> c.getQuantity() != null ? c.getQuantity() : 1)
                        .sum();
            }
            totalCombos += comboCount;
        }
        return new IssueTotalsDTO(filtered.size(), totalQty, totalAmt, totalCombos);
    }

    // ★ CHANGED: mở transaction ngay entrypoint export (tránh self-invocation bỏ qua @Transactional)
    @Transactional(readOnly = true) // ★ CHANGED
    @Override
    public List<IssueReportRowDTO> findAllForExport(IssueReportFilter f) {
        return findIssueHeaders(f, org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE)).getContent();
    }

    private static LocalDate asLocalDate(java.time.LocalDateTime dt) {
        return dt != null ? dt.toLocalDate() : null;
    }
    private static boolean testDate(LocalDate d, LocalDate from, LocalDate to) {
        if (d == null) return false;
        if (from != null && d.isBefore(from)) return false;
        if (to != null && d.isAfter(to)) return false;
        return true;
    }
    private static <T> List<T> safeList(List<T> l){ return l != null ? l : java.util.Collections.emptyList(); }
}
