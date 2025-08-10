package com.eewms.repository;

import com.eewms.entities.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComboRepository extends JpaRepository<Combo, Long> {
    Optional<Combo> findByCode(String code);
    List<Combo> findByStatus(Combo.ComboStatus status);

    // ==== bổ sung để fix lỗi ở Service ====
    boolean existsByCodeIgnoreCase(String code);
    @Query("select (count(c)>0) from Combo c where lower(c.code)=lower(:code) and c.id <> :id")
    boolean existsByCodeIgnoreCaseAndIdNot(@Param("code") String code, @Param("id") Long id);

    List<Combo> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String nameKeyword, String codeKeyword);

    @Query("SELECT MAX(c.code) FROM Combo c WHERE c.code LIKE :pattern")
    String findMaxCodeLike(@Param("pattern") String pattern);
}