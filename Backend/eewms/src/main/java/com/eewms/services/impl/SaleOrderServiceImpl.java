package com.eewms.services.impl;

import com.eewms.dto.SaleOrderDetailDTO;
import com.eewms.dto.SaleOrderMapper;
import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.*;
import com.eewms.repository.*;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.ISaleOrderService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements ISaleOrderService {


    private final SaleOrderDetailRepository saleOrderDetailRepository;
    private final ProductRepository productRepository;

    private final SaleOrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final GoodIssueNoteRepository goodIssueRepository;
    private final ComboRepository comboRepository;
    //sale order combo

    private final SaleOrderComboRepository saleOrderComboRepository;


    @Override
    @Transactional
    public SaleOrderResponseDTO createOrder(SaleOrderRequestDTO dto, String createdByUsername) {
        Customer customer = customerRepo.findById(dto.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        User user = userRepo.findByUsername(createdByUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String orderCode = generateOrderCode();
        SaleOrder saleOrder = new SaleOrder();
        saleOrder.setSoCode(orderCode);
        saleOrder.setCustomer(customer);
        saleOrder.setCreatedByUser(user);
        saleOrder.setStatus(SaleOrder.SaleOrderStatus.PENDING);

        List<SaleOrderDetail> detailList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        StringBuilder warningNote = new StringBuilder();
        boolean hasInsufficientStock = false;

        for (SaleOrderDetailDTO item : dto.getDetails()) {
            Product product = productRepo.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (product.getQuantity() < item.getOrderedQuantity()) {
                hasInsufficientStock = true;
                warningNote.append(String.format("- Sản phẩm %s thiếu hàng (YC: %d / Tồn: %d)\n",
                        product.getName(), item.getOrderedQuantity(), product.getQuantity()));
            }

            SaleOrderDetail detail = SaleOrderMapper.toOrderDetail(item, product);
            detail.setSale_order(saleOrder);
            detailList.add(detail);

            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getOrderedQuantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        // ===== PHẦN MÔ TẢ: GHÉP THIẾU HÀNG + COMBO =====
        // 3.1. Base description (nếu thiếu hàng thì ưu tiên thông báo)
        String baseDesc;
        if (hasInsufficientStock) {
            baseDesc = "Đơn hàng thiếu hàng, cần nhập thêm để hoàn thành:\n" + warningNote.toString().trim();
        } else {
            baseDesc = Optional.ofNullable(dto.getDescription()).orElse("").trim();
        }

        // 3.2. Nếu có comboIds thì nối thêm "Đơn có combo: CODE1, CODE2"
        //      -> cần inject ComboRepository vào service này
        if (dto.getComboIds() != null && !dto.getComboIds().isEmpty()) {
            List<Combo> combos = comboRepository.findAllById(dto.getComboIds());
            // bạn có thể thay .map(Combo::getCode) bằng .map(Combo::getName) nếu muốn hiển thị tên
            String comboLabel = combos.stream()
                    .map(Combo::getName)      // hoặc getName()
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining(", "));

            if (!comboLabel.isBlank()) {
                String suffix = "Đơn có combo: " + comboLabel;
                baseDesc = baseDesc.isBlank() ? suffix : (baseDesc + " | " + suffix);
            }
        }

        saleOrder.setDescription(baseDesc);
        // ===== HẾT PHẦN GHÉP MÔ TẢ =====

        saleOrder.setDetails(detailList);
        saleOrder.setTotalAmount(totalAmount);
        orderRepo.save(saleOrder);
        if (dto.getComboIds() != null && !dto.getComboIds().isEmpty()) {
            Map<Long, Long> counts = dto.getComboIds().stream()
                    .collect(Collectors.groupingBy(id -> id, Collectors.counting()));

            for (Map.Entry<Long, Long> e : counts.entrySet()) {
                Combo combo = comboRepository.findById(e.getKey())
                        .orElseThrow(() -> new RuntimeException("Combo not found: " + e.getKey()));
                SaleOrderCombo soc = SaleOrderCombo.builder()
                        .saleOrder(saleOrder)
                        .combo(combo)
                        .quantity(e.getValue().intValue())
                        .build();
                saleOrderComboRepository.save(soc);
            }
        }

        return SaleOrderMapper.toOrderResponseDTO(saleOrder);
    }

    @Transactional
    @Override
    public void updateOrderItems(Integer orderId, SaleOrderRequestDTO form) {
        SaleOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Sale order not found"));

        if (order.getStatus() != SaleOrder.SaleOrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING order can be edited");
        }

        Set<Integer> ids = form.getDetails().stream()
                .map(SaleOrderDetailDTO::getProductId)
                .collect(Collectors.toSet());

        Map<Integer, Product> pmap = productRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<SaleOrderDetail> newLines = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;

        for (SaleOrderDetailDTO d : form.getDetails()) {
            Product p = pmap.get(d.getProductId());
            if (p == null || p.getStatus() != Product.ProductStatus.ACTIVE) {
                throw new IllegalArgumentException("Invalid/inactive product: " + d.getProductId());
            }

            // Null-safety
            int qty = Optional.ofNullable(d.getOrderedQuantity()).orElse(0);
            BigDecimal price = Optional.ofNullable(d.getPrice()).orElse(BigDecimal.ZERO);

            SaleOrderDetail line = new SaleOrderDetail();
            line.setSale_order(order);
            line.setProduct(p);
            line.setOrderedQuantity(qty);
            line.setPrice(price);
            newLines.add(line);

            // Cộng dồn tổng tiền theo price * qty (không dùng detail.total)
            sum = sum.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        saleOrderDetailRepository.deleteByOrderSoId(orderId);
        saleOrderDetailRepository.saveAll(newLines);

        order.setTotalAmount(sum);
        order.setDescription(Optional.ofNullable(form.getDescription()).orElse(""));

        StringBuilder warn = new StringBuilder();
        for (SaleOrderDetail l : newLines) {
            int stock = Optional.ofNullable(l.getProduct().getQuantity()).orElse(0);
            if (l.getOrderedQuantity() > stock) {
                warn.append("\nThiếu hàng: ").append(l.getProduct().getName())
                        .append(" cần ").append(l.getOrderedQuantity())
                        .append(" tồn ").append(stock);
            }
        }
        if (warn.length() > 0) {
            order.setDescription((order.getDescription() + "\n" + warn).trim());
        }

        orderRepo.save(order);
    }

    @Override
    public List<SaleOrderResponseDTO> getAllOrders() {
        return orderRepo.findAll().stream()
                .map(SaleOrderMapper::toOrderResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SaleOrderResponseDTO getById(Integer orderId) {
        SaleOrder saleOrder = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        SaleOrderResponseDTO dto = SaleOrderMapper.toOrderResponseDTO(saleOrder);

        boolean exported = goodIssueRepository.existsBySaleOrder_SoId(saleOrder.getSoId());
        boolean stillMissing = !exported && saleOrder.getDetails().stream()
                .anyMatch(d -> d.getProduct().getQuantity() < d.getOrderedQuantity());

        dto.setHasInsufficientStock(stillMissing);
        dto.setAlreadyExported(exported);

        return dto;
    }

    @Transactional
    @Override
    public void updateOrderStatus(Integer orderId, SaleOrder.SaleOrderStatus newStatus) {
        SaleOrder saleOrder = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        SaleOrder.SaleOrderStatus currentStatus = saleOrder.getStatus();

        if (currentStatus == SaleOrder.SaleOrderStatus.COMPLETED) {
            throw new RuntimeException("Đơn hàng đã hoàn thành không thể cập nhật.");
        }

        if (currentStatus == SaleOrder.SaleOrderStatus.PENDING && newStatus == SaleOrder.SaleOrderStatus.DELIVERIED) {
            // Trạng thái hợp lệ
            saleOrder.setStatus(SaleOrder.SaleOrderStatus.DELIVERIED);
        } else if (currentStatus == SaleOrder.SaleOrderStatus.DELIVERIED && newStatus == SaleOrder.SaleOrderStatus.COMPLETED) {
            // Trạng thái hợp lệ
            saleOrder.setStatus(SaleOrder.SaleOrderStatus.COMPLETED);
        } else {
            throw new RuntimeException("Không thể cập nhật từ " + currentStatus + " sang " + newStatus);
        }

        orderRepo.save(saleOrder);
    }

    @Override
    public List<SaleOrderResponseDTO> searchByKeyword(String keyword) {
        return orderRepo.searchByKeyword(keyword).stream()
                .map(SaleOrderMapper::toOrderResponseDTO)
                .collect(Collectors.toList());
    }

    private String generateOrderCode() {
        long count = orderRepo.count() + 1;
        return String.format("ORD%05d", count);
    }

    @Override
    public SaleOrder getOrderEntityById(Integer id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
    }
}
