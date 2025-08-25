package com.eewms.controller;

import com.eewms.constant.SettingType;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.dto.SettingDTO;
import com.eewms.dto.ComboDTO;
import com.eewms.services.IProductServices;
import com.eewms.services.ISettingServices;
import com.eewms.services.IComboService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class LandingController {

    private final IProductServices productService;
    private final ISettingServices settingService;
    private final IComboService comboService;

    @ModelAttribute("path")
    public String path(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "";
    }

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
        var combos = comboService.getAllActive();                 // ✨ nạp danh sách combo cho sidebar

        model.addAttribute("productPage", productPage);
        model.addAttribute("products", productPage.getContent());
        model.addAttribute("totalPages", productPage.getTotalPages());
        model.addAttribute("currentPage", productPage.getNumber());
        model.addAttribute("size", productPage.getSize());

        model.addAttribute("categories", categories);
        model.addAttribute("combos", combos);                      // ✨ truyền xuống view
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedCategoryId", categoryId);
        model.addAttribute("sort", sort);

        return "landing/landing-products";
    }

    // ✨ Trang CHI TIẾT COMBO – hiển thị tất cả sản phẩm trong combo
    @GetMapping("/combo/{id}")
    public String showComboDetail(@PathVariable("id") Long comboId, Model model) {
        ComboDTO combo = comboService.getById(comboId);
        if (combo == null || combo.getStatus() != com.eewms.entities.Combo.ComboStatus.ACTIVE) {
            return "redirect:/san-pham";
        }

        // Tính tổng tiền = Σ (price * quantity)
        BigDecimal totalPrice = combo.getDetails() == null ? BigDecimal.ZERO :
                combo.getDetails().stream()
                        .map(it -> (it.getPrice() == null ? BigDecimal.ZERO : it.getPrice())
                                .multiply(BigDecimal.valueOf(it.getQuantity() == null ? 0 : it.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Sidebar vẫn hiển thị categories + combos
        var categories = settingService.getByType(SettingType.CATEGORY);
        var combos = comboService.getAllActive();

        model.addAttribute("categories", categories);
        model.addAttribute("combos", combos);
        model.addAttribute("combo", combo);
        model.addAttribute("totalPrice", totalPrice);

        return "landing/landing-combo-detail";
    }

    @GetMapping("/san-pham/{id}")
    public String showProductDetail(@PathVariable("id") Integer id, Model model) {
        var product = productService.getById(id); // đã có sẵn trong service của bạn
        var categories = settingService.getByType(SettingType.CATEGORY);
        var combos     = comboService.getAllActive(); // ✨ thêm

        model.addAttribute("product", product);
        model.addAttribute("categories", categories);


        model.addAttribute("combos", comboService.getAllActive());

        return "landing/landing-product-detail";
    }


}
