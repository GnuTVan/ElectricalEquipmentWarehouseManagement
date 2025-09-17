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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    public String toggleStatus(@PathVariable Integer id) {
        warehouseService.toggleStatus(id);
        return "redirect:/admin/warehouses";
    }

    @GetMapping("/{id}/members")
    public String members(@PathVariable Integer id, Model model) {
        Warehouse wh = warehouseService.getById(id);
        model.addAttribute("warehouse", wh);
        model.addAttribute("supervisorId", warehouseService.getSupervisorId(id));
        model.addAttribute("staffIds", warehouseService.listStaffIds(id));
        return "warehouses/members";
    }

    @PostMapping("/{id}/members/supervisor")
    public String assignSupervisor(@PathVariable Integer id,
                                   @RequestParam(required = false) Long userId,
                                   RedirectAttributes ra) {
        if (userId == null) {
            warehouseService.clearSupervisor(id);
            ra.addFlashAttribute("message", "Đã bỏ quản lý kho.");
            ra.addFlashAttribute("messageType", "success");
        } else {
            warehouseService.assignSupervisor(id, userId);
            ra.addFlashAttribute("message", "Đã gán quản lý kho.");
            ra.addFlashAttribute("messageType", "success");
        }
        return "redirect:/admin/warehouses/" + id + "/members";
    }

    @PostMapping("/{id}/members/staff/add")
    public String addStaff(@PathVariable Integer id, @RequestParam Long userId, RedirectAttributes ra) {
        warehouseService.addStaff(id, userId);
        ra.addFlashAttribute("message", "Thêm nhân viên thành công.");
        ra.addFlashAttribute("messageType", "success");
        return "redirect:/admin/warehouses/" + id + "/members";
    }

    @PostMapping("/{id}/members/staff/remove")
    public String removeStaff(@PathVariable Integer id, @RequestParam Long userId, RedirectAttributes ra) {
        warehouseService.removeStaff(id, userId);
        ra.addFlashAttribute("message", "Gỡ nhân viên thành công.");
        ra.addFlashAttribute("messageType", "success");

        return "redirect:/admin/warehouses/" + id + "/members";
    }

}
