package com.eewms.services.impl;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.eewms.repository.CustomerRepository;
import com.eewms.entities.Customer;
import com.eewms.dto.CustomerDTO;
import com.eewms.dto.CustomerMapper;
import com.eewms.services.ICustomerService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerServiceImpl implements ICustomerService {
    private final CustomerRepository repo;
    private final CustomerMapper mapper;

    @Override
    public CustomerDTO create(CustomerDTO dto) {
        System.out.println("DTO nhận vào: " + dto); // Debug DTO
        if (dto.getStatus() == null) {
            dto.setStatus(Customer.CustomerStatus.ACTIVE);
        }

        Customer entity = mapper.toEntity(dto);
        System.out.println("Entity sau toEntity: " + entity); // Debug entity

        Customer saved = repo.save(entity);
        return mapper.toDTO(saved);
    }

    @Override
    public CustomerDTO update(CustomerDTO dto) {
        Customer entity = repo.findById(dto.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng với id = " + dto.getId()));
        // cập nhật
        entity.setFullName(dto.getFullName());
        entity.setAddress(dto.getAddress());
        entity.setTaxCode(dto.getTaxCode());
        entity.setBankName(dto.getBankName());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
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
