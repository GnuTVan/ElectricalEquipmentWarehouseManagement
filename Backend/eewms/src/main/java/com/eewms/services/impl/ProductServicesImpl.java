package com.eewms.services.impl;

import com.eewms.constant.SettingType;
import com.eewms.dto.*;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.*;
import com.eewms.services.IProductServices;
import com.eewms.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServicesImpl implements IProductServices {
    private final ProductRepository productRepo;
    private final SettingRepository settingRepo;
    private final ImagesRepository imageRepo;
    private final SupplierRepository supplierRepo;

    private final ImageUploadService imageUploadService;

    private SettingDTO mapSetting(Setting s) {
        return SettingDTO.builder()
                .id(s.getId())
                .name(s.getName())
                .type(s.getType())
                .description(s.getDescription())
                .status(s.getStatus())
                .build();
    }

    private List<ImageDTO> mapImages(List<Image> imgs) {
        return imgs.stream().map(i -> ImageDTO.builder()
                        .id(i.getId())
                        .imageUrl(i.getImageUrl())
                        .isThumbnail(i.isThumbnail())
                        .build())
                .collect(Collectors.toList());
    }

    // Hàm chung cho cả create và update

    private ProductDetailsDTO saveOrUpdate(Integer id, ProductFormDTO dto) throws InventoryException {
        System.out.println(">>> saveOrUpdate called with id = " + id);
        Product product;

        if (id != null) {
            // --- CHỈ gọi findById khi update (id != null) ---
            product = productRepo.findById(id)
                    .orElseThrow(() -> new InventoryException("Sản phẩm không tồn tại"));
        } else {
            // --- Create mới: KHÔNG được gọi findById(null) ---
            product = new Product();
        }

        // --- Gán chung các trường từ DTO ---
        product.setCode(dto.getCode());
        product.setName(dto.getName());
        product.setOriginPrice(dto.getOriginPrice());
        product.setListingPrice(dto.getListingPrice());
        product.setDescription(dto.getDescription());
        product.setStatus(dto.getStatus());
        product.setQuantity(dto.getQuantity());

        // --- Lấy Setting liên quan ---
        Setting unit = settingRepo.findById(dto.getUnitId())
                .orElseThrow(() -> new InventoryException("Đơn vị không tồn tại"));
        Setting category = settingRepo.findById(dto.getCategoryId())
                .orElseThrow(() -> new InventoryException("Danh mục không tồn tại"));
        Setting brand = settingRepo.findById(dto.getBrandId())
                .orElseThrow(() -> new InventoryException("Thương hiệu không tồn tại"));
        product.setUnit(unit);
        product.setCategory(category);
        product.setBrand(brand);

        //Map suppliers từ supplierIds
        List<Long> supplierIds = dto.getSupplierIds() == null ? List.of() : dto.getSupplierIds();
        List<Supplier> foundSuppliers = supplierRepo.findAllById(supplierIds);
        //(optional) validate đủ số lượng
        if (foundSuppliers.size() != supplierIds.size()) {
            throw new InventoryException("Một hoặc nhiều nhà cung cấp không tồn tại");
        }
        product.getSuppliers().clear();
        product.getSuppliers().addAll(foundSuppliers);

        // --- Lưu product ---
        Product saved = productRepo.save(product);

        //Cập nhật hoặc giữ nguyên ảnh
        List<Image> imgs = List.of();

        if (dto.getUploadedImageUrls() != null && !dto.getUploadedImageUrls().isEmpty()) {
            // Có ảnh mới → xoá ảnh cũ (Cloudinary + DB)
            List<Image> oldImages = imageRepo.findByProductId(saved.getId());
            for (Image img : oldImages) {
                try {
                    imageUploadService.deleteImageByUrl(img.getImageUrl());
                } catch (Exception e) {
                    System.err.println("Không thể xoá ảnh khỏi Cloudinary: " + img.getImageUrl());
                }
            }
            imageRepo.deleteAll(oldImages);

            // Tạo và lưu ảnh mới
            imgs = dto.getUploadedImageUrls().stream()
                    .map(data -> {
                        boolean isThumb = data.endsWith("|thumbnail");
                        String cleanUrl = isThumb ? data.replace("|thumbnail", "") : data;
                        return Image.builder()
                                .imageUrl(cleanUrl)
                                .isThumbnail(isThumb)
                                .product(saved)
                                .build();
                    })
                    .collect(Collectors.toList());

            imageRepo.saveAll(imgs);
        } else {
            // Không có ảnh mới → giữ nguyên ảnh cũ
            imgs = imageRepo.findByProductId(saved.getId());
        }

        // --- Build và trả về DTO chi tiết ---
        return ProductDetailsDTO.builder()
                .id(saved.getId())
                .code(saved.getCode())
                .name(saved.getName())
                .originPrice(saved.getOriginPrice())
                .listingPrice(saved.getListingPrice())
                .description(saved.getDescription())
                .status(saved.getStatus())
                .quantity(saved.getQuantity())
                .unit(mapSetting(unit))
                .category(mapSetting(category))
                .brand(mapSetting(brand))
                .images(mapImages(imgs))
                .supplierIds(saved.getSuppliers() == null ? List.of()
                        : saved.getSuppliers().stream().map(Supplier::getId).toList())
                .supplierNames(saved.getSuppliers() == null ? List.of()
                        : saved.getSuppliers().stream().map(Supplier::getName).toList())
                .build();
    }


    @Override
    @Transactional
    public ProductDetailsDTO create(ProductFormDTO dto) throws InventoryException {
        // Kiểm tra mã unique
        if (productRepo.existsByCode(dto.getCode())) {
            throw new InventoryException("Mã sản phẩm đã tồn tại");
        }
        return saveOrUpdate(null, dto);
    }

    @Override
    @Transactional
    public ProductDetailsDTO update(Integer id, ProductFormDTO dto) throws InventoryException {
        // Bắt buộc tồn tại trước khi update
        if (!productRepo.existsById(id)) {
            throw new InventoryException("Sản phẩm không tồn tại");
        }
        return saveOrUpdate(id, dto);
    }

    @Override
    @Transactional
    public void delete(Integer id) throws InventoryException {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Sản phẩm không tồn tại"));
        imageRepo.deleteByProductId(id);
        productRepo.delete(p);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailsDTO getById(Integer id) throws InventoryException {
        Product p = productRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Sản phẩm không tồn tại"));
        List<Image> imgs = imageRepo.findByProductId(id);
        return ProductDetailsDTO.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .originPrice(p.getOriginPrice())
                .listingPrice(p.getListingPrice())
                .description(p.getDescription())
                .status(p.getStatus())
                .quantity(p.getQuantity())
                .unit(mapSetting(p.getUnit()))
                .category(mapSetting(p.getCategory()))
                .brand(mapSetting(p.getBrand()))
                .images(mapImages(imgs))
                .supplierIds(p.getSuppliers() == null ? List.of()
                        : p.getSuppliers().stream().map(Supplier::getId).toList())
                .supplierNames(p.getSuppliers() == null ? List.of()
                        : p.getSuppliers().stream().map(Supplier::getName).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDetailsDTO> getAll() throws InventoryException {
        return productRepo.findAll().stream()
                .map(p -> {
                    try {
                        return getById(p.getId());
                    } catch (InventoryException e) {
                        throw new RuntimeException(e);
                    }
                }).collect(Collectors.toList());
    }

    @Override
    public List<SettingDTO> getSettingOptions(SettingType type) {
        return settingRepo.findByType(type).stream()
                .map(this::mapSetting)
                .collect(Collectors.toList());
    }

    // Toggle trạng thái sản phẩm
    @Override
    @Transactional
    public void updateStatus(Integer id, Product.ProductStatus status) throws InventoryException {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Sản phẩm không tồn tại"));

        product.setStatus(status);
        productRepo.save(product);
    }

    // Tim kiếm sản phẩm theo keyword
    @Override
    @Transactional(readOnly = true)
    public List<ProductDetailsDTO> searchByKeyword(String keyword) {
        return productRepo.searchByKeyword(keyword).stream()
                .map(p -> {
                    try {
                        return getById(p.getId());
                    } catch (InventoryException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }

    //chỉ xóa ảnh cũ
    @Transactional
    @Override
    public void removeImagesByUrls(Integer productId, List<String> urls) throws InventoryException {
        List<Image> images = imageRepo.findByProductId(productId);

        if (images == null || images.isEmpty()) return;

        List<Image> toDelete = images.stream()
                .filter(img -> urls.contains(img.getImageUrl()))
                .toList();

        for (Image img : toDelete) {
            try {
                imageUploadService.deleteImageByUrl(img.getImageUrl());
            } catch (Exception e) {
                System.err.println("Không thể xoá ảnh khỏi Cloudinary: " + img.getImageUrl());
            }
        }

        imageRepo.deleteAll(toDelete);
    }

    @Override
    public List<ProductDetailsDTO> getAllActiveProducts() {
        List<Product> products = productRepo.findByStatus(Product.ProductStatus.ACTIVE);

        return products.stream().map(product -> {
            ProductDetailsDTO dto = new ProductDetailsDTO();
            dto.setId(product.getId());
            dto.setCode(product.getCode());
            dto.setName(product.getName());
            dto.setOriginPrice(product.getOriginPrice());
            dto.setListingPrice(product.getListingPrice());
            dto.setDescription(product.getDescription());
            dto.setStatus(product.getStatus());
            dto.setQuantity(product.getQuantity());

            // Tự gán tên danh mục, đơn vị, thương hiệu nếu cần
            dto.setCategory(SettingDTO.builder()
                    .id(product.getCategory().getId())
                    .name(product.getCategory().getName())
                    .build());

            dto.setBrand(SettingDTO.builder()
                    .id(product.getBrand().getId())
                    .name(product.getBrand().getName())
                    .build());

            dto.setUnit(SettingDTO.builder()
                    .id(product.getUnit().getId())
                    .name(product.getUnit().getName())
                    .build());

            // Nếu có ảnh, lấy ảnh đầu tiên hoặc toàn bộ
            if (product.getImages() != null && !product.getImages().isEmpty()) {
                List<ImageDTO> imageDTOs = product.getImages().stream().map(img ->
                        ImageDTO.builder()
                                .id(img.getId())
                                .imageUrl(img.getImageUrl())
                                .build()
                ).toList();

                dto.setImages(imageDTOs);
            }

            return dto;
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDetailsDTO> searchByKeywordAndCategory(String keyword, Long categoryId) {
        List<Product> products = productRepo.searchByKeywordAndCategory(keyword, categoryId);
        return products.stream()
                .map(p -> {
                    try {
                        return getById(p.getId());
                    } catch (InventoryException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }


}