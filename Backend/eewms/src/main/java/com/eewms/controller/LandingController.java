package com.eewms.controller;

import com.eewms.constant.SettingType;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.dto.SettingDTO;
import com.eewms.services.IProductServices;
import com.eewms.services.ISettingServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class LandingController {

    private final IProductServices productService;
    private final ISettingServices settingService;

    @GetMapping("/landing-page")
    public String showHome() {
        return "landing-home";
    }

    @GetMapping("/gioi-thieu")
    public String showAboutPage() {
        return "landing-about";
    }

    @GetMapping("/san-pham")
    public String showProducts(Model model) {
        List<ProductDetailsDTO> products = productService.getAllActiveProducts();
        List<SettingDTO> categories = settingService.getByType(SettingType.CATEGORY);

        model.addAttribute("products", products);
        model.addAttribute("categories", categories);
        return "landing-products";
    }
}