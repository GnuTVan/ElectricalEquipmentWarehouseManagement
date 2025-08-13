package com.eewms.controller;

import com.eewms.services.IDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IDashboardService dashboardService;

    @GetMapping
    public String view(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                       Model model) {

        if (toDate == null) toDate = LocalDate.now();
        if (fromDate == null) fromDate = toDate.minusDays(29);

        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);

        model.addAttribute("summary",      dashboardService.getSummary(fromDate, toDate));
        model.addAttribute("flows",        dashboardService.getDailyFlow(fromDate, toDate));
        model.addAttribute("lowStock",     dashboardService.getLowStockTop(10));
        model.addAttribute("recent",       dashboardService.getRecentActivities(5));

        model.addAttribute("topSuppliers",   dashboardService.getTopSuppliers(fromDate, toDate, 3));
        model.addAttribute("topSalespeople", dashboardService.getTopSalespeople(fromDate, toDate, 3));
        model.addAttribute("topIssued",      dashboardService.getTopIssued(fromDate, toDate, 3));
        model.addAttribute("topCombos",      dashboardService.getTopCombos(fromDate, toDate, 3));
        model.addAttribute("topCustomers",   dashboardService.getTopCustomers(fromDate, toDate, 3));

        return "dashboard";
    }
}
