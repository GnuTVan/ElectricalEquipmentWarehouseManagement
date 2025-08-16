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

    @GetMapping
    public String listWarehouses(Model model,
                                 @ModelAttribute("form") WarehouseDTO form) {
        model.addAttribute("warehouses", warehouseService.getAll());
        // nếu không có "form" từ redirect thì tạo mới để binding form add
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new WarehouseDTO());
        }
        return "warehouse-list";
    }

    // Thêm kho mới
    @PostMapping
    public String createWarehouse(@ModelAttribute("form") @Valid WarehouseDTO dto,
                                  BindingResult br,
                                  Model model) {
        if (br.hasErrors()) {
            model.addAttribute("warehouses", warehouseService.getAll());
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

    // Cập nhật kho (sửa)
    @PostMapping("/update")
    public String updateWarehouse(@ModelAttribute("form") @Valid WarehouseDTO dto,
                                  BindingResult br,
                                  Model model) {
        if (br.hasErrors()) {
            model.addAttribute("warehouses", warehouseService.getAll());
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

    // Bật / Tắt kho
    @PostMapping("/toggle/{id}")
    public String toggleStatus(@PathVariable Long id) {
        warehouseService.toggleStatus(id);
        return "redirect:/admin/warehouses";
    }
}
