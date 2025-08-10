package com.eewms.controller;

import com.eewms.dto.NotificationDTO;
import com.eewms.services.INotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService notificationService;

    @GetMapping
    public List<NotificationDTO> list(@RequestParam(defaultValue = "20") int limit) {
        return notificationService.list(limit);
    }

    @PostMapping
    public NotificationDTO create(@RequestBody NotificationDTO req) {
        return notificationService.save(req.getType(), req.getMessage());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        notificationService.delete(id);
    }

    @DeleteMapping
    public void clear() {
        notificationService.clear();
    }
}
