package com.eewms.services.impl;

import com.eewms.dto.SaleOrderItemDTO;
import com.eewms.dto.SaleOrderMapper;
import com.eewms.dto.SaleOrderRequestDTO;
import com.eewms.dto.SaleOrderResponseDTO;
import com.eewms.entities.*;
import com.eewms.repository.*;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.ISaleOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleOrderServiceImpl implements ISaleOrderService {

    private final SaleOrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final GoodIssueNoteRepository goodIssueNoteRepo;
    private final IGoodIssueService goodIssueService;

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
        saleOrder.setDescription(dto.getDescription());

        List<SaleOrderDetail> detailList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (SaleOrderItemDTO item : dto.getItems()) {
            Product product = productRepo.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (product.getQuantity() < item.getOrderedQuantity()) {
                throw new RuntimeException("Không đủ tồn kho cho sản phẩm: " + product.getName());
            }

            SaleOrderDetail detail = SaleOrderMapper.toOrderDetail(item, product);
            detail.setSale_order(saleOrder);
            detailList.add(detail);

            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getOrderedQuantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        saleOrder.setDetails(detailList);
        saleOrder.setTotalAmount(totalAmount);
        orderRepo.save(saleOrder);

        return SaleOrderMapper.toOrderResponseDTO(saleOrder);
    }


    @Override
    public List<SaleOrderResponseDTO> getAllOrders() {
        return orderRepo.findAll().stream()
                .map(SaleOrderMapper::toOrderResponseDTO)
                .toList();
    }

    @Override
    public SaleOrderResponseDTO getById(Integer orderId) {
        SaleOrder saleOrder = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return SaleOrderMapper.toOrderResponseDTO(saleOrder);
    }

    @Transactional
    @Override
    public void updateOrderStatus(Integer orderId, SaleOrder.SaleOrderStatus newStatus) {
        SaleOrder saleOrder = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (saleOrder.getStatus() != SaleOrder.SaleOrderStatus.PENDING) {
            throw new RuntimeException("Chỉ đơn hàng ở trạng thái 'Chờ lấy hàng' mới được sửa.");
        }

        saleOrder.setStatus(newStatus);
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
        return orderRepo.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
    }

}
