package com.eewms.services;

import com.eewms.dto.dashboard.*;
import com.eewms.entities.Product;

import java.time.LocalDate;
import java.util.List;

public interface IDashboardService {
    DashboardSummaryDTO getSummary(LocalDate from, LocalDate to);
    List<DailyFlowDTO> getDailyFlow(LocalDate from, LocalDate to);

    List<TopSupplierDTO> getTopSuppliers(LocalDate from, LocalDate to, int limit);
    List<TopSalespersonDTO> getTopSalespeople(LocalDate from, LocalDate to, int limit);
    List<TopProductDTO> getTopIssued(LocalDate from, LocalDate to, int limit);
    List<TopComboDTO> getTopCombos(LocalDate from, LocalDate to, int limit);
    List<TopCustomerDTO> getTopCustomers(LocalDate from, LocalDate to, int limit);

    List<Product> getLowStockTop(int limit);
    List<RecentActivityDTO> getRecentActivities(int limitEach);
}
