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
    boolean existsBySaleOrder_SoId(Integer soId);

    // Dùng cho flow SL + topIssued: fetch details + product để tránh Lazy/N+1
    @Query("""
              select distinct g from GoodIssueNote g
              left join fetch g.details d
              left join fetch d.product
              where g.issueDate between :from and :to
            """)
    List<GoodIssueNote> findByIssueDateBetweenWithDetails(LocalDateTime from, LocalDateTime to);

    // Dùng cho recent: fetch customer
    @Query("""
              select g from GoodIssueNote g
              left join fetch g.customer
              order by g.issueDate desc
            """)
    List<GoodIssueNote> findRecentWithCustomer(Pageable pageable);

    // Dùng cho trang danh sách: fetch details + product để tránh Lazy/N+1
    @Query("""
              select distinct g from GoodIssueNote g
              left join fetch g.saleOrder so
              left join fetch g.customer c          
              left join fetch g.details d
              left join fetch d.product
              order by g.issueDate desc
            """)
    List<GoodIssueNote> findAllWithDetails();

    // Nếu cần phân trang thực sự, KHÔNG dùng fetch collection + Pageable trực tiếp.
// Làm 2 bước: lấy page id trước, rồi fetch theo ids.
    @Query("select g.id from GoodIssueNote g order by g.issueDate desc")
    List<Long> findIdsOrderByIssueDateDesc(Pageable pageable);

    // Lấy 1 GIN kèm details (+ product) để dùng cho getById
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
}
