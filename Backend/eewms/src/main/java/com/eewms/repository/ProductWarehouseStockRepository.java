package com.eewms.repository;

import com.eewms.entities.Product;
import com.eewms.entities.ProductWarehouseStock;
import com.eewms.entities.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

}