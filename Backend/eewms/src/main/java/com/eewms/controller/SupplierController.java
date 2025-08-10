package com.eewms.controller;

import com.eewms.dto.SupplierDTO;
import com.eewms.services.ISupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final ISupplierService supplierService;

    // ✅ Hiển thị danh sách + form thêm mới
    @GetMapping
    public String showSupplierList(Model model,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "") String keyword) {
        model.addAttribute("activePage", "suppliers");

        // Gọi service trả về phân trang
        Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(page, keyword);
        model.addAttribute("supplierPage", supplierPage);
        model.addAttribute("suppliers", supplierPage.getContent()); // dữ liệu thực tế để hiển thị bảng

        // Để giữ lại tìm kiếm nếu có
        model.addAttribute("keyword", keyword);

        // Form thêm và sửa
        model.addAttribute("newSupplier", new SupplierDTO());
        model.addAttribute("editSupplier", new SupplierDTO());

        return "supplier-list";
    }

    @PostMapping
    public String createSupplier(@Valid @ModelAttribute("newSupplier") SupplierDTO dto,
                                 BindingResult result,
                                 @RequestParam(defaultValue = "") String keyword,
                                 Model model,
                                 RedirectAttributes redirect) {
        // 1) Nếu sai regex/NotBlank từ DTO → trả form ngay
        if (result.hasErrors()) {
            Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(0, keyword);
            model.addAttribute("supplierPage", supplierPage);
            model.addAttribute("suppliers", supplierPage.getContent());
            model.addAttribute("keyword", keyword);
            model.addAttribute("newSupplier", dto);
            model.addAttribute("editSupplier", new SupplierDTO());
            model.addAttribute("hasFormError", true);

            return "supplier-list";
        }

        // 2) Chuẩn hoá và check trùng có điều kiện
        String name = dto.getName() == null ? null : dto.getName().trim();
        String tax  = dto.getTaxCode() == null ? null : dto.getTaxCode().trim();
        String acc  = dto.getBankAccount() == null ? null : dto.getBankAccount().trim();
        String mob  = dto.getContactMobile() == null ? null : dto.getContactMobile().trim();

        if (name != null && !name.isBlank() && supplierService.existsByNameIgnoreCase(name)) {
            result.rejectValue("name", "dup.name", "Tên nhà cung cấp đã tồn tại");
        }
        if (tax != null && !tax.isBlank() && supplierService.existsByTaxCode(tax)) {
            result.rejectValue("taxCode", "dup.tax", "Mã số thuế đã tồn tại");
        }
        if (acc != null && !acc.isBlank() && supplierService.existsByBankAccount(acc)) {
            result.rejectValue("bankAccount", "dup.acc", "Số tài khoản đã tồn tại");
        }
        if (mob != null && !mob.isBlank() && supplierService.existsByContactMobile(mob)) {
            result.rejectValue("contactMobile", "dup.mobile", "Số điện thoại đã tồn tại");
        }

        if (result.hasErrors()) {
            Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(0, keyword);
            model.addAttribute("supplierPage", supplierPage);
            model.addAttribute("suppliers", supplierPage.getContent());
            model.addAttribute("keyword", keyword);
            model.addAttribute("newSupplier", dto);
            model.addAttribute("editSupplier", new SupplierDTO());
            model.addAttribute("hasFormError", true);
            return "supplier-list";
        }

        // 3) Lưu
        supplierService.create(dto);
        redirect.addFlashAttribute("message", "Thêm nhà cung cấp thành công, Tên: " + name);
        redirect.addFlashAttribute("messageType", "success");
        return "redirect:/admin/suppliers";
    }

    @PostMapping("/update")
    public String updateSupplier(@Valid @ModelAttribute("editSupplier") SupplierDTO dto,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirect,
                                 @RequestParam(defaultValue = "") String keyword) {
        // 1) Nếu sai regex/NotBlank từ DTO → trả form ngay
        if (result.hasErrors()) {
            Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(0, keyword);
            model.addAttribute("supplierPage", supplierPage);
            model.addAttribute("suppliers", supplierPage.getContent());
            model.addAttribute("keyword", keyword);
            model.addAttribute("newSupplier", new SupplierDTO());
            model.addAttribute("editSupplier", dto);
            model.addAttribute("hasEditError", true);
            return "supplier-list";
        }

        // 2) Chuẩn hoá và check trùng loại trừ chính nó
        String name = dto.getName() == null ? null : dto.getName().trim();
        String tax  = dto.getTaxCode() == null ? null : dto.getTaxCode().trim();
        String acc  = dto.getBankAccount() == null ? null : dto.getBankAccount().trim();
        String mob  = dto.getContactMobile() == null ? null : dto.getContactMobile().trim();

        Long id = dto.getId();

        if (name != null && !name.isBlank() && supplierService.existsByNameIgnoreCaseAndIdNot(name, id)) {
            result.rejectValue("name", "dup.name", "Tên nhà cung cấp đã tồn tại");
        }
        if (tax != null && !tax.isBlank() && supplierService.existsByTaxCodeAndIdNot(tax, id)) {
            result.rejectValue("taxCode", "dup.tax", "Mã số thuế đã tồn tại");
        }
        if (acc != null && !acc.isBlank() && supplierService.existsByBankAccountAndIdNot(acc, id)) {
            result.rejectValue("bankAccount", "dup.acc", "Số tài khoản đã tồn tại");
        }
        if (mob != null && !mob.isBlank() && supplierService.existsByContactMobileAndIdNot(mob, id)) {
            result.rejectValue("contactMobile", "dup.mobile", "Số điện thoại đã tồn tại");
        }

        if (result.hasErrors()) {
            Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(0, keyword);
            model.addAttribute("supplierPage", supplierPage);
            model.addAttribute("suppliers", supplierPage.getContent());
            model.addAttribute("keyword", keyword);
            model.addAttribute("newSupplier", new SupplierDTO());
            model.addAttribute("editSupplier", dto);
            model.addAttribute("hasEditError", true);
            return "supplier-list";
        }

        // 3) Lưu
        supplierService.update(dto);
        redirect.addFlashAttribute("message", "Cập nhật NCC thành công, Tên: " + name);
        redirect.addFlashAttribute("messageType", "success");
        return "redirect:/admin/suppliers";
    }

    // ✅ Bật / Tắt trạng thái
    @PostMapping("/toggle/{id}")
    public String toggleStatus(@PathVariable Long id, RedirectAttributes redirect) {
        supplierService.toggleStatus(id);
        redirect.addFlashAttribute("message", "Cập nhật trạng thái NCC thành công, ID: " + id);
        redirect.addFlashAttribute("messageType", "success");
        return "redirect:/admin/suppliers";
    }
}