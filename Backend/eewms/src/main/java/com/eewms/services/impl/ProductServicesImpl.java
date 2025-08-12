package com.eewms.services.impl;

import com.eewms.constant.SettingType;
import com.eewms.dto.ImageDTO;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.dto.ProductFormDTO;
import com.eewms.dto.SettingDTO;
import com.eewms.entities.Image;
import com.eewms.entities.Product;
import com.eewms.entities.Setting;
import com.eewms.entities.Supplier;
import com.eewms.exception.InventoryException;
import com.eewms.repository.ImagesRepository;
import com.eewms.repository.ProductRepository;
import com.eewms.repository.SettingRepository;
import com.eewms.repository.SupplierRepository;
import com.eewms.services.IProductServices;
import com.eewms.services.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
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

    // ===== Helper: map Sort cho landing =====
    private Sort mapSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.unsorted();
        Sort byPrice = switch (sort) {
            case "priceAsc"  -> Sort.by("listingPrice").ascending();
            case "priceDesc" -> Sort.by("listingPrice").descending();
            default -> Sort.unsorted();
        };
        // Tie-break để ổn định khi giá bằng nhau
        return byPrice.and(Sort.by("id").ascending());
    }

    // ===== Helper: DTO mapping =====
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

    private ProductDetailsDTO toLandingDTO(Product product) {
        ProductDetailsDTO dto = new ProductDetailsDTO();
        dto.setId(product.getId());
        dto.setCode(product.getCode());
        dto.setName(product.getName());
        dto.setOriginPrice(product.getOriginPrice());
        dto.setListingPrice(product.getListingPrice());
        dto.setDescription(product.getDescription());
        dto.setStatus(product.getStatus());
        dto.setQuantity(product.getQuantity());

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

        if (product.getImages() != null && !product.getImages().isEmpty()) {
            dto.setImages(product.getImages().stream()
                    .map(img -> ImageDTO.builder()
                            .id(img.getId())
                            .imageUrl(img.getImageUrl())
                            .isThumbnail(img.isThumbnail())
                            .build())
                    .toList());
        }
        return dto;
    }

    // ===== Save/Update chung =====
    private ProductDetailsDTO saveOrUpdate(Integer id, ProductFormDTO dto) throws InventoryException {
        System.out.println(">>> saveOrUpdate called with id = " + id);
        Product product;

        if (id != null) {
            product = productRepo.findById(id)
                    .orElseThrow(() -> new InventoryException("Sản phẩm không tồn tại"));
        } else {
            product = new Product();
        }

        final String code = dto.getCode();

        // UNIQUE CHECK
        if (id == null) {
            if (productRepo.existsByCode(code)) {
                throw new InventoryException("Mã sản phẩm đã tồn tại");
            }
        } else {
            productRepo.findByCode(code).ifPresent(p -> {
                if (!p.getId().equals(id)) {
                    throw new InventoryException("Mã sản phẩm đã tồn tại");
                }
            });
        }

        // Gán trường
        product.setCode(code);
        product.setName(dto.getName());
        product.setOriginPrice(dto.getOriginPrice());
        product.setListingPrice(dto.getListingPrice());
        product.setDescription(dto.getDescription());
        product.setStatus(dto.getStatus());
        product.setQuantity(dto.getQuantity());

        // Settings
        Setting unit = settingRepo.findById(dto.getUnitId())
                .orElseThrow(() -> new InventoryException("Đơn vị không tồn tại"));
        Setting category = settingRepo.findById(dto.getCategoryId())
                .orElseThrow(() -> new InventoryException("Danh mục không tồn tại"));
        Setting brand = settingRepo.findById(dto.getBrandId())
                .orElseThrow(() -> new InventoryException("Thương hiệu không tồn tại"));
        product.setUnit(unit);
        product.setCategory(category);
        product.setBrand(brand);

        // Suppliers
        List<Long> supplierIds = dto.getSupplierIds() == null ? List.of() : dto.getSupplierIds();
        List<Supplier> foundSuppliers = supplierRepo.findAllById(supplierIds);
        if (foundSuppliers.size() != supplierIds.size()) {
            throw new InventoryException("Một hoặc nhiều nhà cung cấp không tồn tại");
        }
        product.getSuppliers().clear();
        product.getSuppliers().addAll(foundSuppliers);

        // Lưu product
        Product saved = productRepo.save(product);

        // Ảnh
        List<Image> imgs;
        if (dto.getUploadedImageUrls() != null && !dto.getUploadedImageUrls().isEmpty()) {
            List<Image> oldImages = imageRepo.findByProductId(saved.getId());
            for (Image img : oldImages) {
                try { imageUploadService.deleteImageByUrl(img.getImageUrl()); }
                catch (Exception e) { System.err.println("Không thể xoá ảnh khỏi Cloudinary: " + img.getImageUrl()); }
            }
            imageRepo.deleteAll(oldImages);

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
            imgs = imageRepo.findByProductId(saved.getId());
        }

        // Build DTO
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

    // ===== CRUD/legacy giữ nguyên =====
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
                    try { return getById(p.getId()); }
                    catch (InventoryException e) { throw new RuntimeException(e); }
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SettingDTO> getSettingOptions(SettingType type) {
        return settingRepo.findByType(type).stream()
                .map(this::mapSetting)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateStatus(Integer id, Product.ProductStatus status) throws InventoryException {
        Product product = productRepo.findById(id)
                .orElseThrow(() -> new InventoryException("Sản phẩm không tồn tại"));
        product.setStatus(status);
        productRepo.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDetailsDTO> searchByKeyword(String keyword) {
        return productRepo.searchByKeyword(keyword).stream()
                .map(p -> {
                    try { return getById(p.getId()); }
                    catch (InventoryException e) { throw new RuntimeException(e); }
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void removeImagesByUrls(Integer productId, List<String> urls) throws InventoryException {
        List<Image> images = imageRepo.findByProductId(productId);
        if (images == null || images.isEmpty()) return;

        List<Image> toDelete = images.stream()
                .filter(img -> urls.contains(img.getImageUrl()))
                .toList();

        for (Image img : toDelete) {
            try { imageUploadService.deleteImageByUrl(img.getImageUrl()); }
            catch (Exception e) { System.err.println("Không thể xoá ảnh khỏi Cloudinary: " + img.getImageUrl()); }
        }
        imageRepo.deleteAll(toDelete);
    }

    // ===== Landing legacy (không phân trang) – giữ nguyên để tương thích =====
    @Override
    public List<ProductDetailsDTO> getAllActiveProducts() {
        List<Product> products = productRepo.findByStatus(Product.ProductStatus.ACTIVE);
        return products.stream().map(this::toLandingDTO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDetailsDTO> searchByKeywordAndCategory(String keyword, Long categoryId) {
        List<Product> products = productRepo.searchByKeywordAndCategory(keyword, categoryId);
        return products.stream()
                .map(p -> {
                    try { return getById(p.getId()); }
                    catch (InventoryException e) { throw new RuntimeException(e); }
                })
                .collect(Collectors.toList());
    }

    // ===== MỚI: Landing + sort + phân trang (DB-side) =====
    @Override
    @Transactional(readOnly = true)
    public Page<ProductDetailsDTO> getAllActiveProducts(String sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, mapSort(sort));
        Page<Product> pageData = productRepo.findByStatus(Product.ProductStatus.ACTIVE, pageable);
        return pageData.map(this::toLandingDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductDetailsDTO> searchByKeywordAndCategory(String keyword, Long categoryId, String sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, mapSort(sort));
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<Product> pageData = productRepo.searchByKeywordAndCategory(kw, categoryId, pageable);
        return pageData.map(this::toLandingDTO);
    }
}
