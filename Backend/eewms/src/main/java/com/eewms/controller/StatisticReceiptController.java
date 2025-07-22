package com.eewms.controller;

import com.eewms.dto.report.WarehouseReceiptReportDTO;
import com.eewms.entities.Supplier;
import com.eewms.entities.Warehouse;
import com.eewms.entities.WarehouseReceipt;
import com.eewms.entities.WarehouseReceiptItem;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.repository.warehouseReceipt.WarehouseReceiptRepository;
import com.eewms.services.IWarehouseReceiptService;
import com.eewms.repository.SupplierRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.utils.ExcelExporterUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class StatisticReceiptController {

    private final IWarehouseReceiptService warehouseReceiptService;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseReceiptRepository warehouseReceiptRepository;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;

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

    @GetMapping("/admin/reports/warehouse-receipt/export")
    public void exportWarehouseReportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long supplierId,
            HttpServletResponse response
    ) throws Exception {
        List<WarehouseReceiptReportDTO> reports = warehouseReceiptService.getReceiptReport(fromDate, toDate, warehouseId, supplierId);

        List<String> headers = List.of("Mã phiếu", "Ngày tạo", "Kho", "Nhà cung cấp", "Tổng SL", "Tổng tiền");
        List<List<String>> rows = reports.stream().map(r ->
                List.of(
                        r.getReceiptCode(),
                        r.getCreatedAt().toLocalDate().toString(),
                        r.getWarehouseName(),
                        r.getSupplierName(),
                        String.valueOf(r.getTotalQuantity()),
                        r.getTotalAmount().toPlainString()
                )
        ).toList();

        InputStream excel = ExcelExporterUtil.exportWarehouseReceiptReport(headers, rows, "BÁO CÁO NHẬP KHO");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=bao-cao-nhap-kho.xlsx");
        excel.transferTo(response.getOutputStream());
    }

    @GetMapping("/admin/reports/warehouse-receipt/{code}")
    public String viewReceiptDetail(@PathVariable String code, Model model) {
        WarehouseReceipt receipt = warehouseReceiptRepository.findByCode(code)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu nhập"));
        List<WarehouseReceiptItem> items = warehouseReceiptItemRepository.findByWarehouseReceipt(receipt);

        model.addAttribute("receipt", receipt);
        model.addAttribute("items", items);
        return "report-warehouse-receipt-detail";
    }

}