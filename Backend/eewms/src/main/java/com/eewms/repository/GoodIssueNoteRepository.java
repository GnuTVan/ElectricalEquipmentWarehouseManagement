package com.eewms.repository;

import com.eewms.entities.GoodIssueNote;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GoodIssueNoteRepository extends JpaRepository<GoodIssueNote, Long> {

    /** Dùng để chặn tạo trùng phiếu từ cùng một đơn bán */
    boolean existsBySaleOrder_SoId(Integer soId);

    // ==== NEW: phục vụ tính 'đã xuất' theo SO / SO+Product ====
    @Query("""
       select coalesce(sum(d.quantity), 0)
       from GoodIssueDetail d
       join d.goodIssueNote g
       where g.saleOrder.soId = :soId
         and d.product.id = :productId
         and (g.ginCode is null or g.ginCode not like 'RPL%')
       """)
    Integer sumIssuedQtyBySaleOrderAndProduct(@Param("soId") Integer soId,
                                              @Param("productId") Integer productId);

    @Query("""
       select coalesce(sum(d.quantity), 0)
       from GoodIssueDetail d
       join d.goodIssueNote g
       where g.saleOrder.soId = :soId
         and (g.ginCode is null or g.ginCode not like 'RPL%')
       """)
    Integer sumIssuedQtyBySaleOrder(@Param("soId") Integer soId);

    // ==== Các query cũ giữ nguyên ====
    @Query("""
              select distinct g from GoodIssueNote g
              left join fetch g.details d
              left join fetch d.product
              where g.issueDate between :from and :to
            """)
    List<GoodIssueNote> findByIssueDateBetweenWithDetails(LocalDateTime from, LocalDateTime to);

    @Query("""
              select g from GoodIssueNote g
              left join fetch g.customer
              order by g.issueDate desc
            """)
    List<GoodIssueNote> findRecentWithCustomer(Pageable pageable);

    @Query("""
              select distinct g from GoodIssueNote g
              left join fetch g.saleOrder so
              left join fetch g.customer c
              left join fetch g.details d
              left join fetch d.product
              order by g.issueDate desc
            """)
    List<GoodIssueNote> findAllWithDetails();

    @Query("select g.id from GoodIssueNote g order by g.issueDate desc")
    List<Long> findIdsOrderByIssueDateDesc(Pageable pageable);

    @Query("""
              select distinct g from GoodIssueNote g
              left join fetch g.saleOrder so
              left join fetch g.customer c
              left join fetch g.details d
              left join fetch d.product
              where g.id = :id
            """)
    Optional<GoodIssueNote> findByIdWithDetails(@Param("id") Long id);

    @Query("""
              select distinct g from GoodIssueNote g
              left join fetch g.details d
              left join fetch d.product
              where g.id in :ids
              order by g.issueDate desc
            """)
    List<GoodIssueNote> findByIdInWithDetails(List<Long> ids);

    //new
    @Query("""
       select d.product.id, coalesce(sum(d.quantity), 0)
       from GoodIssueDetail d
       join d.goodIssueNote g
       where g.saleOrder.soId = :soId
         and (g.ginCode is null or g.ginCode not like 'RPL%')
       group by d.product.id
       """)
    List<Object[]> sumIssuedBySaleOrderGroupByProduct(@Param("soId") Integer soId);
    Optional<GoodIssueNote> findByGinCode(String ginCode);

    //
    @Query("""
  select g.ginId
  from GoodIssueNote g
  where g.saleOrder.soId = :soId
  order by g.issueDate desc
""")
    List<Long> findGinIdsBySoIdOrderByIssueDateDesc(@Param("soId") Integer soId);

}
