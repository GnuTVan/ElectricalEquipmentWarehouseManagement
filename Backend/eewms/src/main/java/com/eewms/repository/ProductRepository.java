package com.eewms.repository;

import com.eewms.dto.ProductLiteDTO;
import com.eewms.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

    Optional<Product> findByCode(String code);

    @EntityGraph(attributePaths = "suppliers")
    boolean existsByCode(String code);

    // ===== Tìm kiếm theo keyword (cũ, không phân trang) =====
    @EntityGraph(attributePaths = "suppliers")
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

    // ===== Tìm kiếm theo keyword + category (cũ, không phân trang) =====
    @EntityGraph(attributePaths = "suppliers")
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

    // ===== Lấy theo status (cũ, không phân trang) =====
    List<Product> findByStatus(Product.ProductStatus status);

    // ===== MỚI: Lấy theo status + SORT ở DB + PHÂN TRANG (cho landing "tất cả sản phẩm") =====
    // Dùng images để tránh N+1 khi render landing
    @EntityGraph(attributePaths = "images")
    Page<Product> findByStatus(Product.ProductStatus status, Pageable pageable);

    // ===== MỚI: Lọc ACTIVE + search keyword + category + SORT ở DB + PHÂN TRANG (cho landing search) =====
    // Lưu ý: ép chỉ lấy ACTIVE cho landing. Nếu muốn bao cả INACTIVE, bỏ điều kiện status dưới đây.
    @EntityGraph(attributePaths = "images")
    @Query(value = """
            SELECT p FROM Product p
            WHERE (:keyword IS NULL OR
                   LOWER(p.code)        LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(p.name)        LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(p.status)      LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   STR(p.originPrice)   LIKE CONCAT('%', :keyword, '%') OR
                   STR(p.listingPrice)  LIKE CONCAT('%', :keyword, '%') OR
                   STR(p.quantity)      LIKE CONCAT('%', :keyword, '%') OR
                   LOWER(p.brand.name)    LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(p.category.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                   LOWER(p.unit.name)     LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND p.status = com.eewms.entities.Product$ProductStatus.ACTIVE
            """,
            countQuery = """
                    SELECT COUNT(p) FROM Product p
                    WHERE (:keyword IS NULL OR
                           LOWER(p.code)        LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                           LOWER(p.name)        LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                           LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                           LOWER(p.status)      LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                           STR(p.originPrice)   LIKE CONCAT('%', :keyword, '%') OR
                           STR(p.listingPrice)  LIKE CONCAT('%', :keyword, '%') OR
                           STR(p.quantity)      LIKE CONCAT('%', :keyword, '%') OR
                           LOWER(p.brand.name)    LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                           LOWER(p.category.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                           LOWER(p.unit.name)     LIKE LOWER(CONCAT('%', :keyword, '%')))
                      AND (:categoryId IS NULL OR p.category.id = :categoryId)
                      AND p.status = com.eewms.entities.Product$ProductStatus.ACTIVE
                    """)
    Page<Product> searchByKeywordAndCategory(@Param("keyword") String keyword,
                                             @Param("categoryId") Long categoryId,
                                             Pageable pageable);

    // ===== (tuỳ chọn) Lấy theo status + Sort (không phân trang) — dùng ở nơi khác nếu cần =====
    @EntityGraph(attributePaths = "images")
    List<Product> findByStatus(Product.ProductStatus status, Sort sort);

    // ===== Load kèm suppliers để map DTO chi tiết tránh LazyInitialization/N+1 =====
    @EntityGraph(attributePaths = "suppliers")
    Optional<Product> findById(Integer id);

    @EntityGraph(attributePaths = "suppliers")
    List<Product> findAll();

    // top tồn thấp
    List<Product> findTop10ByOrderByQuantityAsc();

    // tổng giá trị tồn kho
    @Query("""
              select coalesce(sum( coalesce(p.listingPrice, 0) * coalesce(p.quantity, 0) ), 0)
              from Product p
            """)
    BigDecimal sumInventoryValue();

    @Query("""
                select distinct p
                from Product p
                left join fetch p.category c
                left join fetch p.brand b
                left join fetch p.unit u
                left join fetch p.images i
                where p.status = com.eewms.entities.Product$ProductStatus.ACTIVE
            """)
    List<Product> findAllActiveWithSetting();

    @EntityGraph(attributePaths = { "brand", "category", "unit", "images", "suppliers" })
    @Query("""
              SELECT p FROM Product p
              LEFT JOIN p.suppliers s
              LEFT JOIN p.category c
              LEFT JOIN p.brand b
              WHERE
                (:keyword IS NULL OR
                  LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                  LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
                AND (:supplierId IS NULL OR s.id = :supplierId)
                AND (:categoryId IS NULL OR c.id = :categoryId)
                AND (:brandId IS NULL OR b.id = :brandId)
                AND (:status IS NULL OR p.status = :status)
            """)
    Page<Product> search(
            @Param("keyword") String keyword,
            @Param("supplierId") Integer supplierId,
            @Param("categoryId") Integer categoryId,
            @Param("brandId") Integer brandId,
            @Param("status") Product.ProductStatus status,
            Pageable pageable
    );

    @Modifying
    @Transactional
    @Query("""
       update Product p
          set p.quantity = p.quantity - :qty
        where p.id = :pid
          and coalesce(p.quantity,0) >= :qty
    """)
    int tryDecreaseOnHand(@Param("pid") Integer productId, @Param("qty") Integer qty);

    // Tìm kiếm cho autocomplete của tạo phiếu chuyển kho(chỉ lấy ACTIVE, không phân trang, giới hạn số kết quả trả về)
    @Query("""
      select p from Product p
      where p.status = com.eewms.entities.Product.ProductStatus.ACTIVE
        and lower(p.name) like lower(concat('%', :q, '%'))
      order by p.name asc
      """)
    List<Product> searchActiveByNameLike(@Param("q") String q, Pageable pageable);

    //query để map ProductLiteDTO
    @Query("""
    select new com.eewms.dto.ProductLiteDTO(
        p.id, p.code, p.name, u.name
    )
    from Product p
    join p.unit u
    where p.status = com.eewms.entities.Product.ProductStatus.ACTIVE
    order by p.code asc
""")
    List<ProductLiteDTO> findAllLite();

    // dùng để save phiếu chuyển kho
    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.unit WHERE p.id = :id")
    Optional<Product> findWithUnit(@Param("id") Integer id);

}


