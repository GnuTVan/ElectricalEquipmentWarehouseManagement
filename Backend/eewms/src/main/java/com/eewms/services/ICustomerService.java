package com.eewms.services;
import com.eewms.dto.CustomerDTO;
import java.util.List;

public interface ICustomerService {
    CustomerDTO create(CustomerDTO dto);
    CustomerDTO update(CustomerDTO dto);
    CustomerDTO getById(Long id);
    List<CustomerDTO> findAll();
    void delete(Long id);
    List<CustomerDTO> searchByKeyword(String keyword); // THÊM
}