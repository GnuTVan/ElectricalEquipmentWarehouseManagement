package com.eewms.controller;

import com.eewms.dto.CustomerDTO;
import com.eewms.services.ICustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final ICustomerService service;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("customers", service.findAll());
        return "customer/list";
    }

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("customer", new CustomerDTO());
        return "customer/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute("customer") @Valid CustomerDTO dto,
                       BindingResult result,
                       RedirectAttributes redirect,
                       Model model) {
        if (result.hasErrors()) {
            model.addAttribute("customer", dto);
            return "customer/form"; // quay lại form nếu có lỗi
        }

        service.create(dto);
        redirect.addFlashAttribute("success", "Thêm khách hàng thành công");
        return "redirect:/customers";
    }


    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("customer", service.findById(id));
        return "customer/form";
    }


    @PostMapping("/update")
    public String update(@ModelAttribute("customer") @Valid CustomerDTO dto,
                         BindingResult result,
                         RedirectAttributes redirect,
                         Model model) {
        if (result.hasErrors()) {
            model.addAttribute("customer", dto);
            return "customer/form";
        }
        service.update(dto);
        redirect.addFlashAttribute("success", "Cập nhật thành công");
        return "redirect:/customers";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        service.delete(id);
        redirect.addFlashAttribute("success", "Xóa thành công");
        return "redirect:/customers";
    }
}
