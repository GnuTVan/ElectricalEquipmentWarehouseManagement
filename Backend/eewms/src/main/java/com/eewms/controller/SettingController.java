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

        // Nếu validate DTO lỗi -> giữ lại trang + bật modal thêm mới
        if (result.hasErrors()) {
            model.addAttribute("hasFormError", true);             // <-- để JS auto mở modal
            model.addAttribute("settingType", dto.getType());
            model.addAttribute("settings", settingService.getByType(dto.getType()));
            return "settings/list";
        }

        try {
            settingService.create(dto);
            ra.addFlashAttribute("message", "Thêm " + dto.getType() + " thành công, Tên: " + dto.getName());
            ra.addFlashAttribute("messageType", "success");
            return "redirect:/settings/" + dto.getType();
        } catch (InventoryException ex) {
            // Map lỗi duplicate vào FIELD 'name' để hiển thị ngay dưới input
            result.rejectValue("name", "duplicate", ex.getMessage());
            model.addAttribute("hasFormError", true);             // <-- để JS auto mở modal
            model.addAttribute("settingType", dto.getType());
            model.addAttribute("settings", settingService.getByType(dto.getType()));
            return "settings/list";
        }
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

        // Bảo đảm DTO có id và type khớp path (phòng trường hợp form thiếu/changed)
        dto.setId(id);
        dto.setType(type);

        // Nếu validate DTO lỗi -> giữ lại trang + bật modal Sửa
        if (result.hasErrors()) {
            model.addAttribute("hasFormError", true);          // <-- để JS auto mở modal
            model.addAttribute("settingType", type);
            model.addAttribute("settings", settingService.getByType(type));
            return "settings/list";
        }

        try {
            settingService.update(id, dto);
            ra.addFlashAttribute("message", "Cập nhật " + dto.getType() + " thành công, Tên: " + dto.getName());
            ra.addFlashAttribute("messageType", "success");
            return "redirect:/settings/" + type;
        } catch (InventoryException ex) {
            // Map lỗi duplicate vào FIELD 'name'
            result.rejectValue("name", "duplicate", ex.getMessage());
            model.addAttribute("hasFormError", true);          // <-- để JS auto mở modal
            model.addAttribute("settingType", type);
            model.addAttribute("settings", settingService.getByType(type));
            return "settings/list";
        }
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
