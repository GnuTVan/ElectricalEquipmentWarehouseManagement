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
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
@RequiredArgsConstructor
@Transactional
public class VerificationTokenServiceImpl implements IVerificationTokenService {

    private final VerificationTokenRepository tokenRepository;

    // TTL mặc định
    private static final Duration DEFAULT_ACTIVATION_TTL = Duration.ofHours(24);

    private final PasswordEncoder passwordEncoder;
    // ===== API MỚI =====
    @Override
    public String createToken(User user, TokenType type, Duration ttl) {
        // 🔁 Upsert theo (user,type): nếu có thì UPDATE; nếu chưa có thì INSERT.
        VerificationToken token = tokenRepository.findByUserAndType(user, type)
                .orElseGet(VerificationToken::new);

        String tokenStr = UUID.randomUUID().toString();
        token.setToken(tokenStr);
        token.setUser(user);
        token.setType(type);
        token.setExpiryDate(LocalDateTime.now().plus(ttl));
        token.setUsed(false);

        tokenRepository.save(token);
        return tokenStr;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationToken> getByTokenAndType(String token, TokenType type) {
        // Repo đã gắn @EntityGraph để fetch user
        return tokenRepository.findByTokenAndTypeAndUsedFalse(token, type);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValid(String token, TokenType type) {
        return getByTokenAndType(token, type)
                .filter(t -> t.getExpiryDate() != null && t.getExpiryDate().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Override
    public Optional<User> consume(String token, TokenType type) {
        var opt = tokenRepository.findByTokenAndTypeAndUsedFalse(token, type);
        if (opt.isEmpty()) return Optional.empty();

        VerificationToken vt = opt.get();
        if (vt.isUsed() || vt.getExpiryDate() == null || vt.getExpiryDate().isBefore(LocalDateTime.now())) {
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
        // Hành vi cũ: ưu tiên ACTIVATION
        return tokenRepository.findByTokenAndTypeAndUsedFalse(token, TokenType.ACTIVATION);
    }

    @Override
    public boolean resetPasswordByToken(String tokenStr, TokenType type, String rawPassword) {
        var opt = tokenRepository.findByTokenAndTypeAndUsedFalse(tokenStr, type);
        if (opt.isEmpty()) return false;

        var vt = opt.get();
        if (vt.isUsed() || vt.getExpiryDate() == null || vt.getExpiryDate().isBefore(LocalDateTime.now())) {
            return false;
        }

        // ✅ Thao tác trong TX: chạm vào user an toàn
        var user = vt.getUser(); // đã fetch cùng user nhờ @EntityGraph
        user.setPassword(passwordEncoder.encode(rawPassword));

        vt.setUsed(true);

        // nếu có UserRepository, save user; nếu cascade từ token->user không bật thì cần save user
        // userRepository.save(user);  // nếu bạn có repo user, bật dòng này
        tokenRepository.save(vt);

        return true;
    }

    @Override
    public boolean activateAccountByToken(String tokenStr, String rawPassword) {
        var opt = tokenRepository.findByTokenAndTypeAndUsedFalse(tokenStr, TokenType.ACTIVATION);
        if (opt.isEmpty()) return false;
        var vt = opt.get();
        if (vt.isUsed() || vt.getExpiryDate() == null || vt.getExpiryDate().isBefore(LocalDateTime.now())) return false;

        var user = vt.getUser();
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setEnabled(true);

        vt.setUsed(true);
        // userRepository.save(user); // nếu cần
        tokenRepository.save(vt);
        return true;
    }

}
