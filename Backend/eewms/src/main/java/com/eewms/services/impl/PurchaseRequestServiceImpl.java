package com.eewms.services.impl;

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

    @Override
    @Transactional
    public PurchaseRequest create(PurchaseRequestDTO dto) {
        List<Product> products = productRepo.findAllById(
                dto.getItems().stream().map(i -> i.getProductId().intValue()).toList()
        );

        List<Supplier> suppliers = supplierRepo.findAll();

        PurchaseRequest request = PurchaseRequestMapper.toEntity(dto, products, suppliers);
        request.setCode(generateCode());

        return prRepo.save(request);
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
        PurchaseRequest request = prRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy yêu cầu mua hàng"));
        request.setStatus(status);
        prRepo.save(request);
    }

    @Override
    @Transactional
    public void updateItems(Long id, List<PurchaseRequestItemDTO> items) {
        PurchaseRequest request = prRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy yêu cầu mua hàng"));

        List<Product> products = productRepo.findAll();
        List<Supplier> suppliers = supplierRepo.findAll();

        List<PurchaseRequestItem> newItems = items.stream().map(i -> {
            Product product = products.stream()
                    .filter(p -> Long.valueOf(p.getId()).equals(i.getProductId()))
                    .findFirst()
                    .orElseThrow(() -> new InventoryException("Không tìm thấy sản phẩm ID: " + i.getProductId()));

            if (i.getQuantityNeeded() == null || i.getQuantityNeeded() <= 0) {
                throw new InventoryException("Số lượng cần mua phải lớn hơn 0");
            }

            Supplier supplier = null;
            if (i.getSuggestedSupplierId() != null) {
                supplier = suppliers.stream()
                        .filter(s -> Long.valueOf(s.getId()).equals(i.getSuggestedSupplierId()))
                        .findFirst()
                        .orElse(null); // Cho phép null nếu chưa chọn NCC
            }

            return PurchaseRequestItem.builder()
                    .id(i.getId()) // có thể giữ lại nếu cần
                    .purchaseRequest(request)
                    .product(product)
                    .quantityNeeded(i.getQuantityNeeded())
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

        Map<Supplier, List<PurchaseRequestItem>> groupedBySupplier = pr.getItems().stream()
                .filter(i -> i.getSuggestedSupplier() != null)
                .collect(Collectors.groupingBy(PurchaseRequestItem::getSuggestedSupplier));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        for (Map.Entry<Supplier, List<PurchaseRequestItem>> entry : groupedBySupplier.entrySet()) {
            Supplier supplier = entry.getKey();
            List<PurchaseRequestItem> items = entry.getValue();

            List<PurchaseOrderItemDTO> itemDTOs = items.stream().map(i -> {
                PurchaseOrderItemDTO dto = new PurchaseOrderItemDTO();
                dto.setProductId(i.getProduct().getId());
                dto.setContractQuantity(i.getQuantityNeeded());
                dto.setPrice(i.getProduct().getOriginPrice()); // sử dụng originPrice
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
    public Page<PurchaseRequestDTO> search(String keyword, Pageable pageable) {
        return prRepo.search(keyword, pageable)
                .map(PurchaseRequestMapper::toDTO);
    }

}