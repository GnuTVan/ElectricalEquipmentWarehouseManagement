package com.eewms.repository;

import com.eewms.entities.GoodIssueNote;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

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
}
