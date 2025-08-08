package com.eewms.controller;

import com.eewms.dto.ProductFormDTO;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.constant.SettingType;
import com.eewms.entities.Product;
import com.eewms.exception.InventoryException;
import com.eewms.services.IProductServices;
import com.eewms.services.ISettingServices;
import com.eewms.services.ISupplierService;
import com.eewms.services.ImageUploadService;
import jakarta.validation.Valid;
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
    private final ISupplierService supplierService;

    @GetMapping
    public String list( @RequestParam(value = "keyword",
                        required = false) String keyword,
                        Model model) throws InventoryException {

        if (keyword != null && !keyword.isBlank()) {
            model.addAttribute("products", productService.searchByKeyword(keyword));
        } else {
            model.addAttribute("products", productService.getAll());
        }

        model.addAttribute("keyword", keyword);
        model.addAttribute("productDTO", new ProductFormDTO());
        model.addAttribute("units",      settingService.findByTypeAndActive(SettingType.UNIT));
        model.addAttribute("brands",     settingService.findByTypeAndActive(SettingType.BRAND));
        model.addAttribute("categories", settingService.findByTypeAndActive(SettingType.CATEGORY));
        model.addAttribute("suppliers",  supplierService.findAll()); // k cần lọc active
        return "product/product-list";
    }

    // xử lý submit modal form thêm sản phẩm
    @PostMapping
    public String create(@ModelAttribute("productDTO") @Valid ProductFormDTO dto,
                         BindingResult br,
                         @RequestParam(value = "images",required = false) List<MultipartFile> images,
                         Model model,
                         RedirectAttributes ra) {
        // Nếu có lỗi validate form
        if (br.hasErrors()) {
            model.addAttribute("productDTO", dto);// gán lại DTO để hiển thị lỗi
            model.addAttribute("hasFormError", true);
            model.addAttribute("products", productService.getAll());
            model.addAttribute("units",      settingService.findByTypeAndActive(SettingType.UNIT));
            model.addAttribute("brands",     settingService.findByTypeAndActive(SettingType.BRAND));
            model.addAttribute("categories", settingService.findByTypeAndActive(SettingType.CATEGORY));
            model.addAttribute("suppliers",  supplierService.findAll()); //lọc active, sau sửa lại thành find active
            return "product/product-list";
        }

        // Nếu không có lỗi validate, tiến hành xử lý upload ảnh
        try {
            List<String> urls = new java.util.ArrayList<>();

            // Nếu người dùng có upload ảnh
            if (images != null && !images.isEmpty()) {
                try {
                    urls = validateAndUploadImages(images, ra);
                    dto.setUploadedImageUrls(urls);
                } catch (Exception e) {
                    ra.addFlashAttribute("error", e.getMessage());
                    return "redirect:/products";
                }
            }

            dto.setUploadedImageUrls(urls); // gán URL đã xử lý vào DTO, có thể rỗng
            productService.create(dto);
            ra.addFlashAttribute("message", "Tạo thành công!");
            ra.addFlashAttribute("messageType", "success");
        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Lỗi khi tạo sản phẩm: " + ex.getMessage());
            ra.addFlashAttribute("messageType", "error");
        }

        return "redirect:/products";
    }

    // Xử lý cập nhật
    @PostMapping("/update/{id}")
    public String updateProduct(@PathVariable Integer id,
                                @RequestParam(value = "images", required = false) MultipartFile image,
                                @RequestParam(value = "deletedImages", required = false) List<String> deletedImages,
                                @ModelAttribute("productDTO") @Valid ProductFormDTO productForm,
                                BindingResult br,
                                Model model,
                                RedirectAttributes redirect) {

        if (br.hasErrors()) {
            model.addAttribute("productDTO", productForm); // gán lại DTO để hiển thị lỗi
            model.addAttribute("editError", true);
            model.addAttribute("editId", id); // để biết đang sửa sản phẩm nào
            model.addAttribute("products", productService.getAll());
            model.addAttribute("units", settingService.findByTypeAndActive(SettingType.UNIT));
            model.addAttribute("brands", settingService.findByTypeAndActive(SettingType.BRAND));
            model.addAttribute("categories", settingService.findByTypeAndActive(SettingType.CATEGORY));
            model.addAttribute("suppliers",  supplierService.findAll()); //lọc active, sau sửa lại thành find active
            return "product/product-list";
        }

        try {
            // Nếu có ảnh mới được upload
            if (image != null && !image.isEmpty()) {
                try {
                    List<String> urls = validateAndUploadImages(List.of(image), redirect);
                    productForm.setUploadedImageUrls(urls);
                } catch (Exception e) {
                    redirect.addFlashAttribute("message", e.getMessage());
                    redirect.addFlashAttribute("messageType", "error");
                    return "redirect:/products";
                }
            }

            // Nếu có ảnh cần xóa
            if (deletedImages != null && !deletedImages.isEmpty()) {
                productService.removeImagesByUrls(id, deletedImages);  // service tự xử lý xóa
            }

            productService.update(id, productForm);
            redirect.addFlashAttribute("message", "Cập nhật sản phẩm thành công");
            redirect.addFlashAttribute("messageType", "success");
        } catch (Exception e) {
            redirect.addFlashAttribute("message", "Lỗi khi cập nhật: " + e.getMessage());
            redirect.addFlashAttribute("messageType", "error");
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


    //validate ảnh
    private List<String> validateAndUploadImages(List<MultipartFile> images, RedirectAttributes redirect) throws Exception {
        List<String> urls = new java.util.ArrayList<>();

        for (int i = 0; i < images.size(); i++) {
            MultipartFile file = images.get(i);
            String contentType = file.getContentType();
            if (file.getSize() > 5 * 1024 * 1024 ||
                    contentType == null || !contentType.matches("image/(jpeg|jpg|png)")) {
                throw new Exception("Ảnh phải là JPG/PNG và nhỏ hơn 5MB");
            }
            String url = imageUploadService.uploadImage(file);
            urls.add(i == 0 ? url + "|thumbnail" : url);
        }

        return urls;
    }
}
