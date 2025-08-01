package com.eewms.controller;

import com.eewms.dto.CustomerDTO;
import com.eewms.dto.GoodIssueDetailDTO;
import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.User;
import com.eewms.services.ICustomerService;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.IUserService;
import com.eewms.utils.ExcelExporterUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/admin/reports/good-issue")
@RequiredArgsConstructor
public class GoodIssueReportController {

    private final IGoodIssueService goodIssueService;
    private final ICustomerService customerService;
    private final IUserService userService;

    @GetMapping
    public String showReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long userId,
            Model model
    ) {
        List<GoodIssueNoteDTO> result = goodIssueService.filterReport(fromDate, toDate, customerId, userId);
        List<CustomerDTO> customers = customerService.findAll();
        List<User> users = userService.findAllUsers();

        BigDecimal totalAmount = result.stream()
                .map(dto -> dto.getTotalAmount() != null ? dto.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        String formattedTotalAmount = nf.format(totalAmount) + " ₫";
        model.addAttribute("formattedTotalAmount", formattedTotalAmount);

        model.addAttribute("reportList", result);
        model.addAttribute("customers", customers);
        model.addAttribute("users", users);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("selectedCustomerId", customerId);
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("totalAmount", totalAmount);
        return "good-issue-report";
    }

    @GetMapping("/export")
    public void exportGoodIssueReportExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long userId,
            HttpServletResponse response
    ) throws Exception {
        List<GoodIssueNoteDTO> reports = goodIssueService.filterReport(fromDate, toDate, customerId, userId);

        // 1. Header của file Excel
        List<String> headers = List.of("Mã phiếu", "Ngày xuất", "Khách hàng", "Người lập", "Ghi chú", "Tổng tiền");

        // 2. Dữ liệu từng dòng
        List<List<String>> rows = reports.stream().map(r -> List.of(
                r.getCode(),
                r.getIssueDate().toLocalDate().toString(),
                r.getCustomerName(),
                r.getCreatedBy(),
                r.getDescription(),
                r.getTotalAmount().toPlainString()
        )).toList();

        // 3. Kiểu dữ liệu của từng cột
        List<String> columnTypes = List.of("text", "text", "text", "text", "text", "currency");

        // 4. Export
        InputStream excel = ExcelExporterUtil.exportReportWithInfo(
                "BÁO CÁO XUẤT KHO",
                "BaoCaoXuatKho",
                Map.of(), // Nếu cần, truyền thêm Map.of("Từ ngày", fromDate.toString(), "Đến ngày", toDate.toString())
                headers,
                rows,
                columnTypes
        );

        // 5. Response
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=bao-cao-xuat-kho.xlsx");
        excel.transferTo(response.getOutputStream());
    }

    @GetMapping("/{id}")
    public String viewGoodIssueDetail(@PathVariable Long id, Model model) {
        GoodIssueNoteDTO dto = goodIssueService.getById(id); // Phải chứa đầy đủ thông tin chi tiết
        model.addAttribute("note", dto);                     // Để dùng ${note.details} trong view
        return "good-issue-report-detail";                   // Trả đúng view
    }

    @GetMapping("/export/{id}")
    public void exportGoodIssueDetail(@PathVariable Long id, HttpServletResponse response) throws Exception {
        GoodIssueNoteDTO dto = goodIssueService.getById(id);

        List<String> headers = List.of("STT", "Sản phẩm", "Số lượng", "Đơn giá", "Thành tiền");
        List<List<String>> rows = new ArrayList<>();

        int index = 1;
        for (GoodIssueDetailDTO detail : dto.getDetails()) {
            rows.add(List.of(
                    String.valueOf(index++),
                    detail.getProductName(),
                    String.valueOf(detail.getQuantity()),
                    detail.getPrice().toPlainString(),
                    detail.getTotal().toPlainString()
            ));
        }

        List<String> columnTypes = List.of("number", "text", "number", "currency", "currency");

        Map<String, String> infoBlock = Map.of(
                "Mã phiếu", dto.getCode(),
                "Ngày xuất", dto.getIssueDate().toLocalDate().toString(),
                "Khách hàng", dto.getCustomerName(),
                "Người lập", dto.getCreatedBy(),
                "Ghi chú", dto.getDescription()
        );

        InputStream excel = ExcelExporterUtil.exportReportWithInfo(
                "CHI TIẾT PHIẾU XUẤT KHO",
                "ChiTietPhieuXuatKho",
                infoBlock,
                headers,
                rows,
                columnTypes
        );

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=chi-tiet-phieu-xuat-kho_" + dto.getCode() + ".xlsx");
        excel.transferTo(response.getOutputStream());
    }

}
