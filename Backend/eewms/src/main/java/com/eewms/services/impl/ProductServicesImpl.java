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
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServicesImpl implements IProductServices {

    private final ProductRepository productRepo;
    private final SettingRepository settingRepo;
    private final ImagesRepository imageRepo;
    private final ImageUploadService imageUploadService;

    // Mapper hỗ trợ chuyển Setting entity → DTO
    private SettingDTO mapSetting(Setting s) {
        return SettingDTO.builder()
                .id(s.getId())
                .name(s.getName())
                .type(s.getType())
                .description(s.getDescription())
                .status(s.getStatus())
                .build();
    }

    // Mapper hỗ trợ chuyển List<Image> → List<ImageDTO>
    private List<ImageDTO> mapImages(List<Image> imgs) {
        return imgs.stream()
                .map(i -> ImageDTO.builder()
                        .id(i.getId())
                        .imageUrl(i.getImageUrl())
                        .build())
                .collect(Collectors.toList());
    }

    private ProductDetailsDTO saveOrUpdate(Integer id, ProductFormDTO dto) throws InventoryException {
        // 1. XỬ LÝ UPLOAD ẢNH: lấy danh sách URL sau khi upload lên Cloudinary
        List<String> urls = Optional.ofNullable(dto.getImageFiles())
                .orElse(List.of())
                .stream()
                .filter(file -> !file.isEmpty())
                .map(imageUploadService::uploadImage)
                .toList();

        // 2. CHUẨN BỊ PRODUCT: tìm hoặc tạo mới
        Product product;
        if (id != null) {
            product = productRepo.findById(id)
                    .orElseThrow(() -> new InventoryException("Sản phẩm không tồn tại"));
        } else {
            product = new Product();
        }

        // 3. GÁN THÔNG TIN CHUNG
        product.setCode(dto.getCode());
        product.setName(dto.getName());
        product.setOriginPrice(dto.getOriginPrice());
        product.setListingPrice(dto.getListingPrice());
        product.setDescription(dto.getDescription());
        product.setStatus(dto.getStatus());
        product.setQuantity(dto.getQuantity());

        // 4. LẤY CÁC SETTING liên quan (unit, category, brand)
        Setting unit = settingRepo.findById(dto.getUnitId())
                .orElseThrow(() -> new InventoryException("Đơn vị không tồn tại"));
        Setting category = settingRepo.findById(dto.getCategoryId())
                .orElseThrow(() -> new InventoryException("Danh mục không tồn tại"));
        Setting brand = settingRepo.findById(dto.getBrandId())
                .orElseThrow(() -> new InventoryException("Thương hiệu không tồn tại"));
        product.setUnit(unit);
        product.setCategory(category);
        product.setBrand(brand);

        // 5. LƯU PRODUCT để sinh ID (nếu mới) hoặc cập nhật
        Product saved = productRepo.save(product);

        // 6. XÓA ẢNH CŨ và LƯU ẢNH MỚI
        imageRepo.deleteByProductId(saved.getId());
        List<Image> imgEntities = urls.stream()
                .map(url -> Image.builder()
                        .imageUrl(url)
                        .product(saved)
                        .build())
                .collect(Collectors.toList());
        imageRepo.saveAll(imgEntities);

        // 7. BUILD VÀ TRẢ VỀ DTO CHI TIẾT
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
                .images(mapImages(imgEntities))
                .build();
    }

    @Override
    @Transactional
    public ProductDetailsDTO create(ProductFormDTO dto) throws InventoryException {
        if (productRepo.existsByCode(dto.getCode())) {
            throw new InventoryException("Mã sản phẩm đã tồn tại");
        }
        return saveOrUpdate(null, dto);
    }

    @Override
    @Transactional
    public ProductDetailsDTO update(Integer id, ProductFormDTO dto) throws InventoryException {
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
}
