package com.eewms.services.impl;

import com.eewms.dto.SupplierDTO;
import com.eewms.dto.SupplierMapper;
import com.eewms.entities.Supplier;
import com.eewms.exception.InventoryException;
import com.eewms.repository.SupplierRepository;
import com.eewms.services.ISupplierService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements ISupplierService {

    private final SupplierRepository supplierRepository;

    private String norm(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

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
    @Transactional
    public void create(SupplierDTO dto) {
        String name          = norm(dto.getName());
        String taxCode       = norm(dto.getTaxCode());
        String bankName      = norm(dto.getBankName());
        String bankAccount   = norm(dto.getBankAccount());
        String contactName   = norm(dto.getContactName());
        String contactMobile = norm(dto.getContactMobile());
        String address       = norm(dto.getAddress());
        String description   = norm(dto.getDescription());

        if (!StringUtils.hasText(name)) {
            throw new InventoryException("Tên nhà cung cấp không được để trống");
        }

        // Duplicate check
        if (supplierRepository.existsByNameIgnoreCase(name)) {
            throw new InventoryException("Tên nhà cung cấp đã tồn tại");
        }
        if (taxCode != null && supplierRepository.existsByTaxCode(taxCode)) {
            throw new InventoryException("Mã số thuế đã tồn tại");
        }
        if (bankAccount != null && supplierRepository.existsByBankAccount(bankAccount)) {
            throw new InventoryException("Số tài khoản đã tồn tại");
        }
        if (contactMobile != null && supplierRepository.existsByContactMobile(contactMobile)) {
            throw new InventoryException("Số điện thoại đã tồn tại");
        }

        Supplier supplier = Supplier.builder()
                .name(name)
                .taxCode(taxCode)
                .bankName(bankName)
                .bankAccount(bankAccount)
                .contactName(contactName)
                .contactMobile(contactMobile)
                .address(address)
                .status(dto.getStatus() == null ? Boolean.TRUE : dto.getStatus())
                .description(description)
                .build();

        supplierRepository.save(supplier);
    }

    @Override
    @Transactional
    public void update(SupplierDTO dto) {
        Supplier supplier = supplierRepository.findById(dto.getId())
                .orElseThrow(() -> new InventoryException("Không tìm thấy nhà cung cấp"));

        String name          = norm(dto.getName());
        String taxCode       = norm(dto.getTaxCode());
        String bankName      = norm(dto.getBankName());
        String bankAccount   = norm(dto.getBankAccount());
        String contactName   = norm(dto.getContactName());
        String contactMobile = norm(dto.getContactMobile());
        String address       = norm(dto.getAddress());
        String description   = norm(dto.getDescription());

        if (!StringUtils.hasText(name)) {
            throw new InventoryException("Tên nhà cung cấp không được để trống");
        }

        Long id = supplier.getId();
        if (supplierRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new InventoryException("Tên nhà cung cấp đã tồn tại");
        }
        if (taxCode != null && supplierRepository.existsByTaxCodeAndIdNot(taxCode, id)) {
            throw new InventoryException("Mã số thuế đã tồn tại");
        }
        if (bankAccount != null && supplierRepository.existsByBankAccountAndIdNot(bankAccount, id)) {
            throw new InventoryException("Số tài khoản đã tồn tại");
        }
        if (contactMobile != null && supplierRepository.existsByContactMobileAndIdNot(contactMobile, id)) {
            throw new InventoryException("Số điện thoại đã tồn tại");
        }

        supplier.setName(name);
        supplier.setTaxCode(taxCode);
        supplier.setBankName(bankName);
        supplier.setBankAccount(bankAccount);
        supplier.setContactName(contactName);
        supplier.setContactMobile(contactMobile);
        supplier.setAddress(address);
        supplier.setDescription(description);

        if (dto.getStatus() != null) {
            supplier.setStatus(dto.getStatus());
        }

        supplierRepository.save(supplier);
    }

    @Override
    public void toggleStatus(Long id) {
        Optional<Supplier> optional = supplierRepository.findById(id);
        optional.ifPresent(supplier -> {
            supplier.setStatus(!Boolean.TRUE.equals(supplier.getStatus()));
            supplierRepository.save(supplier);
        });
    }

    @Override
    public boolean existsByTaxCode(String taxCode) {
        return supplierRepository.existsByTaxCode(taxCode);
    }

    @Override
    public SupplierDTO findByTaxCode(String taxCode) {
        return supplierRepository.findByTaxCode(taxCode)
                .map(SupplierMapper::toDTO)
                .orElse(null);
    }


    @Override
    public Page<SupplierDTO> searchSuppliers(int page, String keyword) {
        Pageable pageable = PageRequest.of(page, 10, Sort.by("id").ascending());
        Page<Supplier> pageResult = supplierRepository
                .findByNameContainingIgnoreCaseOrTaxCodeContainingIgnoreCase(keyword, keyword, pageable);
        return pageResult.map(SupplierMapper::toDTO);
    }

    @Override
    public boolean existsByNameIgnoreCase(String name) {
        return supplierRepository.existsByNameIgnoreCase(name);
    }

    @Override
    public boolean existsByBankAccount(String bankAccount) {
        return supplierRepository.existsByBankAccount(bankAccount);
    }

    @Override
    public boolean existsByContactMobile(String contactMobile) {
        return supplierRepository.existsByContactMobile(contactMobile);
    }

    @Override
    public boolean existsByNameIgnoreCaseAndIdNot(String name, Long id) {
        return supplierRepository.existsByNameIgnoreCaseAndIdNot(name, id);
    }

    @Override
    public boolean existsByTaxCodeAndIdNot(String taxCode, Long id) {
        return supplierRepository.existsByTaxCodeAndIdNot(taxCode, id);
    }

    @Override
    public boolean existsByBankAccountAndIdNot(String bankAccount, Long id) {
        return supplierRepository.existsByBankAccountAndIdNot(bankAccount, id);
    }

    @Override
    public boolean existsByContactMobileAndIdNot(String contactMobile, Long id) {
        return supplierRepository.existsByContactMobileAndIdNot(contactMobile, id);
    }

}
