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

    



    // ✅ Xử lý tạo mới nhà cung cấp
//    @PostMapping
//    public String createSupplier(@Valid @ModelAttribute("newSupplier") SupplierDTO dto,
//                                 BindingResult result,
//                                 @RequestParam(defaultValue = "") String keyword,
//                                 Model model,
//                                 RedirectAttributes redirect) {
//
//
//        // ✅ Kiểm tra trùng mã số thuế nếu có nhập
//        if (dto.getTaxCode() != null && !dto.getTaxCode().isBlank()
//                && supplierService.existsByTaxCode(dto.getTaxCode())) {
//            result.rejectValue("taxCode", "error.taxCode", "Mã số thuế đã tồn tại");
//        }
//
//        // ✅ Kiểm tra tên ngân hàng có hợp lệ không
//        List<String> validBanks = List.of(
//                "Vietcombank", "BIDV", "Techcombank", "MB Bank", "TPBank", "VPBank", "ACB", "Sacombank"
//        );
//        if (dto.getBankName() != null && !dto.getBankName().isBlank()
//                && !validBanks.contains(dto.getBankName())) {
//            result.rejectValue("bankName", "error.bankName", "Ngân hàng không hợp lệ");
//        }
//
//        if (result.hasErrors()) {
//            // ✅ Dữ liệu phân trang mặc định page=0, keyword=""
//            Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(0, "");
//            model.addAttribute("supplierPage", supplierPage);
//            model.addAttribute("suppliers", supplierPage.getContent());
//            model.addAttribute("keyword", keyword);
//
//
//            // ✅ Giữ lại form dữ liệu
//            model.addAttribute("newSupplier", dto);
//            model.addAttribute("editSupplier", new SupplierDTO());
//            return "supplier-list";
//        }
//
//        supplierService.create(dto);
//        redirect.addFlashAttribute("message", "Thêm nhà cung cấp thành công");
//        return "redirect:/admin/suppliers";
//    }
//
//
//    // ✅ Xử lý cập nhật nhà cung cấp
//    @PostMapping("/update")
//    public String updateSupplier(@Valid @ModelAttribute("editSupplier") SupplierDTO dto,
//                                 BindingResult result,
//                                 Model model,
//                                 RedirectAttributes redirect) {
//
//        // ✅ Kiểm tra trùng mã số thuế (nếu có nhập)
//        if (!result.hasFieldErrors("taxCode") &&
//                dto.getTaxCode() != null && !dto.getTaxCode().isBlank()) {
//            SupplierDTO existing = supplierService.findByTaxCode(dto.getTaxCode());
//            if (existing != null && !existing.getId().equals(dto.getId())) {
//                result.rejectValue("taxCode", "error.taxCode", "Mã số thuế đã tồn tại cho nhà cung cấp khác");
//            }
//        }
//
//        // ✅ Kiểm tra tên ngân hàng có hợp lệ không
//        List<String> validBanks = List.of(
//                "Vietcombank", "BIDV", "Techcombank", "MB Bank", "TPBank", "VPBank", "ACB", "Sacombank"
//        );
//        if (!result.hasFieldErrors("bankName") &&
//                (dto.getBankName() == null || !validBanks.contains(dto.getBankName()))) {
//            result.rejectValue("bankName", "error.bankName", "Ngân hàng không hợp lệ");
//        }
//
//        if (result.hasErrors()) {
//            Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(0, "");
//            model.addAttribute("supplierPage", supplierPage);
//            model.addAttribute("suppliers", supplierPage.getContent());
//            model.addAttribute("keyword", "");
//
//            model.addAttribute("newSupplier", new SupplierDTO());
//            model.addAttribute("editSupplier", dto);
//            return "supplier-list";
//        }
//
//        supplierService.update(dto);
//        redirect.addFlashAttribute("message", "Cập nhật nhà cung cấp thành công");
//        return "redirect:/admin/suppliers";
//    }

    @PostMapping
    public String createSupplier(@Valid @ModelAttribute("newSupplier") SupplierDTO dto,
                                 BindingResult result,
                                 @RequestParam(defaultValue = "") String keyword,
                                 Model model,
                                 RedirectAttributes redirect) {

        if (!result.hasFieldErrors("taxCode")
                && dto.getTaxCode() != null && !dto.getTaxCode().isBlank()
                && supplierService.existsByTaxCode(dto.getTaxCode().trim())) {
            result.rejectValue("taxCode", "error.taxCode", "Mã số thuế đã tồn tại");
        }

        if (result.hasErrors()) {
            Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(0, "");
            model.addAttribute("supplierPage", supplierPage);
            model.addAttribute("suppliers", supplierPage.getContent());
            model.addAttribute("keyword", keyword);
            model.addAttribute("newSupplier", dto);
            model.addAttribute("editSupplier", new SupplierDTO());
            return "supplier-list";
        }

        // ✅ Chuyển "" thành null trực tiếp
        if (dto.getTaxCode() != null && dto.getTaxCode().isBlank()) dto.setTaxCode(null);
        if (dto.getBankName() != null && dto.getBankName().isBlank()) dto.setBankName(null);
        if (dto.getBankAccount() != null && dto.getBankAccount().isBlank()) dto.setBankAccount(null);
        if (dto.getContactName() != null && dto.getContactName().isBlank()) dto.setContactName(null);
        if (dto.getContactMobile() != null && dto.getContactMobile().isBlank()) dto.setContactMobile(null);
        if (dto.getAddress() != null && dto.getAddress().isBlank()) dto.setAddress(null);
        if (dto.getDescription() != null && dto.getDescription().isBlank()) dto.setDescription(null);

        supplierService.create(dto);
        redirect.addFlashAttribute("message", "Thêm nhà cung cấp thành công");
        return "redirect:/admin/suppliers";
    }

    @PostMapping("/update")
    public String updateSupplier(@Valid @ModelAttribute("editSupplier") SupplierDTO dto,
                                 BindingResult result,
                                 Model model,
                                 RedirectAttributes redirect) {

        // Check trùng MST nếu có nhập và thuộc về NCC khác
        if (!result.hasFieldErrors("taxCode")
                && dto.getTaxCode() != null && !dto.getTaxCode().isBlank()) {
            String tax = dto.getTaxCode().trim();
            SupplierDTO existing = supplierService.findByTaxCode(tax);
            if (existing != null && !existing.getId().equals(dto.getId())) {
                result.rejectValue("taxCode", "error.taxCode", "Mã số thuế đã tồn tại cho nhà cung cấp khác");
            }
        }

        if (result.hasErrors()) {
            Page<SupplierDTO> supplierPage = supplierService.searchSuppliers(0, "");
            model.addAttribute("supplierPage", supplierPage);
            model.addAttribute("suppliers", supplierPage.getContent());
            model.addAttribute("keyword", "");
            model.addAttribute("newSupplier", new SupplierDTO());
            model.addAttribute("editSupplier", dto);
            return "supplier-list";
        }

        // Chuyển "" -> null cho các trường optional (regex sẽ làm sau)
        if (dto.getTaxCode() != null) dto.setTaxCode(dto.getTaxCode().trim());
        if (dto.getBankName() != null) dto.setBankName(dto.getBankName().trim());
        if (dto.getBankAccount() != null) dto.setBankAccount(dto.getBankAccount().trim());
        if (dto.getContactName() != null) dto.setContactName(dto.getContactName().trim());
        if (dto.getContactMobile() != null) dto.setContactMobile(dto.getContactMobile().trim());
        if (dto.getAddress() != null) dto.setAddress(dto.getAddress().trim());
        if (dto.getDescription() != null) dto.setDescription(dto.getDescription().trim());

        if (dto.getTaxCode() != null && dto.getTaxCode().isBlank()) dto.setTaxCode(null);
        if (dto.getBankName() != null && dto.getBankName().isBlank()) dto.setBankName(null);
        if (dto.getBankAccount() != null && dto.getBankAccount().isBlank()) dto.setBankAccount(null);
        if (dto.getContactName() != null && dto.getContactName().isBlank()) dto.setContactName(null);
        if (dto.getContactMobile() != null && dto.getContactMobile().isBlank()) dto.setContactMobile(null);
        if (dto.getAddress() != null && dto.getAddress().isBlank()) dto.setAddress(null);
        if (dto.getDescription() != null && dto.getDescription().isBlank()) dto.setDescription(null);

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