package com.eewms.dto;

import jakarta.validation.Valid;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleOrderRequestDTO {

    @NotNull(message = "Vui lòng chọn khách hàng")
    private Long customerId;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;

    private List<@Valid SaleOrderDetailDTO> details;

    private List<Long> comboIds;

    private Map<Long, Integer> comboCounts = new LinkedHashMap<>();
}