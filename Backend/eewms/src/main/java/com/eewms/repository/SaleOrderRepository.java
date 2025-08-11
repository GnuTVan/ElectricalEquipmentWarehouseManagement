package com.eewms.repository;

import com.eewms.entities.SaleOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleOrderRepository extends JpaRepository<SaleOrder, Integer> {
    boolean existsBySoCode(String soCode);
    @Query("SELECT so FROM SaleOrder so WHERE so.status = :status")
    List<SaleOrder> findByStatus(@Param("status") SaleOrder.SaleOrderStatus status);
    @Query("SELECT o FROM SaleOrder o WHERE LOWER(o.soCode) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<SaleOrder> searchByKeyword(@Param("keyword") String keyword);
}