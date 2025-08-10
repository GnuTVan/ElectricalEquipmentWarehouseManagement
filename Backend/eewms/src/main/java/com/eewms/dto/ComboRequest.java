package com.eewms.dto;

import com.eewms.entities.Combo;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComboRequest {

    @Size(max = 64, message = "Mã combo tối đa 64 ký tự")
    private String code;

    @NotBlank(message = "Tên combo không được để trống hoặc toàn khoảng trắng")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} ]+$",
            message = "Tên combo chỉ được chứa chữ cái, số và khoảng trắng"
    )
    private String name;

    private String description;

    private Combo.ComboStatus status = Combo.ComboStatus.ACTIVE;

    @NotEmpty(message = "Combo phải có ít nhất một sản phẩm")
    private List<Item> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        @NotNull(message = "ID sản phẩm không được null")
        private Integer productId;

        @NotNull(message = "Số lượng không được null")
        @Min(value = 1, message = "Số lượng phải lớn hơn hoặc bằng 1")
        @Max(value = 999, message = "Số lượng tối đa là 999")
        private Integer quantity;
    }
}
