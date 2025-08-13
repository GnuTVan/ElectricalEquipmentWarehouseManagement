package com.eewms.repository;

import com.eewms.entities.SaleOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SaleOrderDetailRepository extends JpaRepository<SaleOrderDetail, Integer> {
    // Lấy tất cả chi tiết theo soId của đơn
    @Query("select d from SaleOrderDetail d where d.sale_order.soId = :soId")
    List<SaleOrderDetail> findBySaleOrderId(@Param("soId") Integer soId);

    // Xóa toàn bộ chi tiết theo soId của đơn (dùng trong replace-all)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SaleOrderDetail d where d.sale_order.soId = :soId")
    int deleteByOrderSoId(@Param("soId") Integer soId);
}