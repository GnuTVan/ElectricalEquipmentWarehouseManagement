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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements IEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.activation.base-url}")
    private String activationBaseUrl; // ví dụ: http://localhost:8080/activate

    @Value("${app.reset-password.base-url}")
    private String resetPasswordBaseUrl; // ví dụ: http://localhost:8080/reset-password

    private String displayName(User user) {
        String name = user.getFullName();
        return (name == null || name.isBlank()) ? user.getUsername() : name;
    }

    @Override
    public void sendActivationEmail(User user, String token) {
        String to = user.getEmail();
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Email người nhận trống.");
        }
        String subject = "Kích hoạt tài khoản của bạn";
        String activationLink = activationBaseUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        String content = """
                <p>Xin chào <b>%s</b>,</p>
                <p>Bạn đã được tạo tài khoản trong hệ thống quản lý của Công Ty Thiết Bị Điện Hải Phòng .</p>
                <p>Vui lòng nhấn vào đường dẫn dưới đây để kích hoạt tài khoản và đặt mật khẩu mới:</p>
                <p><a href="%s">Kích hoạt tài khoản</a></p>
                <br/>
                <p>Link này sẽ hết hạn sau 24 giờ.</p>
                """.formatted(displayName(user), activationLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Không thể gửi email: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendResetPasswordEmail(User user, String token) {
        String to = user.getEmail();
        if (to == null || to.isBlank()) throw new IllegalArgumentException("Email người nhận trống.");

        String subject = "Đặt lại mật khẩu của bạn";
        String resetLink = resetPasswordBaseUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        String content = """
                <p>Xin chào <b>%s</b>,</p>
                <p>Gần đây có yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>
                <p>Nhấn vào liên kết sau để đặt lại mật khẩu:</p>
                <p><a href="%s">Đặt lại mật khẩu</a></p>
                <br/>
                <p>Liên kết có hiệu lực trong <b>2 giờ</b>. Nếu không phải bạn, vui lòng bỏ qua email này.</p>
                """.formatted(displayName(user), resetLink);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Không thể gửi email đặt lại mật khẩu: " + e.getMessage(), e);
        }
    }
}
