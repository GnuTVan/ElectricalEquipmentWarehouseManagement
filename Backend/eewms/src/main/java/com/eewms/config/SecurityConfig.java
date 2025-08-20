package com.eewms.config;

import com.eewms.services.impl.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // ✅ Bật CSRF với Cookie; bỏ qua webhook PayOS
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/webhooks/payos")
                )
                .authorizeHttpRequests(auth -> auth
                        // ====== Webhook & PayOS return/cancel (public) ======
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/payos").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/payos/return", "/payos/return/",
                                "/payos/cancel", "/payos/cancel/").permitAll()

                        // ====== Static & Public pages ======
                        .requestMatchers("/",
                                "/landing/**",
                                "/css/**", "/js/**", "/images/**", "/assets/**",
                                "/login",
                                "/activate", "/activate/**",
                                "/forgot-password",                 // <-- thêm dấu '/'
                                "/reset-password", "/reset-password/**" // <-- thêm dấu '/'
                        ).permitAll()

                        // ====== Common authenticated ======
                        .requestMatchers("/account/info",
                                "/account/update-profile",
                                "/api/tax-lookup/**",
                                "/admin/notifications/**").authenticated()

                        // ====== Purchase Requests: STAFF được 2 GET cụ thể ======
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/create-from-sale-order/**")
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/*")
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF")

                        // ====== ADMIN ======
                        .requestMatchers("/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/settings/**").hasRole("ADMIN")
                        .requestMatchers("/admin/warehouses/**").hasRole("ADMIN")

                        // ====== MANAGER ======
                        .requestMatchers("/admin/suppliers/**").hasAnyRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/**")
                        .hasAnyRole("MANAGER","STAFF") // sau 2 rule Staff ở trên
                        .requestMatchers("/admin/warehouse-receipts/**").hasAnyRole("MANAGER")

                        // ====== MANAGER + STAFF ======
                        .requestMatchers("/admin/purchase-orders/**").hasAnyRole("MANAGER", "STAFF")

                        // ====== MANAGER + ADMIN ======
                        .requestMatchers("/admin/reports/issues/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/admin/reports/receipts/**").hasAnyRole("ADMIN", "MANAGER")

                        // ⚠️ Quan trọng: mở quyền riêng cho STAFF gọi expand combo (đặt TRƯỚC rule /combos/**)
                        .requestMatchers(HttpMethod.POST, "/combos/expand")
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF")

                        // Các trang quản lý combo (giữ như cũ cho ADMIN/MANAGER)
                        .requestMatchers("/products/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/product-list/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/combos/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/combo-list/**").hasAnyRole("ADMIN", "MANAGER")

                        // ====== STAFF ======
                        .requestMatchers("/debts/**").hasAnyRole("MANAGER", "STAFF")
                        .requestMatchers("/sale-orders/**").hasAnyRole("STAFF")
                        .requestMatchers("/good-issue/**").hasAnyRole("STAFF")
                        .requestMatchers("/customers/**").hasAnyRole("STAFF")
                        .requestMatchers("/customer-list/**").hasAnyRole("STAFF")

                        // ====== Purchase Requests (POST) ======
                        .requestMatchers(HttpMethod.POST, "/admin/purchase-requests")
                        .hasAnyRole("MANAGER", "STAFF") // create
                        .requestMatchers(HttpMethod.POST, "/admin/purchase-requests/**")
                        .hasAnyRole("MANAGER")          // /{id}/status, /{id}/update, /{id}/generate-po

                        // ====== Fallback ======
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.accessDeniedPage("/error/403"))

                // ✅ Login form
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/do-login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // ✅ Logout
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                .userDetailsService(userDetailsService);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
