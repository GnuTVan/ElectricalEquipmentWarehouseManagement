package com.eewms.controller;

import com.eewms.dto.report.ReceiptReportFilter;
import com.eewms.dto.report.ReceiptReportRowDTO;
import com.eewms.dto.report.ReceiptTotalsDTO;
import com.eewms.repository.SupplierRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.services.IReceiptReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin/reports/receipts")
@RequiredArgsConstructor
public class ReceiptReportController {

    private final IReceiptReportService reportService;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;

    @GetMapping
    public String view(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) Long productId,
            // userId tạm thời không dùng vì Receipt hiện chỉ có createdBy (String)
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String receiptCode,
            @RequestParam(required = false) String poCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            Model model
    ) {
        // gom filter
        ReceiptReportFilter f = new ReceiptReportFilter(
                fromDate, toDate, warehouseId, supplierId, productId, userId, receiptCode, poCode
        );

        // data & totals
        Page<ReceiptReportRowDTO> data = reportService.findReceiptHeaders(f, PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
        ReceiptTotalsDTO totals = reportService.totalsForFilter(f);

        // model cho view
        model.addAttribute("data", data);
        model.addAttribute("totals", totals);
        model.addAttribute("suppliers", supplierRepository.findAll());
        model.addAttribute("warehouses", warehouseRepository.findAll());

        // giữ lại giá trị filter để bind lại vào form
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("warehouseId", warehouseId);
        model.addAttribute("supplierId", supplierId);
        model.addAttribute("productId", productId);
        model.addAttribute("userId", userId);
        model.addAttribute("receiptCode", receiptCode);
        model.addAttribute("poCode", poCode);

        return "receipt-report/list";
    }
}
