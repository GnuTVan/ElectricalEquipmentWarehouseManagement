package com.eewms.repository;

import com.eewms.dto.inventory.StockFlatDTO;
import com.eewms.entities.Product;
import com.eewms.entities.ProductWarehouseStock;
import com.eewms.entities.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ProductWarehouseStockRepository extends JpaRepository<ProductWarehouseStock, Long> {

    @EntityGraph(attributePaths = {"product", "warehouse"})
    @Query(
            value = """
                    select pws
                    from ProductWarehouseStock pws
                    join pws.product pr
                    where pws.warehouse.id = :warehouseId
                      and (:keyword is null or :keyword = ''
                           or lower(pr.code) like lower(concat('%', :keyword, '%'))
                           or lower(pr.name) like lower(concat('%', :keyword, '%')))
                    order by pr.code asc, pr.name asc
                    """,
            countQuery = """
                    select count(pws)
                    from ProductWarehouseStock pws
                    join pws.product pr
                    where pws.warehouse.id = :warehouseId
                      and (:keyword is null or :keyword = ''
                           or lower(pr.code) like lower(concat('%', :keyword, '%'))
                           or lower(pr.name) like lower(concat('%', :keyword, '%')))
                    """
    )
    Page<ProductWarehouseStock> searchByWarehouseAndKeyword(
            @Param("warehouseId") Integer warehouseId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"warehouse"})
    List<ProductWarehouseStock> findByProductOrderByWarehouseNameAsc(Product product);

    @EntityGraph(attributePaths = {"product", "warehouse"})
    Optional<ProductWarehouseStock> findByProductAndWarehouse(Product product, Warehouse warehouse);

    @Query(
            value = """
                    select new com.eewms.dto.inventory.WarehouseStockRowDTO(
                        p.id, p.code, p.name,
                        coalesce(pws.quantity, 0)
                    )
                    from Product p
                    left join ProductWarehouseStock pws
                      on pws.product.id = p.id
                     and pws.warehouse.id = :warehouseId
                    where (:keyword is null or :keyword = ''
                           or lower(p.code) like lower(concat('%', :keyword, '%'))
                           or lower(p.name) like lower(concat('%', :keyword, '%')))
                    order by p.code asc, p.name asc
                    """,
            countQuery = """
                    select count(p)
                    from Product p
                    where (:keyword is null or :keyword = ''
                           or lower(p.code) like lower(concat('%', :keyword, '%'))
                           or lower(p.name) like lower(concat('%', :keyword, '%')))
                    """
    )
    Page<com.eewms.dto.inventory.WarehouseStockRowDTO> pageCatalogWithStockAtWarehouse(
            @Param("warehouseId") Integer warehouseId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // Lock tồn kho để tránh race condition khi 2 staff cùng thao tác xuất nhập kho
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
              select pws from ProductWarehouseStock pws
              where pws.product.id = :productId and pws.warehouse.id = :warehouseId
            """)
    Optional<com.eewms.entities.ProductWarehouseStock>
    findForUpdate(Integer productId, Integer warehouseId);

    // query để map StockFlatDTO
    @Query("""
    select new com.eewms.dto.inventory.StockFlatDTO(
        pws.warehouse.id,
        pws.product.id,
        pws.quantity
    )
    from ProductWarehouseStock pws
""")
    List<StockFlatDTO> findAllFlat();

    // ⬇️ NEW: Tổng tồn theo product trên tất cả kho (dùng để đồng bộ Product.quantity)
    @Query("""
           select coalesce(sum(pws.quantity), 0)
           from ProductWarehouseStock pws
           where pws.product.id = :productId
           """)
    BigDecimal sumQuantityByProductId(@Param("productId") Integer productId);
    List<ProductWarehouseStock> findByWarehouse(Warehouse warehouse);
}
