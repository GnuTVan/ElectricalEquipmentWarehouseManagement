package com.eewms.controller;

import com.eewms.dto.ProductFormDTO;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.constant.SettingType;
import com.eewms.entities.Product;
import com.eewms.exception.InventoryException;
import com.eewms.repository.GoodIssueNoteRepository;
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
import com.eewms.repository.warehouseReceipt.WarehouseReceiptItemRepository;
import com.eewms.entities.Product;

import java.util.*;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import com.eewms.dto.ProductDetailsDTO;

@Controller
@RequestMapping({"/products", "/product-list"})
@RequiredArgsConstructor
public class ProductController {

    private final IProductServices productService;
    private final ISettingServices settingService;
    private final ImageUploadService imageUploadService;
    private final ISupplierService supplierService;
    private final WarehouseReceiptItemRepository warehouseReceiptItemRepository;

    private final GoodIssueNoteRepository goodIssueNoteRepository;



    @GetMapping
    public String list(
            @RequestParam(value="keyword", required=false) String keyword,
            @RequestParam(value="supplierId", required=false) Long supplierId,          // <- Long
            @RequestParam(value="categoryId", required=false) Integer categoryId,
            @RequestParam(value="brandId", required=false) Integer brandId,
            @RequestParam(value="status", required=false) Product.ProductStatus status,
            Model model) {

        // 1) Lấy danh sách sản phẩm (DTO) ban đầu
        List<ProductDetailsDTO> products = (keyword != null && !keyword.isBlank())
                ? productService.searchByKeyword(keyword)
                : productService.getAll();

        // 1.1) Áp dụng filter theo cấu trúc DTO (SettingDTO + List<Long>)
        if (supplierId != null) {
            products = products.stream()
                    .filter(p -> p.getSupplierIds() != null && p.getSupplierIds().contains(supplierId))
                    .toList();
        }
        if (categoryId != null) {
            products = products.stream()
                    .filter(p -> p.getCategory() != null && categoryId.equals(p.getCategory().getId()))
                    .toList();
        }
        if (brandId != null) {
            products = products.stream()
                    .filter(p -> p.getBrand() != null && brandId.equals(p.getBrand().getId()))
                    .toList();
        }
        if (status != null) {
            products = products.stream()
                    .filter(p -> p.getStatus() != null && status.equals(p.getStatus()))
                    .toList();
        }

        model.addAttribute("products", products);
        model.addAttribute("keyword", keyword);
        model.addAttribute("supplierId", supplierId);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("brandId", brandId);
        model.addAttribute("status", status);

        // 2) Tổng nhập HOÀN (RETURNED)
        Map<Integer, Long> inReturned = toMap(warehouseReceiptItemRepository.sumReturnedByProduct());

        // 3) Tồn hiện tại từ DTO
        Map<Integer, Long> onHand = products.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ProductDetailsDTO::getId,
                        p -> p.getQuantity() == null ? 0L : ((Number) p.getQuantity()).longValue(),
                        (a, b) -> a
                ));

        // 4) Suy ra “Hoàn” và “Mới”
        Map<Integer, Long> returnedQtyMap = new java.util.HashMap<>();
        Map<Integer, Long> newQtyMap = new java.util.HashMap<>();
        for (ProductDetailsDTO p : products) {
            int pid = p.getId();
            long total = onHand.getOrDefault(pid, 0L);
            long retIn = inReturned.getOrDefault(pid, 0L);
            long returned = Math.min(retIn, total);
            long fresh = Math.max(0L, total - returned);
            returnedQtyMap.put(pid, returned);
            newQtyMap.put(pid, fresh);
        }
        model.addAttribute("newQtyMap", newQtyMap);
        model.addAttribute("returnedQtyMap", returnedQtyMap);

        // 5) master data cho filter + form
        model.addAttribute("productDTO", new ProductFormDTO());
        model.addAttribute("units", settingService.findByTypeAndActive(SettingType.UNIT));
        model.addAttribute("brands", settingService.findByTypeAndActive(SettingType.BRAND));
        model.addAttribute("categories", settingService.findByTypeAndActive(SettingType.CATEGORY));
        model.addAttribute("suppliers", supplierService.findAll());
        model.addAttribute("productStatuses", Product.ProductStatus.values()); // cần cho select trạng thái

        return "product/product-list";
    }



    /** rows: [productId, sumQty] */
    private static java.util.Map<Integer, Long> toMap(java.util.List<Object[]> rows) {
        if (rows == null) return java.util.Collections.emptyMap();
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                r -> ((Number) r[0]).intValue(),
                r -> ((Number) r[1]).longValue()
        ));
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
