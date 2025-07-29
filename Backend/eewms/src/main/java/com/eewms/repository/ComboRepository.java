package com.eewms.repository;

import com.eewms.entities.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComboRepository extends JpaRepository<Combo, Long> {
    Optional<Combo> findByCode(String code);
    List<Combo> findByStatus(Combo.ComboStatus status);
}