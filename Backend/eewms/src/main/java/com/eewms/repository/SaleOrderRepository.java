package com.eewms.repository;

import com.eewms.entities.SaleOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SaleOrderRepository extends JpaRepository<SaleOrder, Integer> {
    boolean existsBySoCode(String soCode);
    @Query("SELECT so FROM SaleOrder so WHERE so.status = :status")
    List<SaleOrder> findByStatus(@Param("status") SaleOrder.SaleOrderStatus status);
    @Query("SELECT o FROM SaleOrder o WHERE LOWER(o.soCode) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<SaleOrder> searchByKeyword(@Param("keyword") String keyword);

    //top sale order
    @Query("""
      select o.createdByUser.id, o.createdByUser.fullName,
             coalesce(sum(o.totalAmount),0), count(o)
      from SaleOrder o
      where o.orderDate between :from and :to
      group by o.createdByUser.id, o.createdByUser.fullName
      order by coalesce(sum(o.totalAmount),0) desc
    """)
    List<Object[]> topSalespeople(LocalDateTime from, LocalDateTime to);

    @Query("""
      select o.customer.id, o.customer.fullName,
             coalesce(sum(o.totalAmount),0), count(o)
      from SaleOrder o
      where o.orderDate between :from and :to
      group by o.customer.id, o.customer.fullName
      order by coalesce(sum(o.totalAmount),0) desc
    """)
    List<Object[]> topCustomers(LocalDateTime from, LocalDateTime to);

    @Query("""
      select count(o) from SaleOrder o
      where o.status in (com.eewms.entities.SaleOrder$SaleOrderStatus.PENDING,
                         com.eewms.entities.SaleOrder$SaleOrderStatus.PROCESSING)
    """)
    long countPending();
}