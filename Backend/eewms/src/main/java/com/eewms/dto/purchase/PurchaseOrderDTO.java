package com.eewms.dto.purchase;

import com.eewms.constant.PurchaseOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderDTO {

    private Long id;

    private String code; // mã đơn (P00001...)

    @NotNull(message = "Vui lòng chọn nhà cung cấp")
    private Long supplierId;

    private String supplierName; // ✅ Thêm tên nhà cung cấp để hiển thị

    @NotBlank(message = "Vui lòng nhập người tạo đơn")
    private String createdByName;

    private String note;

    private MultipartFile attachmentFile; // File chứng từ

    private String attachmentUrl; // URL sau khi upload

    @Valid
    private List<PurchaseOrderItemDTO> items;

    private PurchaseOrderStatus status; // ✅ Trạng thái đơn hàng

    private BigDecimal totalAmount; // ✅ Thành tiền (dùng để hiển thị)

    private LocalDateTime createdAt; // ✅ Thời gian tạo đơn
}

