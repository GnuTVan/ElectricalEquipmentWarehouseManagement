package com.eewms.services;

import com.eewms.dto.report.IssueReportFilter;
import com.eewms.dto.report.IssueReportRowDTO;
import com.eewms.dto.report.IssueTotalsDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface IIssueReportService {
    Page<IssueReportRowDTO> findIssueHeaders(IssueReportFilter f, Pageable pageable);
    IssueTotalsDTO totalsForFilter(IssueReportFilter f);
    List<IssueReportRowDTO> findAllForExport(IssueReportFilter f);
}
