package com.eewms.config;

import com.eewms.services.impl.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity // Vẫn cần cho bảo mật web/URL
@EnableMethodSecurity // Bật bảo mật phương thức
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Bật lại CSRF và dùng Cookie để cấp token ngay cả khi chưa login
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/webhooks/payos", "/api/webhooks/payos/**")
                )
                .authorizeHttpRequests(auth -> auth
                        // ====== Webhook & Public ======
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/payos", "/api/webhooks/payos/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/payos/return", "/payos/return/",
                                "/payos/cancel", "/payos/cancel/").permitAll()
                        // Public/static
                        .requestMatchers("/",
                                "/landing/**","/gioi-thieu","/san-pham/**",
                                "/css/**", "/js/**", "/images/**", "/assets/**",
                                "/login",
                                "/activate", "/activate/**",
                                "/forgot-password",
                                "/reset-password", "/reset-password/**"
                        ).permitAll()

                        // ====== Common authenticated ======
                        .requestMatchers(
                                "/account/info",
                                "/account/update-profile",
                                "/api/tax-lookup/**",
                                "/admin/notifications/**").authenticated()

                        .requestMatchers(HttpMethod.POST, "/admin/debts/*/payos/create")
                        .hasAnyRole("STAFF","MANAGER","ADMIN")

                        .requestMatchers(HttpMethod.GET, "/admin/sales-returns/**").hasAnyRole("ADMIN","MANAGER","STAFF")
                        .requestMatchers( "/admin/sales-returns/**").hasAnyRole("MANAGER","STAFF")

                        // ---------------------------------------------------------
                        // 2) STAFF-only WRITE
                        //    — Không mở rộng cho ADMIN/MANAGER ở phần ghi
                        // ---------------------------------------------------------
                        // (GET ADMIN)
                        .requestMatchers(HttpMethod.GET, "/sale-orders/**").hasAnyRole("ADMIN","STAFF")
                        .requestMatchers("/sale-orders/**").hasRole("STAFF")
                        .requestMatchers(HttpMethod.GET, "/good-issue/**").hasAnyRole("ADMIN","STAFF")
                        .requestMatchers("/good-issue/**").hasRole("STAFF")

                        .requestMatchers(HttpMethod.GET, "/customers/**").hasAnyRole("ADMIN","STAFF")
                        .requestMatchers(HttpMethod.GET, "/customer-list/**").hasAnyRole("ADMIN","STAFF")
                        .requestMatchers("/customers/**").hasRole("STAFF")
                        .requestMatchers("/customer-list/**").hasRole("STAFF")

                        // ---------------------------------------------------------
                        // 3) MANAGER-only
                        // ---------------------------------------------------------
                        // (GET ADMIN)
                        .requestMatchers(HttpMethod.GET, "/admin/warehouse-receipts/**")
                        .hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/admin/warehouse-receipts/**").hasRole("MANAGER")

                        // ---------------------------------------------------------
                        // 4) MANAGER + STAFF
                        // ---------------------------------------------------------
                        // Suppliers (GET ADMIN)
                        .requestMatchers(HttpMethod.GET, "/admin/suppliers/**")
                        .hasAnyRole("ADMIN","MANAGER","STAFF")
                        .requestMatchers("/admin/suppliers/**").hasAnyRole("MANAGER", "STAFF")
                        // Purchase Orders (GET ADMIN)
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-orders/**")
                        .hasAnyRole("ADMIN","MANAGER","STAFF")
                        .requestMatchers("/admin/purchase-orders/**").hasAnyRole("MANAGER", "STAFF")

                        // ---------------------------------------------------------
                        // 5) ADMIN-only
                        // ---------------------------------------------------------
                        .requestMatchers("/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/settings/**").hasRole("ADMIN")

                        // ---------------------------------------------------------
                        // 6) READ-ONLY ADMIN
                        // ---------------------------------------------------------

                        // 6.1 Purchase Requests (GET)
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/create-from-sale-order/**")
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/*")
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/**")
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF") // phủ toàn bộ GET


                        // 6.5 Reports
                        .requestMatchers("/admin/reports/issues/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/admin/reports/receipts/**").hasAnyRole("ADMIN","MANAGER")

                        // 6.6 Products/Combos (GET mở cho cả 3; ghi vẫn Admin+Manager)
                        .requestMatchers(HttpMethod.GET, "/products/**")
                        .hasAnyRole("ADMIN","MANAGER","STAFF")
                        .requestMatchers(HttpMethod.GET, "/products-list/**")
                        .hasAnyRole("ADMIN","MANAGER","STAFF")
                        .requestMatchers("/products/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/product-list/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/combos/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/combo-list/**").hasAnyRole("ADMIN","MANAGER")

                        // 6.7 Debts (GET mở cho cả 3)
                        .requestMatchers(HttpMethod.GET, "/debts/**")
                        .hasAnyRole("ADMIN","MANAGER","STAFF")
                        .requestMatchers("/debts/**").hasAnyRole("MANAGER","STAFF")





                        // ---------------------------------------------------------
                        // 7) Purchase Requests — POST
                        // ---------------------------------------------------------
                        .requestMatchers(HttpMethod.POST, "/admin/purchase-requests")
                        .hasAnyRole("MANAGER", "STAFF")
                        .requestMatchers(HttpMethod.POST, "/admin/purchase-requests/**")
                        .hasAnyRole("MANAGER", "STAFF")

                        // ====== 8) Fallback ======
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e.accessDeniedPage("/error/403"))


                // ✅ Cấu hình login form
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
                        .logoutSuccessUrl("/landing-page")
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
