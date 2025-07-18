package com.eewms.controller;

import com.eewms.entities.Setting;
import com.eewms.constant.SettingType;
import com.eewms.dto.SettingDTO;
import com.eewms.exception.InventoryException;
import com.eewms.services.ISettingServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingController {
    private final ISettingServices settingService;

    @ModelAttribute("types")
    public SettingType[] types() {
        return SettingType.values();
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("settings", List.of());
        model.addAttribute("settingType", null);
        model.addAttribute("settingForm", new SettingDTO());
        return "settings/list";
    }


    @GetMapping("/{type}")
    public String listByType(@PathVariable SettingType type, Model model) {
        model.addAttribute("settingType", type);
        model.addAttribute("settings", settingService.getByType(type));
        model.addAttribute("settingForm", SettingDTO.builder()
                .type(type)
                .build());
        return "settings/list";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("settingForm") @Valid SettingDTO dto,
                       BindingResult result,
                       Model model,
                       RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("settingType", dto.getType());
            model.addAttribute("settings", settingService.getByType(dto.getType()));
            return "settings/list";
        }

        try {
            settingService.create(dto);
            ra.addFlashAttribute("success", "Lưu thành công");
        } catch (InventoryException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/settings/" + dto.getType();
    }

    @GetMapping("/edit/{type}/{id}")
    public String edit(@PathVariable SettingType type,
                       @PathVariable Integer id,
                       Model model) throws InventoryException {
        // 1. Load lại danh sách để vẽ table
        model.addAttribute("settingType", type);
        model.addAttribute("settings", settingService.getByType(type));
        // 2. Load bản ghi cần sửa vào form
        SettingDTO dto = settingService.getById(id);
        model.addAttribute("settingForm", dto);
        return "settings/list";
    }

    @PostMapping("/update/{type}/{id}")
    public String update(@PathVariable SettingType type,
                         @PathVariable Integer id,
                         @ModelAttribute("settingForm") @Valid SettingDTO dto,
                         BindingResult result,
                         Model model,
                         RedirectAttributes ra) {
        if (result.hasErrors()) {
            model.addAttribute("settingType", type);
            model.addAttribute("settings", settingService.getByType(type));
            return "settings/list";
        }

        try {
            settingService.update(id, dto);
            ra.addFlashAttribute("success", "Cập nhật thành công");
        } catch (InventoryException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/settings/" + type;
    }

    @GetMapping("/delete/{type}/{id}")
    public String delete(@PathVariable SettingType type,
                         @PathVariable Integer id,
                         RedirectAttributes ra) {
        try {
            settingService.delete(id);
            ra.addFlashAttribute("success", "Xóa thành công");
        } catch (InventoryException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/settings/" + type;
    }

    @PostMapping("/{id}/status")
    @ResponseBody
    public String updateStatus(@PathVariable Integer id, @RequestBody Map<String, String> payload) {
        try {
            String statusValue = payload.get("status");
            settingService.updateStatus(id, Setting.SettingStatus.valueOf(statusValue));
            return "OK";
        } catch (Exception ex) {
            return "ERROR: " + ex.getMessage();
        }
    }
}
