package com.eewms.services.impl;

import com.eewms.dto.WarehouseDTO;
import com.eewms.entities.User;
import com.eewms.entities.Warehouse;
import com.eewms.entities.WarehouseStaff;
import com.eewms.repository.UserRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.WarehouseStaffRepository;
import com.eewms.services.IWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WarehouseServiceImpl implements IWarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseStaffRepository warehouseStaffRepository;
    private final UserRepository userRepository;

    @Override
    public List<Warehouse> getAll() {
        return warehouseRepository.findAll();
    }

    @Override
    public Warehouse getById(Integer id) {
        return warehouseRepository.findById(id).orElseThrow();
    }

    @Override
    public Warehouse save(WarehouseDTO dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        String description = dto.getDescription() == null ? null : dto.getDescription().trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Tên kho là bắt buộc");
        }

        if (dto.getId() == null) {
            // CREATE
            if (warehouseRepository.existsByNameIgnoreCase(name)) {
                throw new IllegalArgumentException("Tên kho đã tồn tại: " + name);
            }
            Warehouse w = new Warehouse();
            w.setName(name);
            w.setDescription(description);
            w.setStatus(dto.getStatus() == null ? true : dto.getStatus());
            return warehouseRepository.save(w);
        } else {
            // UPDATE
            if (warehouseRepository.existsByNameIgnoreCaseAndIdNot(name, dto.getId())) {
                throw new IllegalArgumentException("Tên kho đã tồn tại: " + name);
            }
            Warehouse existing = getById(dto.getId());
            existing.setName(name);
            existing.setDescription(description);
            if (dto.getStatus() != null) {
                existing.setStatus(dto.getStatus());
            }
            return warehouseRepository.save(existing);
        }
    }

    @Override
    public void toggleStatus(Integer id) {
        Warehouse warehouse = getById(id);
        warehouse.setStatus(!Boolean.TRUE.equals(warehouse.getStatus()));
        warehouseRepository.save(warehouse);
    }

    // ---------- Supervisor ----------
    @Override
    public void assignSupervisor(Integer warehouseId, Long userId) {
        Warehouse wh = getById(warehouseId);
        User user = userRepository.findById(userId).orElseThrow();
        wh.setSupervisor(user);
        warehouseRepository.save(wh);
    }

    @Override
    public void clearSupervisor(Integer warehouseId) {
        Warehouse wh = getById(warehouseId);
        wh.setSupervisor(null);
        warehouseRepository.save(wh);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getSupervisorId(Integer warehouseId) {
        Warehouse wh = findWarehouseOrThrow(warehouseId);
        User sup = wh.getSupervisor();
        return (sup != null) ? sup.getId() : null;
    }

    // ---------- Staff membership ----------
    @Override
    public List<Integer> listStaffIds(Integer warehouseId) {
        return warehouseStaffRepository.findByWarehouse_Id(warehouseId)
                .stream().map(ws -> Math.toIntExact(ws.getUser().getId())).toList();
    }

    @Override
    public void addStaff(Integer warehouseId, Long userId) {
        if (warehouseStaffRepository.existsByWarehouse_IdAndUser_Id(warehouseId, userId)) return;

        Warehouse wh = getById(warehouseId);
        User user = userRepository.findById(userId).orElseThrow();

        WarehouseStaff ws = WarehouseStaff.builder()
                .warehouse(wh)
                .user(user)
                .assignedAt(LocalDateTime.now())
                .build();

        warehouseStaffRepository.save(ws);
    }

    @Override
    public void removeStaff(Integer warehouseId, Long userId) {
        warehouseStaffRepository.deleteByWarehouse_IdAndUser_Id(warehouseId, userId);
    }

    // Tìm kho theo id, không có thì throw
    private Warehouse findWarehouseOrThrow(Integer id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho id=" + id));
    }

    // Tìm user theo id (Long), không có thì throw
    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user id=" + id));
    }

    @Override
    public Long findPrimaryWarehouseIdByUser(Long userId) {
        if (userId == null) return null;

        // Ưu tiên: user là Staff của kho nào
        for (Warehouse w : getAll()) {
            List<Integer> staffIds = listStaffIds(w.getId());
            if (staffIds != null && staffIds.contains(userId.intValue())) {
                return w.getId().longValue();
            }
        }
        // Fallback: user là Supervisor của kho nào
        for (Warehouse w : getAll()) {
            Long sup = getSupervisorId(w.getId());
            if (sup != null && sup.equals(userId)) {
                return w.getId().longValue();
            }
        }
        return null;
    }

    @Override
    public Warehouse findWarehouseByUser(Long userId) {
        if (userId == null) return null;

        // Ưu tiên: staff membership
        for (Warehouse w : getAll()) {
            var staffIds = listStaffIds(w.getId());
            if (staffIds != null && staffIds.contains(userId.intValue())) {
                return w;
            }
        }
        // Fallback: supervisor
        for (Warehouse w : getAll()) {
            Long supId = getSupervisorId(w.getId());
            if (supId != null && supId.equals(userId)) {
                return w;
            }
        }
        return null;
    }
}
