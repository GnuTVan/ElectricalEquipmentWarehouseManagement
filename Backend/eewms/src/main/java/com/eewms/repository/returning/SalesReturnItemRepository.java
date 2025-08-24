package com.eewms.repository.returning;
import com.eewms.constant.ReturnStatus;
import com.eewms.entities.SalesReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesReturnItemRepository extends JpaRepository<SalesReturnItem, Long> {
    @Query("""
           select coalesce(sum(i.quantity),0)
           from SalesReturnItem i
           where i.salesReturn.saleOrder.soId = :soId
             and i.product.id = :productId
             and i.salesReturn.status in :statuses
           """)
    Long sumReturnedBySoAndProduct(@Param("soId") Integer soId,
                                   @Param("productId") Integer productId,
                                   @Param("statuses") java.util.List<ReturnStatus> statuses);
}