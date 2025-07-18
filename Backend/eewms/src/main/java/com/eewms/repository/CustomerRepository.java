package com.eewms.repository;

import com.eewms.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
