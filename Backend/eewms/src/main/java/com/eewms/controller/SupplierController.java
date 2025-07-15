package com.eewms.controller;

import com.eewms.dto.SupplierDTO;
import com.eewms.services.ISupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final ISupplierService supplierService;

    // ✅ Hiển thị danh sách + form thêm mới
    @GetMapping
    public String showSupplierList(Model model) {
        model.addAttribute("suppliers", supplierService.findAll());
        model.addAttribute("newSupplier", new SupplierDTO()); // Cho form thêm mới
        model.addAttribute("editSupplier", new SupplierDTO()); // Cho modal sửa
        System.out.println("==> Danh sách NCC: " + supplierService.findAll().size());
        return "supplier-list";
    }

    // ✅ Xử lý tạo mới nhà cung cấp
    @PostMapping
    public String createSupplier(@Valid @ModelAttribute("newSupplier") SupplierDTO dto,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirect) {

        if (result.hasErrors()) {
            model.addAttribute("suppliers", supplierService.findAll());
            model.addAttribute("editSupplier", new SupplierDTO());
            model.addAttribute("newSupplier", dto); // ⚠️ PHẢI có dòng này
            return "supplier-list";
        }

        supplierService.create(dto);
        redirect.addFlashAttribute("message", "Thêm nhà cung cấp thành công");
        return "redirect:/admin/suppliers";
    }

    // ✅ Xử lý cập nhật nhà cung cấp
    @PostMapping("/update")
    public String updateSupplier(@Valid @ModelAttribute("editSupplier") SupplierDTO dto,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirect) {

        if (result.hasErrors()) {
            model.addAttribute("suppliers", supplierService.findAll());
            model.addAttribute("newSupplier", new SupplierDTO());
            return "supplier-list";
        }

        supplierService.update(dto);
        redirect.addFlashAttribute("message", "Cập nhật nhà cung cấp thành công");
        return "redirect:/admin/suppliers";
    }

    // ✅ Bật / Tắt trạng thái
    @PostMapping("/toggle/{id}")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirect) {
        supplierService.toggleStatus(id);
        redirect.addFlashAttribute("message", "Đã thay đổi trạng thái nhà cung cấp");
        return "redirect:/admin/suppliers";
    }
}