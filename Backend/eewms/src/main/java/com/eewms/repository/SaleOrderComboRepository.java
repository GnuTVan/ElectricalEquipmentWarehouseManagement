package com.eewms.repository;

import com.eewms.entities.SaleOrder;
import com.eewms.entities.SaleOrderCombo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleOrderComboRepository extends JpaRepository<SaleOrderCombo, Long> {
    @Query("""
      select c.combo.id, c.combo.name, coalesce(sum(c.quantity),0)
      from SaleOrderCombo c
      where c.saleOrder.orderDate between :from and :to
      group by c.combo.id, c.combo.name
      order by coalesce(sum(c.quantity),0) desc
    """)
    List<Object[]> topCombos(LocalDateTime from, LocalDateTime to);
    List<SaleOrderCombo> findBySaleOrder(SaleOrder saleOrder);
    void deleteBySaleOrder(SaleOrder saleOrder);
}
