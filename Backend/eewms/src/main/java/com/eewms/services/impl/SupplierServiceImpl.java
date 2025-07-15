package com.eewms.services.impl;

import com.eewms.dto.SupplierDTO;
import com.eewms.dto.SupplierMapper;
import com.eewms.entities.Supplier;
import com.eewms.repository.SupplierRepository;
import com.eewms.services.ISupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements ISupplierService {

    private final SupplierRepository supplierRepository;

    @Override
    public List<SupplierDTO> findAll() {
        return supplierRepository.findAll()
                .stream()
                .map(SupplierMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public SupplierDTO findById(Long id) {
        return supplierRepository.findById(id)
                .map(SupplierMapper::toDTO)
                .orElse(null);
    }

    @Override
    public void create(SupplierDTO dto) {
        Supplier supplier = SupplierMapper.toEntity(dto);
        supplierRepository.save(supplier);
    }

    @Override
    public void update(SupplierDTO dto) {
        Optional<Supplier> optionalSupplier = supplierRepository.findById(dto.getId());
        if (optionalSupplier.isPresent()) {
            Supplier supplier = optionalSupplier.get();
            // Cập nhật từng trường (có thể dùng lại mapper nếu muốn)
            supplier.setName(dto.getName());
            supplier.setTaxCode(dto.getTaxCode());
            supplier.setBankName(dto.getBankName());
            supplier.setBankAccount(dto.getBankAccount());
            supplier.setContactName(dto.getContactName());
            supplier.setContactMobile(dto.getContactMobile());
            supplier.setAddress(dto.getAddress());
            supplier.setStatus(dto.getStatus());
            supplier.setDescription(dto.getDescription());
            supplierRepository.save(supplier);
        }
    }

    @Override
    public void toggleStatus(Long id) {
        Optional<Supplier> optional = supplierRepository.findById(id);
        optional.ifPresent(supplier -> {
            supplier.setStatus(!Boolean.TRUE.equals(supplier.getStatus()));
            supplierRepository.save(supplier);
        });
    }
}
