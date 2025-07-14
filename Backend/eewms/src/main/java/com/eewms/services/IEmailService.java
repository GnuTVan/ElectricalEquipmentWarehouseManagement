package com.eewms.services;

import com.eewms.entities.User;

public interface IEmailService {
    void sendActivationEmail(User user, String token);
    void sendResetPasswordEmail(User user, String token);
}
