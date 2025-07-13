package com.eewms.services.impl;

import com.eewms.entities.User;
import com.eewms.services.IEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements IEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.activation.base-url}")
    private String activationBaseUrl; // ví dụ: http://localhost:8080/activate

    @Override
    public void sendActivationEmail(User user, String token) {
        String to = user.getEmail();
        String subject = "Kích hoạt tài khoản của bạn";
        String activationLink = activationBaseUrl + "?token=" + token;

        String content = """
                <p>Xin chào <b>%s</b>,</p>
                <p>Bạn đã được tạo tài khoản trong hệ thống quản lý của Công Ty Thiết Bị Điện Hải Phòng .</p>
                <p>Vui lòng nhấn vào đường dẫn dưới đây để kích hoạt tài khoản và đặt mật khẩu mới:</p>
                <p><a href="%s">Kích hoạt tài khoản</a></p>
                <br/>
                <p>Link này sẽ hết hạn sau 24 giờ.</p>
                """.formatted(user.getFullName(), activationLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // true = gửi HTML

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Không thể gửi email: " + e.getMessage());
        }
    }
}
