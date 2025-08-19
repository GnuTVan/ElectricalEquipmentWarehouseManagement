package com.eewms.dto;

import com.eewms.entities.SaleOrder.SaleOrderStatus; // enum trạng thái đang nằm trong entity SaleOrder
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleOrderRequestDTO {

    @NotNull(message = "Vui lòng chọn khách hàng")
    private Long customerId;

    /** Số điện thoại để đối chiếu khách hàng trùng tên (server sẽ validate khớp với KH đã chọn) */
    @NotBlank(message = "Vui lòng nhập số điện thoại khách hàng")
    @Size(max = 32, message = "Số điện thoại không hợp lệ")
    private String customerPhone;

    /** Mã đơn hàng: có thể để trống, server sẽ tự sinh nếu null/blank */
    @Size(max = 64, message = "Mã đơn không được vượt quá 64 ký tự")
    private String soCode;

    /** Trạng thái đơn hàng (giá trị theo SaleOrder.SaleOrderStatus trong dự án) */
    @NotNull(message = "Vui lòng chọn trạng thái")
    @Builder.Default
    private SaleOrderStatus status = SaleOrderStatus.PENDING;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;

    /** Chi tiết sản phẩm */
    private List<@Valid SaleOrderDetailDTO> details;

    /** Các combo được chọn (cho phép lặp ở tầng form – backend xử lý) */
    private List<Long> comboIds;

    /** Đếm combo (nếu bạn đang dùng để tổng hợp) */
    @Builder.Default
    private Map<Long, Integer> comboCounts = new LinkedHashMap<>();
}
