package com.eewms.repository;

import com.eewms.entities.User;
import com.eewms.entities.VerificationToken;
import com.eewms.entities.VerificationToken.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    Optional<VerificationToken> findByToken(String token);
    Optional<VerificationToken> findByUser(User user);

    // Tìm token theo token + type + chưa dùng
    Optional<VerificationToken> findByTokenAndTypeAndUsedFalse(String token, TokenType type);

    // === NEW: fetch luôn user để tránh proxy ngoài session ===
    @Query("""
        SELECT t FROM VerificationToken t
        JOIN FETCH t.user u
        WHERE t.token = :token
    """)
    Optional<VerificationToken> findByTokenWithUser(@Param("token") String token);

    @Query("""
        SELECT t FROM VerificationToken t
        JOIN FETCH t.user u
        WHERE t.token = :token AND t.type = :type AND t.used = false
    """)
    Optional<VerificationToken> findByTokenAndTypeAndUsedFalseWithUser(@Param("token") String token,
                                                                       @Param("type") TokenType type);

    // Vô hiệu hóa toàn bộ token chưa dùng của 1 user cho 1 loại type
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE VerificationToken t SET t.used = true " +
            "WHERE t.user.id = :userId AND t.type = :type AND t.used = false")
    void invalidateAllByUserAndType(@Param("userId") Long userId,
                                    @Param("type") TokenType type);
}
