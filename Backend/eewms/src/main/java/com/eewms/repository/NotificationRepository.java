package com.eewms.repository;

import com.eewms.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Lấy mới nhất giới hạn số lượng
    List<Notification> findTop20ByOrderByCreatedAtDesc();

    // Lấy mới nhất với giới hạn tuỳ chọn
    List<Notification> findTopByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);
}
