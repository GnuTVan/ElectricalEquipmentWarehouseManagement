//package com.eewms.repository.warehouseReceipt;
//
//import com.eewms.entities.Product;
//import com.eewms.entities.ProductWarehouseStock;
//import com.eewms.entities.Warehouse;
//import org.springframework.data.jpa.repository.JpaRepository;
//
//import java.util.Optional;
//
//public interface ProductWarehouseStockRepository extends JpaRepository<ProductWarehouseStock, Long> {
//    Optional<ProductWarehouseStock> findByProductAndWarehouse(Product product, Warehouse warehouse);
//}