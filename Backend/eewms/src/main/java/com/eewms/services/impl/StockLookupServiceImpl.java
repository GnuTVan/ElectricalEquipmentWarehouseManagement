package com.eewms.services.impl;

import com.eewms.dto.inventory.WarehouseStockRowDTO;
import com.eewms.entities.User;
import com.eewms.entities.Warehouse;
import com.eewms.entities.WarehouseStaff;
import com.eewms.repository.ProductWarehouseStockRepository;
import com.eewms.repository.WarehouseRepository;
import com.eewms.repository.WarehouseStaffRepository;
import com.eewms.services.IProductServices;
import com.eewms.services.IStockLookupService;
import com.eewms.services.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockLookupServiceImpl implements IStockLookupService {

    private final IUserService userService;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseStaffRepository warehouseStaffRepository;
    private final ProductWarehouseStockRepository pwsRepository;
    private final IProductServices productService;

    @Override
    public Integer resolveWarehouseIdForCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null) return null;
        User u = userService.findByUsername(username).orElse(null);
        if (u == null) return null;
        return resolveWarehouseIdForUser(u.getId());
    }

    @Override
    public Integer resolveWarehouseIdForUser(Long userId) {
        List<Warehouse> supWhs = warehouseRepository.findBySupervisor_Id(userId);
        if (supWhs != null && !supWhs.isEmpty()) {
            return supWhs.get(0).getId();
        }
        List<WarehouseStaff> staffLinks = warehouseStaffRepository.findByUser_Id(userId);
        if (staffLinks != null && !staffLinks.isEmpty()) {
            return staffLinks.get(0).getWarehouse().getId();
        }
        return null;
    }

    @Override
    public String resolveWarehouseNameForCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getName()
                : null;
        if (username == null) return null;
        User u = userService.findByUsername(username).orElse(null);
        if (u == null) return null;
        // Ưu tiên supervisor
        List<Warehouse> supWhs = warehouseRepository.findBySupervisor_Id(u.getId());
        if (supWhs != null && !supWhs.isEmpty()) return supWhs.get(0).getName();
        // Sau đó staff
        List<WarehouseStaff> staffLinks = warehouseStaffRepository.findByUser_Id(u.getId());
        if (staffLinks != null && !staffLinks.isEmpty()) return staffLinks.get(0).getWarehouse().getName();
        return null;
    }

    @Override
    public Map<Integer, Integer> getStockByProductAtWarehouse(Integer warehouseId) {
        if (warehouseId == null) return Collections.emptyMap();
        var page = pwsRepository.pageCatalogWithStockAtWarehouse(warehouseId, null, Pageable.unpaged());
        return page.getContent().stream()
                .collect(Collectors.toMap(
                        WarehouseStockRowDTO::getProductId,
                        dto -> {
                            Number q = dto.getQuantity();
                            return q == null ? 0 : q.intValue();      // chuẩn hoá về Integer
                        },
                        Integer::sum,                                 // nếu trùng productId thì cộng dồn
                        LinkedHashMap::new                            // giữ thứ tự (tuỳ chọn)
                ));

    }

    @Override
    public List<Map<String, Object>> buildProductListWithStock(Integer warehouseId) {
        Map<Integer, Integer> stockByPid = getStockByProductAtWarehouse(warehouseId);
        List<Map<String, Object>> products = new ArrayList<>();
        productService.getAllActiveProducts().forEach(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("listingPrice", p.getListingPrice());
            m.put("quantity", stockByPid.getOrDefault(p.getId(), 0));
            products.add(m);
        });
        return products;
    }
}
