package com.eewms.controller;

import com.eewms.dto.report.WarehouseReceiptReportDTO;
import com.eewms.entities.Supplier;
import com.eewms.entities.Warehouse;
import com.eewms.services.IWarehouseReceiptService;
import com.eewms.repository.SupplierRepository;
import com.eewms.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class StatisticReceiptController {

    private final IWarehouseReceiptService warehouseReceiptService;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;

    @GetMapping("/admin/reports/warehouse-receipt")
    public String showReportPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long supplierId,
            Model model
    ) {
        // Load danh sách kho & NCC
        List<Warehouse> warehouses = warehouseRepository.findAll();
        List<Supplier> suppliers = supplierRepository.findAll();

        // Lấy dữ liệu báo cáo
        List<WarehouseReceiptReportDTO> reportList = warehouseReceiptService.getReceiptReport(fromDate, toDate, warehouseId, supplierId);

        // Truyền vào view
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("reportList", reportList);

        return "report-warehouse-receipt";
    }


}