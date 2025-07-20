package com.eewms.repository;

import com.eewms.entities.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {
    boolean existsByPoCode(String poCode);
}