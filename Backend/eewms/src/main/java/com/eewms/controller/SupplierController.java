package com.eewms.controller;

import com.eewms.dto.SupplierDTO;
import com.eewms.services.ISupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final ISupplierService supplierService;

    @GetMapping
    public String showSupplierList(Model model) {
        model.addAttribute("suppliers", supplierService.findAll());
        model.addAttribute("newSupplier", new SupplierDTO());
        return "supplier-list";
    }
}
