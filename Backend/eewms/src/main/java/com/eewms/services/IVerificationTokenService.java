package com.eewms.services;

import com.eewms.entities.User;
import com.eewms.entities.VerificationToken;

import java.util.Optional;

public interface IVerificationTokenService {

    String createVerificationToken(User user);

    Optional<VerificationToken> getByToken(String token);

    void markTokenAsUsed(VerificationToken token);
}
