package com.eewms.services.impl;
import com.eewms.entities.SaleOrder;
import com.eewms.services.ISaleOrderService;
import com.eewms.constant.PRStatus;
import com.eewms.constant.PurchaseOrderStatus;
import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseOrderItemDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestMapper;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.SupplierRepository;
import com.eewms.repository.purchaseRequest.PurchaseRequestRepository;
import com.eewms.services.IPurchaseOrderService;
import com.eewms.services.IPurchaseRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseRequestServiceImpl implements IPurchaseRequestService {

    private final PurchaseRequestRepository prRepo;
    private final ProductRepository productRepo;
    private final SupplierRepository supplierRepo;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final IPurchaseOrderService purchaseOrderService;
    private final ISaleOrderService saleOrderService;

    @Override
    @Transactional
    public PurchaseRequest create(PurchaseRequestDTO dto) {
        // ✅ Chặn tạo PR trùng cho cùng SaleOrder
        if (dto.getSaleOrderId() != null) {
            prRepo.findBySaleOrder_SoId(dto.getSaleOrderId())
                    .ifPresent(pr -> { throw new InventoryException("Đơn bán đã có yêu cầu mua hàng."); });
        }

        List<Product> products = productRepo.findAllById(
                dto.getItems().stream().map(i -> i.getProductId().intValue()).toList()
        );
        List<Supplier> suppliers = supplierRepo.findAll();

        PurchaseRequest request = PurchaseRequestMapper.toEntity(dto, products, suppliers);

        // ✅ Gắn SaleOrder vào PR (khắc phục lỗi không map)
        if (dto.getSaleOrderId() != null) {
            SaleOrder so = saleOrderService.getOrderEntityById(dto.getSaleOrderId());
            if (so == null) throw new InventoryException("Không tìm thấy SaleOrder: " + dto.getSaleOrderId());
            request.setSaleOrder(so);
        }

        request.setCode(generateCode());
        PurchaseRequest saved = prRepo.save(request);

        // (tuỳ) Cập nhật trạng thái SO nếu bạn muốn
        // if (dto.getSaleOrderId() != null) {
        //     saleOrderService.updateOrderStatus(dto.getSaleOrderId(), SaleOrder.SaleOrderStatus.PROCESSING);
        // }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseRequestDTO> findAll(Pageable pageable) {
        return prRepo.findAll(pageable)
                .map(PurchaseRequestMapper::toDTO);
    }

    @Override
    public Optional<PurchaseRequest> findById(Long id) {
        return prRepo.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseRequestDTO> findDtoById(Long id) {
        return prRepo.findWithItemsById(id)
                .map(PurchaseRequestMapper::toDTO);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, PRStatus status) {
        // Nếu set sang ĐÃ DUYỆT → chạy luồng duyệt với validate NCC
        if (status == PRStatus.DA_DUYET) {
            approve(id);
            return;
        }
        PurchaseRequest request = prRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy yêu cầu mua hàng"));
        request.setStatus(status);
        prRepo.save(request);
    }

    // ✅ Duyệt: chỉ cho khi mọi item có NCC thuộc danh sách NCC của Product
    public void approve(Long id) {
        PurchaseRequest pr = prRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy yêu cầu mua hàng"));

        if (pr.getStatus() != PRStatus.MOI_TAO) {
            throw new InventoryException("Chỉ duyệt PR ở trạng thái MỚI TẠO");
        }

        for (PurchaseRequestItem it : pr.getItems()) {
            Product p = it.getProduct();
            Supplier s = it.getSuggestedSupplier();
            if (p == null) throw new InventoryException("Thiếu sản phẩm cho một item");
            if (s == null) throw new InventoryException("Thiếu nhà cung cấp gợi ý cho sản phẩm: " + p.getName());
            if (p.getSuppliers() == null || !p.getSuppliers().contains(s)) {
                throw new InventoryException("NCC '" + s.getName() + "' không thuộc danh sách của sản phẩm '" + p.getName() + "'");
            }
        }

        pr.setStatus(PRStatus.DA_DUYET);
        prRepo.save(pr);
    }

    @Override
    @Transactional
    public void updateItems(Long id, List<PurchaseRequestItemDTO> items) {
        PurchaseRequest request = prRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy yêu cầu mua hàng"));

        if (request.getStatus() != PRStatus.MOI_TAO) {
            throw new InventoryException("Chỉ được sửa danh sách khi PR đang ở trạng thái MỚI TẠO");
        }

        List<Product> products = productRepo.findAll();
        List<Supplier> suppliers = supplierRepo.findAll();

        List<PurchaseRequestItem> newItems = items.stream().map(i -> {
            Product product = products.stream()
                    .filter(p -> Long.valueOf(p.getId()).equals(i.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new InventoryException("Không tìm thấy sản phẩm ID: " + i.getProductId()));

            Integer q = i.getQuantityNeeded();
            if (q == null || q <= 0) {
                throw new InventoryException("Số lượng cần mua phải lớn hơn 0 cho sản phẩm: " + product.getName());
            }

            Supplier supplier = null;
            if (i.getSuggestedSupplierId() != null) {
                supplier = suppliers.stream()
                        .filter(s -> Long.valueOf(s.getId()).equals(i.getSuggestedSupplierId()))
                        .findFirst()
                        .orElse(null); // cho phép null nếu chưa chọn
            }

            return PurchaseRequestItem.builder()
                    .id(i.getId())
                    .purchaseRequest(request)
                    .product(product)
                    .quantityNeeded(q)
                    .note(i.getNote())
                    .suggestedSupplier(supplier)
                    .build();
        }).toList();

        request.getItems().clear();
        request.getItems().addAll(newItems);
        prRepo.save(request);
    }

    private String generateCode() {
        long next = prRepo.findAll().stream()
                .map(PurchaseRequest::getCode)
                .filter(code -> code != null && code.matches("PR\\d+"))
                .map(code -> Long.parseLong(code.replace("PR", "")))
                .max(Comparator.naturalOrder())
                .orElse(0L) + 1;

        return String.format("PR%05d", next);
    }

    @Override
    @Transactional
    public void generatePurchaseOrdersFromRequest(Long prId) throws Exception {
        PurchaseRequest pr = prRepo.findById(prId)
                .orElseThrow(() -> new InventoryException("Không tìm thấy yêu cầu mua hàng"));

        if (!pr.getStatus().equals(PRStatus.DA_DUYET)) {
            throw new InventoryException("Chỉ tạo PO từ yêu cầu đã duyệt");
        }

        // ensure mọi item đều có NCC
        for (PurchaseRequestItem it : pr.getItems()) {
            if (it.getSuggestedSupplier() == null) {
                throw new InventoryException("Thiếu nhà cung cấp gợi ý cho sản phẩm: " +
                        (it.getProduct() != null ? it.getProduct().getName() : "UNKNOWN"));
            }
        }

        Map<Supplier, List<PurchaseRequestItem>> groupedBySupplier = pr.getItems().stream()
                .collect(Collectors.groupingBy(PurchaseRequestItem::getSuggestedSupplier));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";

        for (Map.Entry<Supplier, List<PurchaseRequestItem>> entry : groupedBySupplier.entrySet()) {
            Supplier supplier = entry.getKey();
            List<PurchaseRequestItem> items = entry.getValue();

            List<PurchaseOrderItemDTO> itemDTOs = items.stream().map(i -> {
                PurchaseOrderItemDTO dto = new PurchaseOrderItemDTO();
                dto.setProductId(i.getProduct().getId());
                dto.setContractQuantity(i.getQuantityNeeded());
                dto.setPrice(i.getProduct().getOriginPrice()); // bạn có thể thay bằng chính sách giá khác
                dto.setActualQuantity(null); // ban đầu chưa giao
                return dto;
            }).toList();

            PurchaseOrderDTO orderDTO = new PurchaseOrderDTO();
            orderDTO.setSupplierId(supplier.getId());
            orderDTO.setItems(itemDTOs);
            orderDTO.setNote("Tạo từ yêu cầu mua hàng " + pr.getCode());
            orderDTO.setCreatedByName(username);

            purchaseOrderService.create(orderDTO);
        }

        pr.setStatus(PRStatus.DA_TAO_PO);
        prRepo.save(pr);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseRequestDTO> filter(String creator, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return prRepo.filter(creator, startDate, endDate, pageable)
                .map(PurchaseRequestMapper::toDTO);
    }
}
