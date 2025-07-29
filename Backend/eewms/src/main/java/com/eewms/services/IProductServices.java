package com.eewms.services;

import com.eewms.constant.SettingType;
import com.eewms.dto.ProductFormDTO;
import com.eewms.dto.ProductDetailsDTO;
import com.eewms.dto.SettingDTO;
import com.eewms.entities.Product;
import com.eewms.exception.InventoryException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface IProductServices {
    ProductDetailsDTO create(ProductFormDTO dto) throws InventoryException;
    ProductDetailsDTO update(Integer id, ProductFormDTO dto) throws InventoryException;
    void delete(Integer id) throws InventoryException;
    ProductDetailsDTO getById(Integer id) throws InventoryException;
    List<ProductDetailsDTO> getAll() throws InventoryException;

    // toggle status
    void updateStatus(Integer id, Product.ProductStatus status) throws InventoryException;

    // để lấy options trong dropdown
    List<SettingDTO> getSettingOptions(SettingType type);

    // tìm kiếm sản phẩm theo từ khóa
    List<ProductDetailsDTO> searchByKeyword(String keyword);

    // tìm kiếm theo từ khóa kết hợp danh mục
    List<ProductDetailsDTO> searchByKeywordAndCategory(String keyword, Long categoryId);

    // chỉ xóa ảnh cũ
    @Transactional
    void removeImagesByUrls(Integer productId, List<String> urls) throws InventoryException;

    // landing-page
    List<ProductDetailsDTO> getAllActiveProducts();
}
