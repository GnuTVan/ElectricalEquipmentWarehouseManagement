package com.eewms.services.impl;

import com.eewms.constant.SettingType;
import com.eewms.dto.SettingDTO;
import com.eewms.entities.Setting;
import com.eewms.exception.InventoryException;
import com.eewms.repository.SettingRepository;
import com.eewms.services.ISettingServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingServicesImpl implements ISettingServices {
    private final SettingRepository settingRepository;

    // ===== Helpers normalize =====
    private String collapseSpaces(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("\\s+", " ");
    }

    private String titleCase(String s) {
        if (s == null || s.isBlank()) return s;
        String[] parts = s.toLowerCase(Locale.ROOT).split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            String first = p.substring(0, 1).toUpperCase(Locale.ROOT);
            String rest = p.substring(1);
            sb.append(first).append(rest).append(' ');
        }
        return sb.toString().trim();
    }

    private void normalize(SettingDTO dto) {
        dto.setName(titleCase(collapseSpaces(dto.getName())));
        dto.setDescription(collapseSpaces(dto.getDescription()));
        if (dto.getStatus() == null) {
            dto.setStatus(Setting.SettingStatus.ACTIVE);
        }
        if (dto.getType() != null) {
            dto.setType(SettingType.valueOf(dto.getType().name().toUpperCase()));
        }
    }

    @Override
    @Transactional
    public SettingDTO create(SettingDTO dto) throws InventoryException {
        // 1) Normalize trước khi kiểm tra
        normalize(dto);

        // 2) Check trùng theo (type, name) không phân biệt hoa thường
        if (settingRepository.existsByTypeAndNameIgnoreCase(dto.getType(), dto.getName())) {
            throw new InventoryException("Tên đã tồn tại trong nhóm " + dto.getType());
        }

        // 3) Lưu
        Setting s = Setting.builder()
                .name(dto.getName())
                .type(dto.getType())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .build();
        return toDto(settingRepository.save(s));
    }

    @Override
    @Transactional
    public SettingDTO update(Integer id, SettingDTO dto) throws InventoryException {
        Setting s = settingRepository.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy id=" + id));
        // 1) Normalize input
        normalize(dto);

        // 2) Check trùng theo (type, name) nhưng loại trừ chính record hiện tại
        if (settingRepository.existsByTypeAndNameIgnoreCaseAndIdNot(
                s.getType(), dto.getName(), id)) {
            throw new InventoryException("Tên đã tồn tại trong nhóm " + dto.getType());
        }

        // 3) Cập nhật field (nếu KHÔNG cho phép đổi type, hãy bỏ dòng setType)
        s.setName(dto.getName());
        s.setDescription(dto.getDescription());
        s.setStatus(dto.getStatus());
        return toDto(settingRepository.save(s));
    }

    @Override
    public void delete(Integer id) throws InventoryException {
        if (!settingRepository.existsById(id)) {
            throw new InventoryException("Không tìm thấy id=" + id);
        }
        settingRepository.deleteById(id);
    }

    @Override
    public SettingDTO getById(Integer id) throws InventoryException {
        Setting s = settingRepository.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy id=" + id));
        return toDto(s);
    }

    @Override
    public List<SettingDTO> getAll() {
        return settingRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<SettingDTO> getByType(SettingType type) {
        return settingRepository.findByType(type).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private SettingDTO toDto(Setting s) {
        return SettingDTO.builder()
                .id(s.getId())
                .name(s.getName())
                .type(s.getType())
                .description(s.getDescription())
                // enum sang DTO
                .status(s.getStatus())
                .build();
    }

    // Toggle trạng thái setting
    @Override
    @Transactional
    public void updateStatus(Integer id, Setting.SettingStatus status) throws InventoryException {
        Setting setting = settingRepository.findById(id)
                .orElseThrow(() -> new InventoryException("Sản phẩm không tồn tại"));

        setting.setStatus(status);
        settingRepository.saveAndFlush(setting);
    }

    // Lọc theo trạng thái active của setting
    @Override
    public List<Setting> findByTypeAndActive(SettingType type) {
        return settingRepository.findByTypeAndStatus(type, Setting.SettingStatus.ACTIVE);
    }
}
