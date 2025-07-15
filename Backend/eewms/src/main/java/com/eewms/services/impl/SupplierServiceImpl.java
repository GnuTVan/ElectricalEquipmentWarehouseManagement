package com.eewms.services.impl;

import com.eewms.dto.SupplierDTO;
import com.eewms.services.ISupplierService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SupplierServiceImpl implements ISupplierService {

    @Override
    public List<SupplierDTO> findAll() {
        return new ArrayList<>(); // Tạm chưa có dữ liệu
    }

    @Override
    public void createSupplier(SupplierDTO dto) {
        // Tạm bỏ qua logic lưu
    }
}
