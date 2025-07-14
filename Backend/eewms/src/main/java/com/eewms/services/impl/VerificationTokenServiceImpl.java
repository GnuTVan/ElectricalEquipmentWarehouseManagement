package com.eewms.services.impl;

import com.eewms.entities.User;
import com.eewms.entities.VerificationToken;
import com.eewms.repository.VerificationTokenRepository;
import com.eewms.services.IVerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationTokenServiceImpl implements IVerificationTokenService {

    private final VerificationTokenRepository tokenRepository;

    @Override
    public String createVerificationToken(User user) {
        // Nếu đã có token chưa dùng → tái sử dụng
        Optional<VerificationToken> existingTokenOpt = tokenRepository.findByUser(user);

        if (existingTokenOpt.isPresent()) {
            VerificationToken existingToken = existingTokenOpt.get();
            if (!existingToken.isUsed() && existingToken.getExpiryDate().isAfter(LocalDateTime.now())) {
                return existingToken.getToken(); // ✅ Tái sử dụng token
            } else {
                tokenRepository.delete(existingToken); // ✅ Token hết hạn hoặc đã dùng → xóa
            }
        }

        // ✅ Tạo token mới
        String token = UUID.randomUUID().toString();
        VerificationToken newToken = VerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        tokenRepository.save(newToken);
        return token;
    }

    @Override
    public Optional<VerificationToken> getByToken(String token) {
        return tokenRepository.findByToken(token);
    }

    @Override
    public void markTokenAsUsed(VerificationToken token) {
        token.setUsed(true);
        tokenRepository.save(token);
    }
}
