package com.eewms.controller;

import com.eewms.constant.SettingType;
import com.eewms.dto.SettingDTO;
import com.eewms.exception.InventoryException;
import com.eewms.services.ISettingServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

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
    public String save(@ModelAttribute("settingForm") SettingDTO dto,
                       RedirectAttributes ra) {
        try {
            settingService.create(dto);
            ra.addFlashAttribute("success", "Lưu thành công");
        } catch (InventoryException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/settings/" + dto.getType();
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
}
