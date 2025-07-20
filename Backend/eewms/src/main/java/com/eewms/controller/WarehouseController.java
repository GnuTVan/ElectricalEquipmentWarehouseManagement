package com.eewms.controller;

import com.eewms.entities.Warehouse;
import com.eewms.services.IWarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final IWarehouseService warehouseService;

    // Hiển thị danh sách kho
    @GetMapping
    public String listWarehouses(Model model) {
        model.addAttribute("warehouses", warehouseService.getAll());
        model.addAttribute("warehouse", new Warehouse()); // dùng cho form thêm
        return "warehouse-list";
    }

    // Thêm kho mới
    @PostMapping
    public String createWarehouse(@ModelAttribute @Valid Warehouse warehouse) {
        warehouseService.save(warehouse);
        return "redirect:/admin/warehouses";
    }

    // Bật / Tắt kho
    @PostMapping("/toggle/{id}")
    public String toggleStatus(@PathVariable Long id) {
        warehouseService.toggleStatus(id);
        return "redirect:/admin/warehouses";
    }

    // Cập nhật kho (sửa)
    @PostMapping("/update")
    public String updateWarehouse(@ModelAttribute Warehouse warehouse) {
        warehouseService.save(warehouse);
        return "redirect:/admin/warehouses";
    }
}