package com.eewms.repository;

import com.eewms.entities.SaleOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SaleOrderRepository extends JpaRepository<SaleOrder, Integer> {

    boolean existsBySoCode(String soCode);

    @Query("SELECT so FROM SaleOrder so WHERE so.status = :status")
    List<SaleOrder> findByStatus(@Param("status") SaleOrder.SaleOrderStatus status);

    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(so_code,4) AS UNSIGNED)), 0) FROM sale_orders", nativeQuery = true)
    Long findMaxSoCodeNumber();

    // Trang LIST khi có từ khoá -> cần fetch customer/createdByUser để mapper đọc phone & creator
    @EntityGraph(attributePaths = {"customer", "createdByUser"})
    @Query("SELECT o FROM SaleOrder o WHERE LOWER(o.soCode) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<SaleOrder> searchByKeyword(@Param("keyword") String keyword);

    // ==== NEW: lấy SO theo khách hàng & status (gom thiếu cho PR theo KH) ====
    @Query("""
           select so from SaleOrder so
           where so.customer.id = :customerId
             and so.status in (:statuses)
           """)
    List<SaleOrder> findByCustomerIdAndStatusIn(@Param("customerId") Long customerId,
                                                @Param("statuses") List<SaleOrder.SaleOrderStatus> statuses);

    // ==== Các báo cáo cũ giữ nguyên ====
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

    Optional<SaleOrder> findByPayOsOrderCode(String payOsOrderCode);

    // Trang LIST mặc định (không có keyword)
    @EntityGraph(attributePaths = {"customer", "createdByUser"})
    Page<SaleOrder> findAllByOrderBySoIdDesc(Pageable pageable);

    // Trang CHI TIẾT: fetch-join toàn bộ cần thiết
    @Query("""
        select distinct so
        from SaleOrder so
        left join fetch so.customer c
        left join fetch so.createdByUser u
        left join fetch so.details d
        left join fetch d.product p
        left join fetch d.combo cb
        where so.soId = :id
    """)
    Optional<SaleOrder> findByIdWithDetails(@Param("id") Integer id);

    List<SaleOrder> findByStatusIn(Collection<SaleOrder.SaleOrderStatus> statuses);

    @Query("""
      select distinct so from SaleOrder so
      left join fetch so.details d
      where so.status in (com.eewms.entities.SaleOrder.SaleOrderStatus.PENDING,
                          com.eewms.entities.SaleOrder.SaleOrderStatus.PARTLY_DELIVERED)
        and (:start is null or so.orderDate >= :start)
        and (:end   is null or so.orderDate <= :end)
    """)
    List<SaleOrder> findOpenOrdersInRange(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    @EntityGraph(attributePaths = { "createdByUser","customer" })
    @Query("""
    SELECT so FROM SaleOrder so
    LEFT JOIN so.customer c
    WHERE (:keyword IS NULL OR
           LOWER(so.soCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
           (c IS NOT NULL AND (
               LOWER(c.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))
           )))
      AND (:status IS NULL OR so.status = :status)
      AND (:from IS NULL OR so.orderDate >= :from)
      AND (:to   IS NULL OR so.orderDate <= :to)
    ORDER BY so.soCode ASC
""")
    Page<SaleOrder> searchWithFilters(
            @Param("keyword") String keyword,
            @Param("status") com.eewms.entities.SaleOrder.SaleOrderStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
