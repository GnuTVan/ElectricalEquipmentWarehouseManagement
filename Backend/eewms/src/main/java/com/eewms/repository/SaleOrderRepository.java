package com.eewms.repository;

import com.eewms.entities.SaleOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    //findByPayOsOrderCode: để webhook controller gọi khi muốn tìm đơn hàng khớp với orderCode PayOS gửi về.
    Optional<SaleOrder> findByPayOsOrderCode(String payOsOrderCode);

    //LIST: nạp sẵn customer & createdByUser, KHÔNG nạp details( Dùng cho View List
    @EntityGraph(attributePaths = {"customer", "createdByUser"})
    Page<SaleOrder> findAllByOrderBySoIdDesc(Pageable pageable);

            //VIEW: fetch-join details + product (+ customer, createdByUser) dùng cho màn chi tiết
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
}