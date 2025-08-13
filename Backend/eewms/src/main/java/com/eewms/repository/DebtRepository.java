package com.eewms.repository;

import com.eewms.entities.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {

    // Dùng ở /view/{id} hoặc khi cần lấy record công nợ theo phiếu nhập
    Optional<Debt> findByWarehouseReceiptId(Long warehouseReceiptId);

    // Dùng ở trang list để biết phiếu nhập đã có công nợ chưa (controller của bạn đang gọi)
    boolean existsByWarehouseReceiptId(Long warehouseReceiptId);
}
