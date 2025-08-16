package com.eewms.controller;

import com.eewms.dto.WarehouseDTO;
import com.eewms.entities.Warehouse;
import com.eewms.services.IWarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final IWarehouseService warehouseService;

    // List + form
    @GetMapping
    public String listWarehouses(Model model, @ModelAttribute("form") WarehouseDTO form) {
        model.addAttribute("warehouses", warehouseService.getAll());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new WarehouseDTO());
        }
        // cờ mặc định
        if (!model.containsAttribute("hasValidationErrors")) {
            model.addAttribute("hasValidationErrors", false);
        }
        if (!model.containsAttribute("openCreateModal")) {
            model.addAttribute("openCreateModal", false);
        }
        return "warehouse-list";
    }

    // Tạo kho
    @PostMapping
    public String createWarehouse(@ModelAttribute("form") @Valid WarehouseDTO dto,
                                  BindingResult br,
                                  Model model) {
        if (br.hasErrors()) {
            model.addAttribute("warehouses", warehouseService.getAll());
            model.addAttribute("hasValidationErrors", true);
            model.addAttribute("openCreateModal", true);
            return "warehouse-list";
        }
        try {
            warehouseService.save(dto);
            return "redirect:/admin/warehouses";
        } catch (IllegalArgumentException ex) {
            br.rejectValue("name", "duplicate", ex.getMessage());
            model.addAttribute("warehouses", warehouseService.getAll());
            model.addAttribute("hasValidationErrors", true);
            model.addAttribute("openCreateModal", true);
            return "warehouse-list";
        }
    }

    // Cập nhật kho (Modal Sửa – nếu muốn auto open khi lỗi, ta sẽ thêm sau)
    @PostMapping("/update")
    public String updateWarehouse(@ModelAttribute("form") @Valid WarehouseDTO dto,
                                  BindingResult br,
                                  Model model) {
        if (br.hasErrors()) {
            model.addAttribute("warehouses", warehouseService.getAll());
            // Ở bước này chưa auto-open modal Sửa để tránh phức tạp front-end
            return "warehouse-list";
        }
        try {
            warehouseService.save(dto);
            return "redirect:/admin/warehouses";
        } catch (IllegalArgumentException ex) {
            br.rejectValue("name", "duplicate", ex.getMessage());
            model.addAttribute("warehouses", warehouseService.getAll());
            return "warehouse-list";
        }
    }

    // Bật/Tắt kho
    @PostMapping("/toggle/{id}")
    public String toggleStatus(@PathVariable Long id) {
        warehouseService.toggleStatus(id);
        return "redirect:/admin/warehouses";
    }
}
