package com.eewms.repository;

import com.eewms.entities.CustomerRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRefundRepository extends JpaRepository<CustomerRefund, Long> {
    List<CustomerRefund> findBySaleOrderSoIdOrderByIdDesc(Integer soId);
}