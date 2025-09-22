package com.eewms.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/inventory/count")
public class InventoryCountController {

    // LIST: /inventory/count
    @GetMapping
    public String list(Model model) {
        // Có thể add model attribute sau này nếu cần
        return "inventory/inventory-order-list";
    }

    // COUNTING: /inventory/count/{id}
    @GetMapping("/{id}")
    public String counting(@PathVariable("id") Long id, Model model) {
        // Dummy: chỉ điều hướng tới trang nhập số lượng
        model.addAttribute("orderId", id);
        return "inventory/inventory-counting";
    }

    // Alias COUNTING: /inventory/count/{id}/continue
    @GetMapping("/{id}/continue")
    public String countingContinue(@PathVariable("id") Long id, Model model) {
        model.addAttribute("orderId", id);
        return "inventory/inventory-counting";
    }

    // REVIEW: /inventory/count/{id}/review
    @GetMapping("/{id}/review")
    public String review(@PathVariable("id") Long id, Model model) {
        model.addAttribute("orderId", id);
        return "inventory/inventory-review";
    }

    // REPORT (dummy): điều hướng tạm về review để tránh 404
    @GetMapping("/{id}/report")
    public String report(@PathVariable("id") Long id) {
        return "redirect:/inventory/count/{id}/review";
    }

    // EXPORT (dummy): điều hướng tạm về review để tránh 404
    @GetMapping("/{id}/export")
    public String export(@PathVariable("id") Long id) {
        return "redirect:/inventory/count/{id}/review";
    }

    // CREATE (dummy): điều hướng về list vì chưa làm form
    @GetMapping("/create")
    public String create(Model model) {
        // chuẩn bị dữ liệu nếu cần, hiện tại dummy
        return "inventory/inventory-count-create";
    }
}
