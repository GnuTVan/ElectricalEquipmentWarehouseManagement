package com.eewms.controller;

import com.eewms.dto.ProductFormDTO;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.constant.SettingType;
import com.eewms.dto.UserDTO;
import com.eewms.dto.UserMapper;
import com.eewms.entities.Product;
import com.eewms.entities.User;
import com.eewms.exception.InventoryException;
import com.eewms.services.IProductServices;
import com.eewms.services.ISettingServices;
import com.eewms.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping({"/products", "/product-list"})
@RequiredArgsConstructor
public class ProductController {

    private final IProductServices productService;
    private final ISettingServices settingService;
    private final ImageUploadService imageUploadService;

    @GetMapping
    public String list(Model model) throws InventoryException {
        model.addAttribute("products", productService.getAll());
        model.addAttribute("productDTO", new ProductFormDTO());
        model.addAttribute("units",      settingService.getByType(SettingType.UNIT));
        model.addAttribute("brands",     settingService.getByType(SettingType.BRAND));
        model.addAttribute("categories", settingService.getByType(SettingType.CATEGORY));
        return "product-list";
    }

    // xử lý submit modal form thêm sản phẩm
    @PostMapping
    public String create(@ModelAttribute("productDTO") ProductFormDTO dto,
                         @RequestParam("images") List<MultipartFile> images,
                         RedirectAttributes ra) {
        try {
            if (images == null || images.isEmpty()) {
                ra.addFlashAttribute("error", "Vui lòng chọn ít nhất một ảnh.");
                return "redirect:/products";
            }

            // Validate ảnh
            for (MultipartFile file : images) {
                String contentType = file.getContentType();
                if (file.getSize() > 5 * 1024 * 1024 ||
                        contentType == null || !contentType.matches("image/(jpeg|jpg|png)")) {
                    ra.addFlashAttribute("error", "Ảnh phải là JPG/PNG và nhỏ hơn 5MB");
                    return "redirect:/products";
                }
            }

            // Upload ảnh và gán URL
            List<String> urls = new java.util.ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                MultipartFile file = images.get(i);
                String url = imageUploadService.uploadImage(file);
                if (i == 0) {
                    urls.add(url + "|thumbnail"); // ảnh đầu tiên là thumbnail
                } else {
                    urls.add(url);
                }
            }

            dto.setUploadedImageUrls(urls); // gán URL đã xử lý vào DTO
            productService.create(dto);
            ra.addFlashAttribute("success", "Thêm sản phẩm thành công");

        } catch (Exception ex) {
            ra.addFlashAttribute("error", "Lỗi khi tạo sản phẩm: " + ex.getMessage());
        }

        return "redirect:/products";
    }

    @PostMapping("/save")
    public String save(
            @ModelAttribute ProductFormDTO productForm,
            BindingResult br,
            RedirectAttributes ra,
            Model model) {
        if (br.hasErrors()) {
            return "products/form";
        }
        try {
            if (productForm.getId() == null)
                productService.create(productForm);
            else
                productService.update(productForm.getId(), productForm);
            ra.addFlashAttribute("success", "Lưu thành công");
            return "redirect:/products";
        } catch (InventoryException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "products/form";
        }
    }

    // Xử lý cập nhật
    @PostMapping("/update/{id}")
    public String updateProduct(@PathVariable Integer id,
                                @RequestParam(value = "images", required = false) MultipartFile image,
                                @ModelAttribute ProductFormDTO productForm,
                                RedirectAttributes redirect) {
        try {
            // Nếu có ảnh mới được upload
            if (image != null && !image.isEmpty()) {
                String contentType = image.getContentType();
                if (image.getSize() > 5 * 1024 * 1024 ||
                        contentType == null || !contentType.matches("image/(jpeg|jpg|png)")) {
                    redirect.addFlashAttribute("error", "Ảnh phải là JPG/PNG và nhỏ hơn 5MB");
                    return "redirect:/products";
                }

                // Upload lên Cloudinary
                String url = imageUploadService.uploadImage(image);

                // Gán vào danh sách ảnh (chỉ 1 ảnh, đánh dấu thumbnail)
                productForm.setUploadedImageUrls(List.of(url + "|thumbnail"));
            }

            // Gọi service update
            productService.update(id, productForm);
            redirect.addFlashAttribute("success", "Cập nhật sản phẩm thành công");

        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Lỗi khi cập nhật: " + e.getMessage());
        }

        return "redirect:/products";
    }


    @GetMapping("/{id}")
    public String detail(@PathVariable Integer id, Model model) {
        try {
            ProductDetailsDTO dto = productService.getById(id);
            model.addAttribute("product", dto);
            return "products/detail";
        } catch (InventoryException ex) {
            return "redirect:/products";
        }
    }

    @PostMapping("/{id}/status")
    @ResponseBody
    public String updateStatus(@PathVariable Integer id, @RequestBody Map<String, String> payload) {
        try {
            String statusValue = payload.get("status");
            productService.updateStatus(id, Product.ProductStatus.valueOf(statusValue));
            return "OK";
        } catch (Exception ex) {
            return "ERROR: " + ex.getMessage();
        }
    }


}
