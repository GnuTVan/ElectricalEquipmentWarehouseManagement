package com.eewms.services;

import com.eewms.dto.NotificationDTO;
import java.util.List;

public interface INotificationService {
    NotificationDTO save(String type, String message);
    List<NotificationDTO> list(int limit);
    void delete(Long id);
    void clear();
}
