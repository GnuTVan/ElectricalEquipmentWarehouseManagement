package com.eewms.repository;

import com.eewms.entities.Customer;

import java.util.Collection;
import java.util.Optional;

public interface CustomerRepository {
    Customer save(Customer entity);

    Optional<Customer> findById(Long id);

    Collection<Customer> findAll();

    void deleteById(Long id);
}
