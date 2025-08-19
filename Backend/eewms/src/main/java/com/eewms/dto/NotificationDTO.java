package com.eewms.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationDTO {
    private Long id;
    private Long userNotificationId;
    private String type;
    private String message;
    private LocalDateTime createdAt;
    private Boolean read;
}
