package com.eewms.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageDTO {
    private Integer id;
    private String imageUrl;
    private boolean isThumbnail; //check thumbnail
}
