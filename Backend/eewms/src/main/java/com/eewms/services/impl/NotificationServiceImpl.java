package com.eewms.services.impl;

import com.eewms.dto.NotificationDTO;
import com.eewms.entities.Notification;
import com.eewms.repository.NotificationRepository;
import com.eewms.services.INotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements INotificationService {

    private final NotificationRepository repo;

    @Override
    public NotificationDTO save(String type, String message) {
        String t = normType(type);
        String msg = (message == null ? "" : message.trim());
        Notification n = Notification.builder()
                .type(t)
                .message(msg)
                .build();
        n = repo.save(n);
        return toDTO(n);
    }

    @Override
    public List<NotificationDTO> list(int limit) {
        int lim = Math.max(1, Math.min(200, limit)); // clamp 1..200
        return repo.findAll(PageRequest.of(0, lim, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toDTO)
                .getContent();
    }

    @Override
    public void delete(Long id) {
        if (id != null && repo.existsById(id)) {
            repo.deleteById(id);
        }
    }

    @Override
    public void clear() {
        repo.deleteAllInBatch();
    }

    // --- helpers ---
    private String normType(String type) {
        String t = (StringUtils.hasText(type) ? type.trim().toLowerCase() : "info");
        return switch (t) {
            case "success", "info", "warning", "error" -> t;
            default -> "info";
        };
    }

    private NotificationDTO toDTO(Notification n) {
        if (n == null) return null;
        return NotificationDTO.builder()
                .id(n.getId())
                .type(n.getType())
                .message(n.getMessage())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
