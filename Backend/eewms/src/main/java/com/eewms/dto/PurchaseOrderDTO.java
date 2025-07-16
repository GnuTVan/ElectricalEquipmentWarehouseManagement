package com.eewms.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderDTO {
    private Long poId;
    private String poCode;
    private Long supplierId;
    private LocalDateTime orderDate;
    private String status;
//
//    private String supplierName;
//    private String supplierContactName;
//    private String supplierAddress;
//    private String supplierPhone;
//    private String purchaseRequestCode;
//    private Long purchaseRequestId;
//
//    // Chi tiết đơn hàng - chỉ sử dụng cho chi tiết
//    private List<PurchaseOrderDetailDTO> details;
//
//    // Static factory method cho danh sách
//    public static PurchaseOrderDTO createForList(PurchaseOrder order) {
//        return PurchaseOrderDTO.builder()
//                .poId(order.getPoId())
//                .poCode(order.getPoCode())
//                .supplierId(order.getPartner() != null ? order.getPartner().getPartnerId() : null)
//                .supplierName(order.getPartner() != null ? order.getPartner().getPartnerName() : null)
//                .orderDate(order.getOrderDate())
//                .status(order.getStatus().name())
//                .build();
//    }
}
