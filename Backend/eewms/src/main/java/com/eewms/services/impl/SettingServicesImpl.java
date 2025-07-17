package com.eewms.services.impl;

import com.eewms.constant.SettingType;
import com.eewms.dto.SettingDTO;
import com.eewms.entities.Product;
import com.eewms.entities.Setting;
import com.eewms.exception.InventoryException;
import com.eewms.repository.SettingRepository;
import com.eewms.services.ISettingServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SettingServicesImpl implements ISettingServices {
    private final SettingRepository settingRepository;

    @Override
    public SettingDTO create(SettingDTO dto) throws InventoryException {
        if (settingRepository.existsByNameAndType(dto.getName(), dto.getType())) {
            throw new InventoryException("Tên đã tồn tại trong nhóm " + dto.getType());
        }
        Setting s = Setting.builder()
                .name(dto.getName())
                .type(dto.getType())
                .description(dto.getDescription())
                .status(dto.getStatus())
                .build();
        Setting saved = settingRepository.save(s);
        return toDto(saved);
    }

    @Override
    public SettingDTO update(Integer id, SettingDTO dto) throws InventoryException {
        Setting s = settingRepository.findById(id)
                .orElseThrow(() -> new InventoryException("Không tìm thấy id=" + id));
        if (!s.getType().equals(dto.getType()) || !s.getName().equals(dto.getName())) {
            if (settingRepository.existsByNameAndType(dto.getName(), dto.getType())) {
                throw new InventoryException("Tên đã tồn tại trong nhóm " + dto.getType());
            }
        }
        s.setName(dto.getName());
        s.setType(dto.getType());
        s.setDescription(dto.getDescription());
        s.setStatus(dto.getStatus());
        Setting updated = settingRepository.save(s);
        return toDto(updated);
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
