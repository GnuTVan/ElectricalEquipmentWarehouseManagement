package com.eewms.services;

import com.eewms.dto.report.ReceiptReportFilter;
import com.eewms.dto.report.ReceiptReportRowDTO;
import com.eewms.dto.report.ReceiptTotalsDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IReceiptReportService {
    Page<ReceiptReportRowDTO> findReceiptHeaders(ReceiptReportFilter f, Pageable pageable);
    ReceiptTotalsDTO totalsForFilter(ReceiptReportFilter f);
}