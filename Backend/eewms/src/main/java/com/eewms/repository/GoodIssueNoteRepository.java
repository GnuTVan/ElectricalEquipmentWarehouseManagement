package com.eewms.repository;

import com.eewms.entities.GoodIssueNote;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface GoodIssueNoteRepository extends JpaRepository<GoodIssueNote, Long> {

    /** Dùng để chặn tạo trùng phiếu từ cùng một đơn bán */
    boolean existsBySaleOrder_SoId(Integer soId);

    // Các query phục vụ dashboard/thống kê (giữ nguyên nếu đang dùng)
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
}
