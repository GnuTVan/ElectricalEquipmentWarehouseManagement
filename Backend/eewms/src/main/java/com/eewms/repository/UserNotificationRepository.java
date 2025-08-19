package com.eewms.repository;

import com.eewms.entities.UserNotification;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    @Query("""
    SELECT un FROM UserNotification un
    JOIN FETCH un.notification n
    WHERE un.user.id = :userId
      AND (:onlyUnread = false OR un.isRead = false)
      AND (:type IS NULL OR n.type = :type)
    ORDER BY un.createdAt DESC
  """)
    Page<UserNotification> findByUser(
            @Param("userId") Long userId,
            @Param("onlyUnread") boolean onlyUnread,
            @Param("type") String type,
            Pageable pageable);

    long countByUser_IdAndIsReadFalse(Long userId);

    @Modifying
    @Query("UPDATE UserNotification un SET un.isRead = true, un.readAt = :now WHERE un.user.id = :userId AND un.isRead = false")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from UserNotification un where un.id = :id and un.user.id = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

}
