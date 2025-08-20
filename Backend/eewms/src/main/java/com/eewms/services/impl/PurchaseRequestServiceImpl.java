package com.eewms.services.impl;

import com.eewms.constant.PRStatus;
import com.eewms.dto.purchase.PurchaseOrderDTO;
import com.eewms.dto.purchase.PurchaseOrderItemDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestItemDTO;
import com.eewms.dto.purchaseRequest.PurchaseRequestMapper;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.PurchaseOrderRepository;
import com.eewms.repository.SaleOrderRepository;
import com.eewms.repository.SupplierRepository;
import com.eewms.repository.purchaseRequest.PurchaseRequestRepository;
import com.eewms.services.IPurchaseOrderService;
import com.eewms.services.IPurchaseRequestService;
import com.eewms.services.ISaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.CustomerRepository;

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
    private final GoodIssueNoteRepository goodIssueNoteRepository;
    private final SaleOrderRepository saleOrderRepository;
    private final CustomerRepository customerRepository;


    @Override
    @Transactional
    public PurchaseRequest create(PurchaseRequestDTO dto) {
        if (dto.getSaleOrderId() != null) {
            prRepo.findBySaleOrder_SoId(dto.getSaleOrderId())
                    .ifPresent(pr -> { throw new InventoryException("Đơn bán đã có yêu cầu mua hàng."); });
        }

        List<Product> products = productRepo.findAllById(
                dto.getItems().stream().map(i -> i.getProductId().intValue()).toList()
        );
        List<Supplier> suppliers = supplierRepo.findAll();

        PurchaseRequest request = PurchaseRequestMapper.toEntity(dto, products, suppliers);

        if (dto.getSaleOrderId() != null) {
            SaleOrder so = saleOrderService.getOrderEntityById(dto.getSaleOrderId());
            if (so == null) throw new InventoryException("Không tìm thấy SaleOrder: " + dto.getSaleOrderId());
            request.setSaleOrder(so);
            request.setCustomer(so.getCustomer());
        }

        request.setCode(generateCode());
        // Trạng thái mặc định đã set trong @PrePersist: MOI_TAO (chờ duyệt)
        return prRepo.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseRequestDTO> findAll(Pageable pageable) {
        return prRepo.findAll(pageable).map(PurchaseRequestMapper::toDTO);
    }

    @Override
    public Optional<PurchaseRequest> findById(Long id) {
        return prRepo.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseRequestDTO> findDtoById(Long id) {
        return prRepo.findWithItemsById(id).map(PurchaseRequestMapper::toDTO);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, PRStatus status) {
        if (status == PRStatus.DA_DUYET) { approve(id); return; }
        PurchaseRequest request = prRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy yêu cầu mua hàng"));
        request.setStatus(status);
        prRepo.save(request);
    }

    @Override
    @Transactional
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
                        .orElse(null);
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
                dto.setPrice(i.getProduct().getOriginPrice());
                dto.setActualQuantity(null);
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
    public Page<PurchaseRequestDTO> filter(String creator, java.time.LocalDateTime startDate, java.time.LocalDateTime endDate, Pageable pageable) {
        return prRepo.filter(creator, startDate, endDate, pageable).map(PurchaseRequestMapper::toDTO);
    }

    // ==== B5: tự động gom thiếu của TẤT CẢ đơn PENDING/PARTLY_DELIVERED của 1 khách ====
    @Override
    @Transactional
    public PurchaseRequest generateForCustomer(Long customerId, String createdByName) {
        var customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new InventoryException("Không tìm thấy khách hàng"));

        List<SaleOrder> orders = saleOrderRepository.findByCustomerIdAndStatusIn(
                customerId, List.of(SaleOrder.SaleOrderStatus.PENDING, SaleOrder.SaleOrderStatus.PARTLY_DELIVERED)
        );

        Map<Integer, Integer> shortageByProduct = new LinkedHashMap<>();
        for (SaleOrder so : orders) {
            for (var d : so.getDetails()) {
                Integer issued = goodIssueNoteRepository
                        .sumIssuedQtyBySaleOrderAndProduct(so.getSoId(), d.getProduct().getId());
                int shortage = d.getOrderedQuantity() - (issued == null ? 0 : issued);
                if (shortage > 0) {
                    shortageByProduct.merge(d.getProduct().getId(), shortage, Integer::sum);
                }
            }
        }
        if (shortageByProduct.isEmpty()) {
            throw new InventoryException("Không có sản phẩm thiếu để tạo yêu cầu mua.");
        }

        PurchaseRequest pr = prRepo.findFirstByCustomer_IdAndStatusInOrderByIdDesc(
                customerId, List.of(PRStatus.MOI_TAO)).orElse(null);

        if (pr == null) {
            pr = PurchaseRequest.builder()
                    .code(generateCode())
                    .createdByName(createdByName)
                    .status(PRStatus.MOI_TAO) // chờ duyệt
                    .customer(customer)
                    .build();
            pr.setItems(new ArrayList<>());
        } else {
            pr.getItems().clear();
        }

        List<Product> products = productRepo.findAllById(shortageByProduct.keySet());
        Map<Integer, Product> byId = products.stream().collect(Collectors.toMap(Product::getId, p -> p));

        for (var e : shortageByProduct.entrySet()) {
            Product p = byId.get(e.getKey());
            pr.getItems().add(PurchaseRequestItem.builder()
                    .purchaseRequest(pr)
                    .product(p)
                    .quantityNeeded(e.getValue())
                    .build());
        }

        return prRepo.save(pr);
    }

    @Override
    @Transactional
    public void cancel(Long id, String reason) {
        PurchaseRequest pr = prRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy yêu cầu mua hàng"));
        if (pr.getStatus() != PRStatus.MOI_TAO) {
            throw new InventoryException("Chỉ được hủy khi PR đang ở trạng thái MỚI TẠO");
        }
        if (reason == null || reason.isBlank()) {
            throw new InventoryException("Vui lòng nhập lý do hủy");
        }
        pr.setStatus(PRStatus.HUY);
        pr.setCancelReason(reason);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        pr.setCanceledByName(auth != null ? auth.getName() : "system");
        pr.setCanceledAt(LocalDateTime.now());
        prRepo.save(pr);
    }
    @Override
    @Transactional
    public PurchaseRequest generateForAllOpen(String createdByName) {

        // Lấy tất cả SO đang mở
        List<SaleOrder> orders = saleOrderRepository.findByStatusIn(
                List.of(SaleOrder.SaleOrderStatus.PENDING, SaleOrder.SaleOrderStatus.PARTLY_DELIVERED)
        );

        // Gom thiếu theo product trên toàn hệ thống
        Map<Integer, Integer> shortageByProduct = new LinkedHashMap<>();
        for (SaleOrder so : orders) {
            for (SaleOrderDetail d : so.getDetails()) {
                Integer issued = goodIssueNoteRepository
                        .sumIssuedQtyBySaleOrderAndProduct(so.getSoId(), d.getProduct().getId());
                int remaining = d.getOrderedQuantity() - (issued == null ? 0 : issued);
                if (remaining > 0) {
                    shortageByProduct.merge(d.getProduct().getId(), remaining, Integer::sum);
                }
            }
        }

        if (shortageByProduct.isEmpty()) {
            throw new InventoryException("Không có sản phẩm thiếu để tạo yêu cầu mua tổng hợp.");
        }

        // Tạo PR mới (GLOBAL): không gán customer/saleOrder
        PurchaseRequest pr = PurchaseRequest.builder()
                .code(generateCode())
                .createdByName(createdByName)
                .status(PRStatus.MOI_TAO)
                .build();
        pr.setItems(new ArrayList<>());

        List<Product> products = productRepo.findAllById(shortageByProduct.keySet());
        Map<Integer, Product> byId = products.stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p));

        for (var e : shortageByProduct.entrySet()) {
            Product p = byId.get(e.getKey());
            if (p == null) continue;
            pr.getItems().add(PurchaseRequestItem.builder()
                    .purchaseRequest(pr)
                    .product(p)
                    .quantityNeeded(e.getValue())
                    .build());
        }

        return prRepo.save(pr);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseRequestItemDTO> collectShortagesForAllOpen(LocalDateTime start, LocalDateTime end) {
        // Lấy tất cả SO đang mở theo khoảng ngày
        List<SaleOrder> orders = saleOrderRepository.findOpenOrdersInRange(start, end);

        Map<Integer, Integer> shortageByProduct = new LinkedHashMap<>();

        for (SaleOrder so : orders) {
            for (SaleOrderDetail d : so.getDetails()) {
                int pid = d.getProduct().getId();
                int ordered = d.getOrderedQuantity();

                Integer issued = goodIssueNoteRepository
                        .sumIssuedQtyBySaleOrderAndProduct(so.getSoId(), pid);
                int issuedQty = (issued == null ? 0 : issued);

                // NEW: nếu user KHÔNG chọn start/end => KHÔNG trừ PR mở
                int requestedQty = 0;
                if (start != null || end != null) {
                    Integer requestedOpen = prRepo
                            .sumRequestedQtyOpenPRByProductInRange(pid, start, end);
                    requestedQty = (requestedOpen == null ? 0 : requestedOpen);
                }

                int remaining = ordered - issuedQty - requestedQty;
                if (remaining > 0) {
                    shortageByProduct.merge(pid, remaining, Integer::sum);
                }
            }
        }

        if (shortageByProduct.isEmpty()) return List.of();

        List<Product> products = productRepo.findAllById(shortageByProduct.keySet());
        Map<Integer, Product> pmap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return shortageByProduct.entrySet().stream()
                .map(e -> {
                    Product p = pmap.get(e.getKey());
                    return PurchaseRequestItemDTO.builder()
                            .productId(Long.valueOf(e.getKey()))
                            .productName(p != null ? p.getName() : ("SP#" + e.getKey()))
                            .quantityNeeded(e.getValue())
                            .build();
                })
                .toList();
    }


    @Override
    @Transactional
    public PurchaseRequestDTO createFromCollected(List<PurchaseRequestItemDTO> items, String createdBy) {
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("Không có sản phẩm thiếu để tạo yêu cầu.");
        }

        // Dựng DTO rồi dùng lại create(dto) để save entity + items
        PurchaseRequestDTO dto = PurchaseRequestDTO.builder()
                .createdByName(createdBy)
                .items(items)
                .build();

        PurchaseRequest saved = this.create(dto); // dùng service hiện có
        return PurchaseRequestMapper.toDTO(saved); // mapper static của bạn
    }
}
