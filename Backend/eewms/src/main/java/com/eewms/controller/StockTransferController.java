package com.eewms.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class StockTransferController {

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public String list(Model model,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String status) {
        model.addAttribute("q", q);
        model.addAttribute("status", status);
        model.addAttribute("transfers", java.util.List.of());
        // khớp đúng: src/main/resources/templates/warehouses/transfer-list.html
        return "warehouses/transfer-list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','STAFF')")
    public String createForm(Model model) {
        // khớp đúng: src/main/resources/templates/warehouses/transfer-form.html
        return "warehouses/transfer-form";
    }
}
