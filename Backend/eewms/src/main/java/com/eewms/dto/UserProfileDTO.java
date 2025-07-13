package com.eewms.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDTO {
    private String fullName;
    private String phone;
    private String email;
    private String address;
    private String avatarUrl;
}