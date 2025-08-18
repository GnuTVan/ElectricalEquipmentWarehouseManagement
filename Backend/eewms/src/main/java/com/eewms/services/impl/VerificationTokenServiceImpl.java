package com.eewms.services.impl;

import com.eewms.entities.User;
import com.eewms.entities.VerificationToken;
import com.eewms.entities.VerificationToken.TokenType;
import com.eewms.repository.VerificationTokenRepository;
import com.eewms.services.IVerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VerificationTokenServiceImpl implements IVerificationTokenService {

    private final VerificationTokenRepository tokenRepository;

    // TTL mặc định
    private static final Duration DEFAULT_ACTIVATION_TTL = Duration.ofHours(24);

    // ===== API MỚI =====
    @Override
    public String createToken(User user, TokenType type, Duration ttl) {
        // Invalidate tất cả token chưa dùng cùng type
        tokenRepository.invalidateAllByUserAndType(user.getId(), type);

        String tokenStr = UUID.randomUUID().toString();
        VerificationToken token = VerificationToken.builder()
                .token(tokenStr)
                .user(user)
                .type(type)
                .expiryDate(LocalDateTime.now().plus(ttl))
                .used(false)
                .build();
        tokenRepository.save(token);
        return tokenStr;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationToken> getByTokenAndType(String token, TokenType type) {
        return tokenRepository.findByTokenAndTypeAndUsedFalseWithUser(token, type);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValid(String token, TokenType type) {
        return getByTokenAndType(token, type)
                .filter(t -> t.getExpiryDate().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Override
    public Optional<User> consume(String token, TokenType type) {
        var opt = tokenRepository.findByTokenAndTypeAndUsedFalseWithUser(token, type);
        if (opt.isEmpty()) return Optional.empty();

        VerificationToken vt = opt.get();
        if (vt.isUsed() || vt.getExpiryDate().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        vt.setUsed(true);
        tokenRepository.save(vt);
        return Optional.of(vt.getUser());
    }

    @Override
    public void invalidateAll(User user, TokenType type) {
        tokenRepository.invalidateAllByUserAndType(user.getId(), type);
    }

    @Override
    public void markTokenAsUsed(VerificationToken token) {
        token.setUsed(true);
        tokenRepository.save(token);
    }

    // ===== API CŨ (Backward-compat) =====
    @Deprecated
    @Override
    public String createVerificationToken(User user) {
        // Mặc định coi là token kích hoạt
        return createToken(user, TokenType.ACTIVATION, DEFAULT_ACTIVATION_TTL);
    }

    @Deprecated
    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationToken> getByToken(String token) {
        // Giữ hành vi cũ: không phân biệt type, ưu tiên ACTIVATION để hợp lệ kích hoạt
        // (nếu dự án của bạn cần cả RESET thì nên chuyển hết code cũ sang API mới)
        return tokenRepository.findByTokenAndTypeAndUsedFalseWithUser(token, TokenType.ACTIVATION);
    }
}
