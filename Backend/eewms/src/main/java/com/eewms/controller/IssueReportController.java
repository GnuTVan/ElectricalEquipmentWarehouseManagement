package com.eewms.controller;

import com.eewms.dto.report.IssueReportFilter;
import com.eewms.dto.report.IssueReportRowDTO;
import com.eewms.dto.report.IssueTotalsDTO;
import com.eewms.repository.CustomerRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IIssueReportService;
import com.eewms.utils.IssueReportExcelExporter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/admin/reports/issues")
@RequiredArgsConstructor
public class IssueReportController {

    private final IIssueReportService reportService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    @GetMapping
    public String view(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String issueCode,
            @RequestParam(required = false) String saleOrderCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            Model model
    ) {
        IssueReportFilter f = new IssueReportFilter(fromDate, toDate, customerId, userId, issueCode, saleOrderCode);

        Page<IssueReportRowDTO> data = reportService.findIssueHeaders(f, PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
        IssueTotalsDTO totals = reportService.totalsForFilter(f);

        model.addAttribute("data", data);
        model.addAttribute("totals", totals);
        model.addAttribute("customers", customerRepository.findAll());
        model.addAttribute("users", userRepository.findAll());

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("customerId", customerId);
        model.addAttribute("userId", userId);
        model.addAttribute("issueCode", issueCode);
        model.addAttribute("saleOrderCode", saleOrderCode);
        return "issue-report/list";
    }

    @GetMapping("/export/xlsx")
    public void exportXlsx(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String issueCode,
            @RequestParam(required = false) String saleOrderCode,
            HttpServletResponse response
    ) {
        IssueReportFilter f = new IssueReportFilter(fromDate, toDate, customerId, userId, issueCode, saleOrderCode);
        List<IssueReportRowDTO> rows = reportService.findAllForExport(f);
        IssueReportExcelExporter.export(rows, fromDate, toDate, response);
    }
}
