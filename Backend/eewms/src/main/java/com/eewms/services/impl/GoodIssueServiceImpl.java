package com.eewms.services.impl;

import com.eewms.dto.GoodIssueMapper;
import com.eewms.dto.GoodIssueNoteDTO;
import com.eewms.entities.*;
import com.eewms.repository.GoodIssueNoteRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.UserRepository;
import com.eewms.services.IGoodIssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GoodIssueServiceImpl implements IGoodIssueService {

    private final GoodIssueNoteRepository goodIssueRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Override
    public GoodIssueNote createFromOrder(SaleOrder order, String username) {
        // Tìm user đang đăng nhập
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        // Tạo phiếu xuất kho
        GoodIssueNote note = new GoodIssueNote();
        note.setGinCode(generateGINCode());
        note.setCustomer(order.getCustomer());
        note.setDescription("Phiếu xuất từ đơn hàng #" + order.getSoCode());
        note.setIssueDate(LocalDateTime.now());
        note.setCreatedBy(currentUser);
        note.setSaleOrder(order); // Gán đơn hàng gốc

        // Tạo chi tiết phiếu xuất và trừ kho
        List<GoodIssueDetail> details = order.getDetails().stream().map(orderDetail -> {
            Product product = orderDetail.getProduct();
            int orderedQty = orderDetail.getOrderedQuantity();

            if (product.getQuantity() < orderedQty) {
                throw new RuntimeException("Không đủ hàng trong kho cho sản phẩm: " + product.getName());
            }

            // Trừ kho
            product.setQuantity(product.getQuantity() - orderedQty);
            productRepository.save(product);

            // Tạo chi tiết xuất kho
            GoodIssueDetail detail = GoodIssueDetail.builder()
                    .product(product)
                    .price(orderDetail.getPrice())
                    .quantity(orderedQty)
                    .build();
            detail.setGoodIssueNote(note); // Gán ngược về phiếu xuất
            return detail;
        }).toList();

        // Tính tổng tiền
        BigDecimal total = details.stream()
                .map(d -> d.getPrice().multiply(BigDecimal.valueOf(d.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        note.setDetails(details);
        note.setTotalAmount(total);

        return goodIssueRepository.save(note);
    }

    @Override
    public GoodIssueNoteDTO getById(Long id) {
        GoodIssueNote gin = goodIssueRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu xuất kho"));
        return GoodIssueMapper.toNoteDTO(gin);
    }

    @Override
    public List<GoodIssueNoteDTO> getAllNotes() {
        List<GoodIssueNote> notes = goodIssueRepository.findAll();
        return notes.stream()
                .map(GoodIssueMapper::toNoteDTO)
                .toList();
    }

    private String generateGINCode() {
        long count = goodIssueRepository.count() + 1;
        return String.format("GIN%05d", count);
    }

    @Override
    public List<GoodIssueNoteDTO> filterReport(LocalDate fromDate, LocalDate toDate, Long customerId, Long userId) {
        return goodIssueRepository.findAll().stream()
                .filter(note -> fromDate == null || !note.getIssueDate().toLocalDate().isBefore(fromDate))
                .filter(note -> toDate == null || !note.getIssueDate().toLocalDate().isAfter(toDate))
                .filter(note -> customerId == null ||
                        (note.getCustomer() != null && note.getCustomer().getId().equals(customerId)))
                .filter(note -> userId == null ||
                        (note.getCreatedBy() != null && note.getCreatedBy().getId().equals(userId)))
                .map(GoodIssueMapper::toNoteDTO)
                .toList();
    }

}
