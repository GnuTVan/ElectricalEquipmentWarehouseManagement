package com.eewms.dto;

import jakarta.validation.Valid;
import lombok.*;
import java.util.List;

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

    @NotEmpty(message = "Cần có ít nhất 1 sản phẩm")
    private List<@Valid SaleOrderDetailDTO> details;
}