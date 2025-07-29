package com.eewms.dto;

import com.eewms.entities.*;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComboDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Combo.ComboStatus status;
    private List<ComboDetailDTO> details;
}