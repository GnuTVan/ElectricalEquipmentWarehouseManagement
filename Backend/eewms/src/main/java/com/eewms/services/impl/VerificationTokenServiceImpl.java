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
        String token = UUID.randomUUID().toString();

        VerificationToken verificationToken = VerificationToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        tokenRepository.save(verificationToken);
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
