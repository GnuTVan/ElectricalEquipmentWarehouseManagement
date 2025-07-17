package com.eewms.services;

import com.eewms.constant.SettingType;
import com.eewms.dto.SettingDTO;
import com.eewms.entities.Product;
import com.eewms.entities.Setting;
import com.eewms.exception.InventoryException;

import java.util.List;

public interface ISettingServices {
    SettingDTO create(SettingDTO dto) throws InventoryException;
    SettingDTO update(Integer id, SettingDTO dto) throws InventoryException;
    void delete(Integer id) throws InventoryException;
    SettingDTO getById(Integer id) throws InventoryException;
    List<SettingDTO> getAll();
    List<SettingDTO> getByType(SettingType type);

    //toggle status
    void updateStatus(Integer id, Setting.SettingStatus status) throws InventoryException;

    //lọc theo trạng thái active của setting
    List<Setting> findByTypeAndActive(SettingType type);
}
