package com.eewms.services.impl;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.eewms.repository.CustomerRepository;
import com.eewms.entities.Customer;
import com.eewms.dto.CustomerDTO;
import com.eewms.dto.CustomerMapper;
import com.eewms.services.ICustomerService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements ICustomerService {
    private final CustomerRepository repo;
    private final CustomerMapper mapper;

    // ===== Helpers: normalize & duplicate checks =====
    private String collapseSpaces(String s) {
        if (s == null) return null;
        // trim 2 đầu và gộp khoảng trắng ở giữa
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private String titleCaseWords(String s) {
        s = collapseSpaces(s);
        if (s == null) return null;
        String[] parts = s.toLowerCase().split(" ");
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                char first = Character.toUpperCase(parts[i].charAt(0));
                String rest = parts[i].length() > 1 ? parts[i].substring(1) : "";
                parts[i] = first + rest;
            }
        }
        return String.join(" ", parts);
    }

    private void normalize(CustomerDTO dto) {
        // Họ tên: bắt buộc => title-case
        dto.setFullName(titleCaseWords(dto.getFullName()));
        // Các field khác: trim về null
        dto.setEmail(collapseSpaces(dto.getEmail()));
        dto.setPhone(collapseSpaces(dto.getPhone()));
        dto.setAddress(collapseSpaces(dto.getAddress()));
        dto.setTaxCode(collapseSpaces(dto.getTaxCode()));
        dto.setBankName(collapseSpaces(dto.getBankName()));
        if (dto.getStatus() == null) {
            dto.setStatus(Customer.CustomerStatus.ACTIVE);
        }
    }

    private void ensureEmailNotDuplicate(String email, Long selfId) {
        if (!StringUtils.hasText(email)) return; // cho phép trống
        boolean exists = (selfId == null)
                ? repo.existsByEmailIgnoreCase(email)
                : repo.existsByEmailIgnoreCaseAndIdNot(email, selfId);
        if (exists) {
            throw new IllegalArgumentException("Email đã tồn tại");
        }
    }

    private void ensurePhoneNotDuplicate(String phone, Long selfId) {
        if (!StringUtils.hasText(phone)) return; // cho phép trống
        boolean exists = (selfId == null)
                ? repo.existsByPhone(phone)
                : repo.existsByPhoneAndIdNot(phone, selfId);
        if (exists) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại");
        }
    }

    @Override
    public CustomerDTO create(CustomerDTO dto) {
        // 1) normalize dữ liệu
        normalize(dto);
        // 2) check duplicate email
        ensureEmailNotDuplicate(dto.getEmail(), null);
        ensurePhoneNotDuplicate(dto.getPhone(), null);

        // 3) map & save
        Customer entity = mapper.toEntity(dto);
        Customer saved = repo.save(entity);
        return mapper.toDTO(saved);
    }

    @Override
    public CustomerDTO update(CustomerDTO dto) {
        Customer entity = repo.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng với id = " + dto.getId()));

        // 1) normalize
        normalize(dto);
        // 2) check duplicate email (loại trừ chính nó)
        ensureEmailNotDuplicate(dto.getEmail(), dto.getId());
        ensurePhoneNotDuplicate(dto.getPhone(), dto.getId());

        // 3) cập nhật entity từ dto
        entity.setFullName(dto.getFullName());
        entity.setAddress(dto.getAddress());
        entity.setTaxCode(dto.getTaxCode());
        entity.setBankName(dto.getBankName());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }

        Customer updated = repo.save(entity);
        return mapper.toDTO(updated);
    }

    @Override
    public CustomerDTO getById(Long id) {
        return repo.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng với id = " + id));
    }

    @Override
    public List<CustomerDTO> findAll() {
        return repo.findAll()
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) {
        repo.deleteById(id);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Customer.CustomerStatus status) {
        Customer customer = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng với id = " + id));
        customer.setStatus(status);
        repo.save(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDTO> searchByKeyword(String keyword) {
        return repo.searchByKeyword(keyword).stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

}
