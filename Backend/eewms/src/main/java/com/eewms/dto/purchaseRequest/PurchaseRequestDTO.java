package com.eewms.dto.purchaseRequest;

import com.eewms.constant.PRStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseRequestDTO {
    private Long id;
    private String code;

    @NotBlank(message = "Vui lòng nhập người tạo")
    private String createdByName;

    private LocalDateTime createdAt;
    private PRStatus status;

    @NotNull(message = "Danh sách sản phẩm không được để trống")
    private List<PurchaseRequestItemDTO> items;
    private Integer saleOrderId;
}