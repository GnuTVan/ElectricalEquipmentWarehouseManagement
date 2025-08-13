package com.eewms.services;

import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.entities.PurchaseRequest;
import com.eewms.constant.PRStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IPurchaseRequestService {

    // Tạo PR (có thể từ SaleOrder nếu dto.saleOrderId != null)
    PurchaseRequest create(PurchaseRequestDTO dto);

    // Danh sách (paged) dưới dạng DTO
    Page<PurchaseRequestDTO> findAll(Pageable pageable);

    // Lấy entity/DTO theo id
    Optional<PurchaseRequest> findById(Long id);
    Optional<PurchaseRequestDTO> findDtoById(Long id);

    // Cập nhật trạng thái (vẫn giữ cho tương thích)
    // Lưu ý: nếu status == DA_DUYET sẽ tự chuyển sang luồng approve() để validate NCC
    void updateStatus(Long id, PRStatus status);

    // ✅ Mới: Duyệt PR — chỉ cho khi mọi item có suggestedSupplier thuộc product.suppliers
    void approve(Long id);

    // Cập nhật danh sách item (chỉ khi MOI_TAO)
    void updateItems(Long id, List<PurchaseRequestItemDTO> items);

    // Tạo các PO nhóm theo Nhà cung cấp (chỉ khi PR == DA_DUYET)
    void generatePurchaseOrdersFromRequest(Long prId) throws Exception;

    // Tìm kiếm/lọc theo người tạo + khoảng thời gian
    Page<PurchaseRequestDTO> filter(String creator, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
