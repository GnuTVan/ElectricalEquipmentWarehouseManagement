package com.eewms.controller;

import com.eewms.dto.ComboDTO;
import com.eewms.dto.ComboDetailDTO;
import com.eewms.dto.ComboRequest;
import com.eewms.entities.Combo;
import com.eewms.services.IComboService;
import com.eewms.services.IProductServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping({"/combos", "/combo-list"})
@RequiredArgsConstructor
public class ComboController {

    private final IComboService comboService;
    private final IProductServices productService;

    /* ===== LIST ===== */
    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       Model model) {
        if (keyword != null && !keyword.isBlank()) {
            model.addAttribute("combos", comboService.searchByKeyword(keyword));
        } else {
            model.addAttribute("combos", comboService.getAll());
        }
        model.addAttribute("keyword", keyword);
        return "combo/combo-list";
    }

    /* ===== SHOW CREATE FORM ===== */
    @GetMapping("/create")
    public String showCreateForm(Model model) {
        if (!model.containsAttribute("comboForm")) {
            model.addAttribute("comboForm", new ComboRequest());
        }
        model.addAttribute("products", productService.getAllActiveProducts());
        return "combo/combo-create";
    }

    /* ===== CREATE ===== */
    @PostMapping("/create")
    public String create(@ModelAttribute("comboForm") @Valid ComboRequest req,
                         BindingResult br,
                         Model model,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            // Lấy thông báo lỗi đầu tiên
            String errMsg = br.getFieldErrors().stream()
                    .map(e -> e.getDefaultMessage())
                    .findFirst()
                    .orElse("Vui lòng kiểm tra lại thông tin");
            ra.addFlashAttribute("message", errMsg);
            ra.addFlashAttribute("messageType", "error");
            ra.addFlashAttribute("comboForm", req);
            return "redirect:/combos/create";
        }

        try {
            comboService.create(req);
            ra.addFlashAttribute("message", "Tạo combo thành công.");
            ra.addFlashAttribute("messageType", "success");
            return "redirect:/combos";
        } catch (Exception e) {
            ra.addFlashAttribute("message", "Lỗi khi tạo combo: " + e.getMessage());
            ra.addFlashAttribute("messageType", "error");
            ra.addFlashAttribute("comboForm", req);
            return "redirect:/combos/create";
        }
    }


    /* ===== UPDATE (/{id}/edit) ===== */
    @PostMapping("/{id:\\d+}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("comboForm") @Valid ComboRequest req,
                         BindingResult br,
                         RedirectAttributes ra) {
        if (br.hasErrors()) {
            String firstError = br.getFieldErrors().stream()
                    .findFirst()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .orElse("Dữ liệu không hợp lệ");
            ra.addFlashAttribute("message", firstError);
            ra.addFlashAttribute("messageType", "error");
            ra.addFlashAttribute("comboForm", req);
            return "redirect:/combos/" + id + "/edit";
        }
        try {
            comboService.update(id, req);
            ra.addFlashAttribute("message", "Cập nhật combo thành công.");
            ra.addFlashAttribute("messageType", "success");
            return "redirect:/combos";
        } catch (Exception e) {
            ra.addFlashAttribute("message", e.getMessage());
            ra.addFlashAttribute("messageType", "error");
            ra.addFlashAttribute("comboForm", req);
            return "redirect:/combos/" + id + "/edit";
        }
    }

    /* ===== SHOW EDIT FORM (/{id}/edit) ===== */
    @GetMapping("/{id:\\d+}/edit")
    public String editModel(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            ComboDTO dto = comboService.getById(id);

            ComboRequest form = new ComboRequest();
            form.setCode(dto.getCode());
            form.setName(dto.getName());
            form.setDescription(dto.getDescription());
            form.setStatus(dto.getStatus());
            form.setDetails(dto.getDetails() == null ? List.of() :
                    dto.getDetails().stream()
                            .map(d -> ComboRequest.Item.builder()
                                    .productId(d.getProductId())
                                    .quantity(d.getQuantity())
                                    .build())
                            .toList());

            if (!model.containsAttribute("comboForm")) {
                model.addAttribute("comboForm", form);
            }
            model.addAttribute("comboId", id);
            model.addAttribute("products", productService.getAllActiveProducts());
            return "combo/combo-edit";
        } catch (Exception e) {
            ra.addFlashAttribute("message", "Không tìm thấy combo");
            ra.addFlashAttribute("messageType", "error");
            return "redirect:/combos";
        }
    }



    /* ===== DETAIL (/{id}/view) ===== */
    @GetMapping("/{id:\\d+}/view")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes ra) {
        try {
            model.addAttribute("combo", comboService.getById(id));
            return "combo/combo-detail";
        } catch (Exception e) {
            ra.addFlashAttribute("message", "Không tìm thấy combo");
            ra.addFlashAttribute("messageType", "error");
            return "redirect:/combos";
        }
    }

    /* ===== TOGGLE STATUS (AJAX) ===== */
    @PostMapping("/{id:\\d+}/status")
    @ResponseBody
    public String updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            Combo.ComboStatus status = Combo.ComboStatus.valueOf(payload.get("status"));
            comboService.updateStatus(id, status);
            return "OK";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /* ===== BUNG COMBO RA SẢN PHẨM (cho sale-order) ===== */
    @PostMapping("/expand")
    @ResponseBody
    public List<ComboDetailDTO> expand(@RequestBody List<Long> comboIds) {
        return comboService.expandAsComboDetailDTO(comboIds);
    }
}
