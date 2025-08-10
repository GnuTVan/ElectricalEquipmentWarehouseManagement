package com.eewms.services;

import com.eewms.dto.ComboDTO;
import com.eewms.dto.ComboDetailDTO;
import com.eewms.dto.ComboRequest;
import com.eewms.entities.Combo;

import java.util.List;

public interface IComboService {
    // CRUD cốt lõi
    ComboDTO create(ComboRequest req);
    ComboDTO update(Long id, ComboRequest req);
    ComboDTO getById(Long id);

    // Listing / search cho trang list
    List<ComboDTO> getAll();
    List<ComboDTO> searchByKeyword(String keyword);

    // Đổi trạng thái ACTIVE/INACTIVE
    void updateStatus(Long id, Combo.ComboStatus status);

    // Cho SaleOrder: bung nhiều combo thành list product đã gộp
    List<ComboDetailDTO> expandAsComboDetailDTO(List<Long> comboIds);
    List<ComboDTO> getAllActive();
}