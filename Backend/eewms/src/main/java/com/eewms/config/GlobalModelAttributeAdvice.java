package com.eewms.config;

import com.eewms.entities.User;
import com.eewms.services.IUserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributeAdvice {

    private final IUserService userService;

    @ModelAttribute("loggedInAvatarUrl")
    public String getAvatarUrl(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return null;

        return userService.findByUsername(userDetails.getUsername())
                .map(User::getAvatarUrl)
                .orElse(null);
    }

    @ModelAttribute("avatarTimestamp")
    public long avatarTimestamp(@RequestParam(value = "t", required = false) Long t,
                                HttpSession session) {
        if (t != null) return t;

        Long sessionTs = (Long) session.getAttribute("avatarTimestamp");
        return (sessionTs != null) ? sessionTs : System.currentTimeMillis();
    }
}
