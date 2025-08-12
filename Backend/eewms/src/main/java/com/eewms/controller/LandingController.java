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
        return "landing/landing-home";
    }

    // Trang giới thiệu
    @GetMapping("/gioi-thieu")
    public String showAboutPage() {
        return "landing/landing-about";
    }

    // Trang sản phẩm (landing) – lọc + sắp xếp + phân trang (DB-side)
    @GetMapping("/san-pham")
    public String showProducts(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "filterCategory", required = false) Long categoryId,
                               @RequestParam(value = "sort", required = false, defaultValue = "") String sort,
                               @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                               @RequestParam(value = "size", required = false, defaultValue = "8") int size,
                               Model model) {

        boolean hasFilter = (keyword != null && !keyword.isBlank()) || (categoryId != null);

        var productPage = hasFilter
                ? productService.searchByKeywordAndCategory(keyword, categoryId, sort, page, size)
                : productService.getAllActiveProducts(sort, page, size);

        var categories = settingService.getByType(SettingType.CATEGORY);

        model.addAttribute("productPage", productPage);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("currentPage", productPage.getNumber());
        model.addAttribute("size", productPage.getSize());

        model.addAttribute("categories", categories);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("sort", sort);

        return "landing/landing-products";
    }

}
