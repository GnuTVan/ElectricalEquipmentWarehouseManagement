package com.eewms.controller;

import com.eewms.dto.ProductFormDTO;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.constant.SettingType;
import com.eewms.dto.UserDTO;
import com.eewms.dto.UserMapper;
import com.eewms.entities.User;
import com.eewms.exception.InventoryException;
import com.eewms.services.IProductServices;
import com.eewms.services.ISettingServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping({"/products", "/product-list"})
@RequiredArgsConstructor
public class ProductController {
    private final IProductServices productService;
    private final ISettingServices settingService;
    @GetMapping
    public String list(Model model) throws InventoryException {
        model.addAttribute("products", productService.getAll());
        model.addAttribute("productDTO", new ProductFormDTO());
        model.addAttribute("units",      settingService.getByType(SettingType.UNIT));
        model.addAttribute("brands",     settingService.getByType(SettingType.BRAND));
        model.addAttribute("categories", settingService.getByType(SettingType.CATEGORY));
        return "product-list";
    }

    // xử lý submit modal form
    @PostMapping
    public String create(
            @ModelAttribute("productDTO") ProductFormDTO dto,
            BindingResult br,
            Model model,
            RedirectAttributes ra) {
        if (br.hasErrors()) {
            // repopulate dropdowns
            model.addAttribute("units",      settingService.getByType(SettingType.UNIT));
            model.addAttribute("brands",     settingService.getByType(SettingType.BRAND));
            model.addAttribute("categories", settingService.getByType(SettingType.CATEGORY));
            return "product-list";
        }
        try {
            productService.create(dto);
            ra.addFlashAttribute("success", "Thêm sản phẩm thành công");
        } catch (InventoryException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/products";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute("productDTO") ProductFormDTO dto,
            BindingResult br,
            Model model,
            RedirectAttributes ra) {

        if (br.hasErrors()) {
            model.addAttribute("units",      settingService.getByType(SettingType.UNIT));
            model.addAttribute("brands",     settingService.getByType(SettingType.BRAND));
            model.addAttribute("categories", settingService.getByType(SettingType.CATEGORY));
            return "product-list";
        }

        try {
            // Nếu id == null thì tạo mới, ngược lại update
            if (dto.getId() == null) {
                productService.create(dto);
                ra.addFlashAttribute("success", "Thêm thành công");
            } else {
                productService.update(dto.getId(), dto);
                ra.addFlashAttribute("success", "Cập nhật thành công");
            }
        } catch (InventoryException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            model.addAttribute("units",      settingService.getByType(SettingType.UNIT));
            model.addAttribute("brands",     settingService.getByType(SettingType.BRAND));
            model.addAttribute("categories", settingService.getByType(SettingType.CATEGORY));
            return "product-list";
        }
        return "redirect:/products";
    }

    // Xử lý cập nhật
    @PostMapping("/update/{id}")
    public String updateProduct(
            @PathVariable Integer id,
            @ModelAttribute("productDTO") ProductFormDTO dto,
            BindingResult br,
            Model model,
            RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("units",      settingService.getByType(SettingType.UNIT));
            model.addAttribute("brands",     settingService.getByType(SettingType.BRAND));
            model.addAttribute("categories", settingService.getByType(SettingType.CATEGORY));
            return "product-list";
        }
        try {
            productService.update(id, dto);
            ra.addFlashAttribute("success", "Cập nhật sản phẩm thành công");
        } catch (InventoryException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/products";
    }
    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        try {
            ProductDetailsDTO dto = productService.getById(id);
            model.addAttribute("product", dto);
            return "products/detail";
        } catch (InventoryException ex) {
            return "redirect:/products";
        }
    }
}
