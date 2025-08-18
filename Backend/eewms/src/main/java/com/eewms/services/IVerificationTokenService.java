package com.eewms.services;

import com.eewms.entities.User;
import com.eewms.entities.VerificationToken;
import com.eewms.entities.VerificationToken.TokenType;

import java.time.Duration;
import java.util.Optional;

public interface IVerificationTokenService {

    // ===== API mới (chuẩn hoá theo TokenType) =====

    /**
     * Tạo token cho user theo type với TTL chỉ định.
     * Đồng thời invalidate tất cả token chưa dùng cùng type trước đó.
     * @return token string
     */
    String createToken(User user, TokenType type, Duration ttl);

    /**
     * Lấy token theo giá trị + type, chỉ dành cho token chưa dùng.
     */
    Optional<VerificationToken> getByTokenAndType(String token, TokenType type);

    /**
     * Kiểm tra token hợp lệ (đúng type, chưa dùng, chưa hết hạn).
     */
    boolean isValid(String token, TokenType type);

    /**
     * Validate + mark used, trả về User nếu hợp lệ; empty nếu không hợp lệ/hết hạn/đã dùng.
     */
    Optional<User> consume(String token, TokenType type);

    /**
     * Invalidate tất cả token chưa dùng của user theo type (đánh dấu used=true).
     */
    void invalidateAll(User user, TokenType type);

    /**
     * Đánh dấu token đã dùng.
     */
    void markTokenAsUsed(VerificationToken token);

    // ===== Backward-compat (để code cũ compile) =====
    /**
     * Tạo token kích hoạt (mặc định type=ACTIVATION, TTL mặc định).
     * Khuyến nghị chuyển sang createToken(user, TokenType, Duration).
     */
    @Deprecated
    String createVerificationToken(User user);

    /**
     * Lấy token không phân loại type (chỉ dùng cho code cũ).
     * Khuyến nghị chuyển sang getByTokenAndType(token, type).
     */
    @Deprecated
    Optional<VerificationToken> getByToken(String token);
}
