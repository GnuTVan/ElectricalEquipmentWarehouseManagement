package com.eewms.controller;

import com.eewms.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import(TestSecurityConfig.class)
public class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testLoginPageLoads() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void testLoginSuccess() throws Exception {
        mockMvc.perform(formLogin("/login")
                        .user("admin")
                        .password("admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/")); // Hoặc /dashboard nếu cấu hình khác
    }

    @Test
    void testLoginFailure() throws Exception {
        mockMvc.perform(formLogin("/login")
                        .user("admin")
                        .password("1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }
}

