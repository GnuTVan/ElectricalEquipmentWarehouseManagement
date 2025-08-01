package com.eewms.repository;

import com.eewms.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    Optional<Product> findByCode(String code);

    boolean existsByCode(String code);

    // Tìm kiếm theo keyword (cũ)
    @Query("""
        SELECT p FROM Product p
        WHERE
            LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(p.status) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            STR(p.originPrice) LIKE CONCAT('%', :keyword, '%') OR
            STR(p.listingPrice) LIKE CONCAT('%', :keyword, '%') OR
            STR(p.quantity) LIKE CONCAT('%', :keyword, '%') OR
            LOWER(p.brand.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(p.category.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(p.unit.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    List<Product> searchByKeyword(@Param("keyword") String keyword);

    // Tìm kiếm theo keyword và category (mới)
    @Query("""
        SELECT p FROM Product p
        WHERE (:keyword IS NULL OR
               LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(p.status) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               STR(p.originPrice) LIKE CONCAT('%', :keyword, '%') OR
               STR(p.listingPrice) LIKE CONCAT('%', :keyword, '%') OR
               STR(p.quantity) LIKE CONCAT('%', :keyword, '%') OR
               LOWER(p.brand.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(p.category.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(p.unit.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:categoryId IS NULL OR p.category.id = :categoryId)
    """)
    List<Product> searchByKeywordAndCategory(@Param("keyword") String keyword,
                                             @Param("categoryId") Long categoryId);

    List<Product> findByStatus(Product.ProductStatus status);
}
