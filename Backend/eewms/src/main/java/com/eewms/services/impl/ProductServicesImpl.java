package com.eewms.services.impl;

import com.eewms.constant.SettingType;
import com.eewms.dto.*;
import com.eewms.entities.*;
import com.eewms.exception.InventoryException;
import com.eewms.repository.*;
import com.eewms.services.IProductServices;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServicesImpl implements IProductServices {
    private final ProductRepository productRepo;
    private final SettingRepository settingRepo;
    private final ImagesRepository imageRepo;

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

        // --- Lưu product ---
        Product saved = productRepo.save(product);

        //Cập nhật hoặc giữ nguyên ảnh
        List<Image> imgs = List.of();

        if (dto.getUploadedImageUrls() != null && !dto.getUploadedImageUrls().isEmpty()) {
            // Có ảnh mới → xoá ảnh cũ và lưu ảnh mới
            imageRepo.deleteByProductId(saved.getId());

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


}
