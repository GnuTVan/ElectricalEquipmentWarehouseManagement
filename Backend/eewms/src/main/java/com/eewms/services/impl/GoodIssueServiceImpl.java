package com.eewms.services.impl;

import com.eewms.entities.*;
import com.eewms.repository.GoodIssueNoteRepository;
//import com.eewms.repository.GoodIssueDetailRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IGoodIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoodIssueServiceImpl implements IGoodIssueService {

    private final GoodIssueNoteRepository goodIssueRepository;
    private final UserRepository userRepository;

    @Override
    public GoodIssueNote createFromOrder(SaleOrder order) {
        // Lấy user hiện tại
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Tạo phiếu xuất
        GoodIssueNote note = new GoodIssueNote();
        note.setGinCode(generateGINCode());
        note.setCustomer(order.getCustomer());
        note.setDescription("Phiếu xuất từ đơn hàng #" + order.getSoCode());
        note.setIssueDate(LocalDateTime.now());
        note.setCreatedBy(currentUser);

        // Tạo danh sách chi tiết
        List<GoodIssueDetail> details = order.getDetails().stream().map(orderDetail -> {
            return GoodIssueDetail.builder()
                    .goodIssueNote(note)
                    .product(orderDetail.getProduct())
                    .price(orderDetail.getPrice())
                    .quantity(orderDetail.getOrderedQuantity())
                    .build();
        }).toList();

        note.setDetails(details);
        BigDecimal total = details.stream()
                .map(d -> d.getPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        note.setTotalAmount(total);

        return goodIssueRepository.save(note);
    }

    private String generateGINCode() {
        long count = goodIssueRepository.count() + 1;
        return String.format("GIN%05d", count);
    }
}

