package com.eewms.repository;

import com.eewms.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username); // dùng cho đăng nhập
    boolean existsByUsername(String username);
    boolean existsByUsernameAndIdNot(String username, Long id);

    @Query("""
SELECT DISTINCT u FROM User u
LEFT JOIN u.roles r
WHERE 
    LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(u.address) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
    LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
""")
    Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);

}
