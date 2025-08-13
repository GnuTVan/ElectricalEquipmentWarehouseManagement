package com.eewms.controller;

import com.eewms.dto.CustomerDTO;
import com.eewms.entities.Customer;
import com.eewms.services.ICustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping({"/customers", "/customer-list"})
@RequiredArgsConstructor
public class CustomerController {

    private final ICustomerService service;

    @GetMapping
    public String list(@RequestParam(value = "keyword", required = false) String keyword, Model model) {
        if (!model.containsAttribute("customer")) {
            CustomerDTO dto = new CustomerDTO();
            dto.setStatus(Customer.CustomerStatus.ACTIVE);  // Gán mặc định
            model.addAttribute("customer", dto);
        }
        if (keyword != null && !keyword.isBlank()) {
            model.addAttribute("customers", service.searchByKeyword(keyword));
        } else {
            model.addAttribute("customers", service.findAll());
        }
        model.addAttribute("keyword", keyword);
        return "customer/customer-list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("customer", new CustomerDTO());
        return "customer/form";
    }

    @PostMapping("/save")
    public String createCustomer(@ModelAttribute("customer") @Valid CustomerDTO dto,
                                 BindingResult result,
                                 RedirectAttributes redirect,
                                 Model model) {
        if (result.hasErrors()) {
            model.addAttribute("customer", dto);
            model.addAttribute("hasFormError", true);
            model.addAttribute("customers", service.findAll());
            return "customer/customer-list";
        }

        try {
            service.create(dto);
            redirect.addFlashAttribute("message", "Thêm KH " + dto.getFullName() + " thành công");
            redirect.addFlashAttribute("messageType", "success");
        } catch (Exception ex) {
            redirect.addFlashAttribute("error", "Lỗi khi thêm khách hàng: " + ex.getMessage());
        }

        return "redirect:/customers";
    }


    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("customer", service.getById(id));
        return "customer/form";
    }


    @PostMapping("/update/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute("customer") @Valid CustomerDTO dto,
                         BindingResult result,
                         RedirectAttributes redirect,
                         Model model) {
//        dto.setId(id); // Gán id từ đường dẫn vào DTO khi k có hidden id
        if (result.hasErrors()) {
            model.addAttribute("customer", dto);
            model.addAttribute("editId", id);
            model.addAttribute("hasEditError", true);
            model.addAttribute("customers", service.findAll());
            return "customer/customer-list";
        }

        try {
            service.update(dto);
            redirect.addFlashAttribute("message", "Cập nhật thông tin KH thành công, Tên: " + dto.getFullName());
            redirect.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi khi cập nhật: " + e.getMessage());
        }

        return "redirect:/customers";
    }

    @PostMapping("/{id}/status")
    @ResponseBody
    public String updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload, RedirectAttributes redirect, Model model) {
        try {
            String statusStr = payload.get("status");
            Customer.CustomerStatus status = Customer.CustomerStatus.valueOf(statusStr);
            service.updateStatus(id, status);
            redirect.addFlashAttribute("message", "Cập nhật trạng thái KH thành công");
            redirect.addFlashAttribute("messageType", "success");
            return "OK";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        service.delete(id);
        redirect.addFlashAttribute("success", "Xóa thành công");
        return "redirect:/customers";
    }
}
