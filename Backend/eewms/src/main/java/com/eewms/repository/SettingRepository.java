package com.eewms.repository;

import com.eewms.dto.SettingDTO;
import com.eewms.entities.Setting;
import com.eewms.constant.SettingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettingRepository extends JpaRepository<Setting, Integer> {
    boolean existsByNameAndType(String name, SettingType type);
    List<Setting> findByType(SettingType type);


}
