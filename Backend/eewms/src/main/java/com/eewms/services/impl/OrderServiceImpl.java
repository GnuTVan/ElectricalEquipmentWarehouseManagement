package com.eewms.services.impl;

import com.eewms.dto.*;
import com.eewms.entities.*;
import com.eewms.dto.OrderMapper;
import com.eewms.repository.*;
import com.eewms.services.IGoodIssueService;
import com.eewms.services.IOrderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements IOrderService {

    private final OrderRepository orderRepo;
    private final ProductRepository productRepo;
    private final CustomerRepository customerRepo;
    private final UserRepository userRepo;
    private final GoodIssueNoteRepository goodIssueNoteRepo;
    private final IGoodIssueService goodIssueService;

    @Override
    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO dto, String createdByUsername) {
        Customer customer = customerRepo.findById(dto.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found"));
        User user = userRepo.findByUsername(createdByUsername)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String orderCode = generateOrderCode();
        Order order = new Order();
        order.setPoCode(orderCode);
        order.setCustomer(customer);
        order.setCreatedByUser(user);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setDescription(dto.getDescription());

        List<OrderDetail> detailList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDTO item : dto.getItems()) {
            Product product = productRepo.findById(item.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found"));

            if (product.getQuantity() < item.getOrderedQuantity()) {
                throw new RuntimeException("Không đủ tồn kho cho sản phẩm: " + product.getName());
            }

            product.setQuantity(product.getQuantity() - item.getOrderedQuantity());
            productRepo.save(product);

            OrderDetail detail = OrderMapper.toOrderDetail(item, product);
            detail.setOrder(order);
            detailList.add(detail);

            BigDecimal lineTotal = item.getPrice().multiply(BigDecimal.valueOf(item.getOrderedQuantity()));
            totalAmount = totalAmount.add(lineTotal);
        }

        order.setDetails(detailList);
        order.setTotalAmount(totalAmount);
        orderRepo.save(order);

        goodIssueService.createFromOrder(order);

        return OrderMapper.toOrderResponseDTO(order);
    }

    @Override
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepo.findAll().stream()
                .map(OrderMapper::toOrderResponseDTO)
                .toList();
    }

    @Override
    public OrderResponseDTO getById(Integer orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return OrderMapper.toOrderResponseDTO(order);
    }

    @Override
    public void cancelOrder(Integer orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getStatus() == Order.OrderStatus.COMPLETED) {
            throw new RuntimeException("Không thể hủy đơn đã hoàn thành");
        }
        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepo.save(order);
    }

    @Override
    public boolean canApprove(Integer orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return order.getStatus() == Order.OrderStatus.PENDING;
    }

    @Transactional
    @Override
    public void updateOrderStatus(Integer orderId, Order.OrderStatus newStatus, String username) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new RuntimeException("Không thể cập nhật đơn đã hủy.");
        }

        if (order.getStatus() == Order.OrderStatus.COMPLETED) {
            throw new RuntimeException("Không thể cập nhật đơn đã hoàn thành.");
        }

        // Giả sử bạn kiểm tra quyền MANAGER bằng role của User
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (newStatus == Order.OrderStatus.COMPLETED && !user.getRoles().contains("ROLE_MANAGER")) {
            throw new RuntimeException("Chỉ MANAGER mới có thể duyệt hoàn thành đơn.");
        }

        order.setStatus(newStatus);
        orderRepo.save(order);
    }


    private String generateOrderCode() {
        long count = orderRepo.count() + 1;
        return String.format("ORD%05d", count);
    }
}
