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
    public String list(
            @RequestParam(value = "keyword",    required = false) String keyword,
            @RequestParam(value = "supplierId", required = false) String supplierIdStr,
            @RequestParam(value = "categoryId", required = false) String categoryIdStr,
            @RequestParam(value = "brandId",    required = false) String brandIdStr,
            @RequestParam(value = "status",     required = false) String statusStr,
            Model model
    ) throws InventoryException {

        // 1) Chuẩn hoá keyword
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;

        // 2) Ép kiểu an toàn: "" hoặc null -> null, còn lại -> Long
        Integer supplierId = parseIntegerOrNull(supplierIdStr);
        Integer categoryId = parseIntegerOrNull(categoryIdStr);
        Integer brandId    = parseIntegerOrNull(brandIdStr);


        // 3) Ép Enum an toàn: "" hoặc null -> null
        Product.ProductStatus status = parseEnumOrNull(statusStr, Product.ProductStatus.class);

        // 4) Gọi service (khớp với repo: Page<Product> + Long)
        var page = productService.searchByFilters(
                kw, supplierId, categoryId, brandId, status,
                org.springframework.data.domain.Pageable.unpaged()
        );

        model.addAttribute("products", page.getContent());

        // 5) Giữ lại giá trị filter cho view
        model.addAttribute("keyword", kw);
        model.addAttribute("supplierId", supplierId);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("brandId", brandId);
        model.addAttribute("status", status);
        model.addAttribute("productStatuses", Product.ProductStatus.values());

        // 6) Dữ liệu cho select + modal
        model.addAttribute("productDTO", new ProductFormDTO());
        model.addAttribute("units", settingService.findByTypeAndActive(SettingType.UNIT));
        model.addAttribute("brands", settingService.findByTypeAndActive(SettingType.BRAND));
        model.addAttribute("categories", settingService.findByTypeAndActive(SettingType.CATEGORY));
        model.addAttribute("suppliers", supplierService.findAll());

        return "product/product-list";
    }

    // ===== Helpers =====
    private Integer parseIntegerOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.valueOf(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }


    private <E extends Enum<E>> E parseEnumOrNull(String s, Class<E> type) {
        if (s == null || s.isBlank()) return null;
        try { return Enum.valueOf(type, s.trim()); }
        catch (IllegalArgumentException e) { return null; }
    }

    // xử lý submit modal form thêm sản phẩm
    @PostMapping
    public String create(@ModelAttribute("productDTO") @Valid ProductFormDTO dto,
                         BindingResult br,
                         @RequestParam(value = "images", required = false) List<MultipartFile> images,
                         Model model,
                         RedirectAttributes ra) {
        // Nếu có lỗi validate form
        if (br.hasErrors()) {
            model.addAttribute("productDTO", dto);// gán lại DTO để hiển thị lỗi
            model.addAttribute("hasFormError", true);
            model.addAttribute("products", productService.getAll());
            model.addAttribute("units", settingService.findByTypeAndActive(SettingType.UNIT));
            model.addAttribute("brands", settingService.findByTypeAndActive(SettingType.BRAND));
            model.addAttribute("categories", settingService.findByTypeAndActive(SettingType.CATEGORY));
            model.addAttribute("suppliers", supplierService.findAll()); //lọc active, sau sửa lại thành find active
            return "product/product-list";
        }

        // Nếu không có lỗi validate, tiến hành xử lý upload ảnh
        try {
            List<String> urls = new java.util.ArrayList<>();

            // Lọc bỏ file rỗng (nhiều trình duyệt gửi part rỗng khi không chọn ảnh)
            List<MultipartFile> safeImages = (images == null) ? List.of()
                    : images.stream()
                    .filter(f -> f != null && !f.isEmpty() && f.getSize() > 0)
                    .toList();

            // Nếu người dùng có upload ảnh
            if (!safeImages.isEmpty()) {
                try {
                    urls = validateAndUploadImages(safeImages, ra);
                    dto.setUploadedImageUrls(urls);
                } catch (Exception e) {
                    ra.addFlashAttribute("message", e.getMessage());
                    ra.addFlashAttribute("messageType", "error");
                    return "redirect:/products";
                }
            }

            dto.setUploadedImageUrls(urls); // gán URL đã xử lý vào DTO, có thể rỗng
            productService.create(dto);

            ra.addFlashAttribute("message", "Thêm SP thành công, ID: " + dto.getId() + " - " + dto.getName());
            ra.addFlashAttribute("messageType", "success");
            return "redirect:/products";

        } catch (InventoryException ex) {
            // <<<<<< THÊM: gắn lỗi vào field 'code' và mở lại modal Add
            br.rejectValue("code", "code.duplicate", "Mã sản phẩm đã tồn tại");

            model.addAttribute("productDTO", dto);
            model.addAttribute("hasFormError", true);
            model.addAttribute("products", productService.getAll());
            model.addAttribute("units", settingService.findByTypeAndActive(SettingType.UNIT));
            model.addAttribute("brands", settingService.findByTypeAndActive(SettingType.BRAND));
            model.addAttribute("categories", settingService.findByTypeAndActive(SettingType.CATEGORY));
            model.addAttribute("suppliers", supplierService.findAll());
            return "product/product-list"; // <<<<<< KHÔNG redirect để modal hiển thị lỗi

        } catch (Exception ex) {
            ra.addFlashAttribute("message", "Lỗi khi tạo sản phẩm: " + dto.getName());
            ra.addFlashAttribute("messageType", "error");
            return "redirect:/products";
        }
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
            model.addAttribute("suppliers", supplierService.findAll()); //lọc active, sau sửa lại thành find active
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
            productService.update(id, productForm);

            // Nếu có ảnh cần xóa
            if (deletedImages != null && !deletedImages.isEmpty()) {
                productService.removeImagesByUrls(id, deletedImages);  // service tự xử lý xóa
            }


            redirect.addFlashAttribute("message", "Cập nhật SP thành công, Tên" + productForm.getName());
            redirect.addFlashAttribute("messageType", "success");
            return "redirect:/products";

        } catch (InventoryException ex) {
            // <<<<<< THÊM: gắn lỗi vào field 'code' và mở lại modal Edit
            br.rejectValue("code", "code.duplicate", "Mã sản phẩm đã tồn tại");

            model.addAttribute("productDTO", productForm);
            model.addAttribute("editError", true);
            model.addAttribute("editId", id);
            model.addAttribute("products", productService.getAll());
            model.addAttribute("units", settingService.findByTypeAndActive(SettingType.UNIT));
            model.addAttribute("brands", settingService.findByTypeAndActive(SettingType.BRAND));
            model.addAttribute("categories", settingService.findByTypeAndActive(SettingType.CATEGORY));
            model.addAttribute("suppliers", supplierService.findAll());
            return "product/product-list"; // <<<<<< KHÔNG redirect

        } catch (Exception e) {
            redirect.addFlashAttribute("message", "Lỗi khi cập nhật: " + e.getMessage());
            redirect.addFlashAttribute("messageType", "error");
            return "redirect:/products";
        }
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
    private List<String> validateAndUploadImages(List<MultipartFile> images, RedirectAttributes redirect) throws
            Exception {
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
