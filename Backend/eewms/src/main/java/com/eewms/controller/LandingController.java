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
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class LandingController {

    private final IProductServices productService;
    private final ISettingServices settingService;

    // Trang chủ
    @GetMapping("/landing-page")
    public String showHome() {
        return "landing-home";
    }

    // Trang giới thiệu
    @GetMapping("/gioi-thieu")
    public String showAboutPage() {
        return "landing-about";
    }

    // Trang sản phẩm (landing), hỗ trợ lọc
    @GetMapping("/san-pham")
    public String showProducts(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "filterCategory", required = false) Long categoryId,
                               Model model) {

        List<ProductDetailsDTO> products;

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = categoryId != null;

        if (hasKeyword || hasCategory) {
            products = productService.searchByKeywordAndCategory(hasKeyword ? keyword : null, categoryId);
        } else {
            products = productService.getAllActiveProducts();
        }

        List<SettingDTO> categories = settingService.getByType(SettingType.CATEGORY);

        model.addAttribute("products", products);
        model.addAttribute("categories", categories);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);

        return "landing-products";
    }
}
