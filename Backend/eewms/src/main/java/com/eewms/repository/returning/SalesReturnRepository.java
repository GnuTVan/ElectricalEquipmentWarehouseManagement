package com.eewms.repository.returning;

import com.eewms.constant.ReturnStatus;
import com.eewms.entities.SalesReturn;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {
    Optional<SalesReturn> findByCode(String code);
    List<SalesReturn> findBySaleOrder_SoId(Integer soId);
    List<SalesReturn> findBySaleOrder_SoIdAndStatusIn(Integer soId, List<ReturnStatus> statuses);

    @EntityGraph(attributePaths = {"saleOrder"})
    @org.springframework.data.jpa.repository.Query("select sr from SalesReturn sr")
    java.util.List<com.eewms.entities.SalesReturn> findAllWithSaleOrder(org.springframework.data.domain.Sort sort);
}
