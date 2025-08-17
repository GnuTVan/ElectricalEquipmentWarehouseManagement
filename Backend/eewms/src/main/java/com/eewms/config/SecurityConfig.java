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
                // ✅ Bật lại CSRF và dùng Cookie để cấp token ngay cả khi chưa login
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/api/webhooks/payos")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/payos").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/payos/return", "/payos/return/",
                                "/payos/cancel", "/payos/cancel/").permitAll()
                        // Public/static
                        .requestMatchers("/", "/landing/**",
                                "/css/**", "/js/**", "/images/**", "/assets/**",
                                "/login", "/activate", "/activate/**").permitAll()

                        // Common authenticated
                        .requestMatchers("/account/info", "/account/update-profile", "/api/tax-lookup/**").authenticated()
                        //Purchase Requests: STAFF được 2 GET cụ thể (đặt TRƯỚC rule rộng cho Manager)
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/create-from-sale-order/**")
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF")
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/*") // ví dụ /admin/purchase-requests/{id}
                        .hasAnyRole("ADMIN", "MANAGER", "STAFF")

                        //ADMIN
                        .requestMatchers("/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/settings/**").hasRole("ADMIN")
                        .requestMatchers("/admin/warehouses/**").hasRole("ADMIN")
                        .requestMatchers("/**").permitAll()

                        //MANAGER
                        .requestMatchers("/admin/suppliers/**").hasAnyRole("MANAGER")
                        .requestMatchers("/admin/purchase-orders/**").hasAnyRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/admin/purchase-requests/**").hasAnyRole("MANAGER") // sau 2 rule Staff ở trên
                        .requestMatchers("/admin/warehouse-receipts/**").hasAnyRole("MANAGER")


                        //MANAGER + ADMIN
                        .requestMatchers("/admin/reports/issues/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/admin/reports/receipts/**").hasAnyRole("ADMIN", "MANAGER")
                        .requestMatchers("/products/**").hasAnyRole("ADMIN","MANAGER")
                        .requestMatchers("/product-list/**").hasAnyRole("ADMIN","MANAGER")
                        //STAFF
                        .requestMatchers("/debts/**").hasAnyRole("MANAGER", "STAFF") //Manager cũng được xem công nợ
                        .requestMatchers("/sale-orders/**").hasAnyRole("STAFF")
                        .requestMatchers("/good-issue/**").hasAnyRole("STAFF")
                        .requestMatchers("/customers/**").hasAnyRole("STAFF")
                        .requestMatchers("/customer-list/**").hasAnyRole("STAFF")
                        .requestMatchers("/combos/**").hasAnyRole("STAFF")
                        .requestMatchers("/combo-list/**").hasAnyRole("STAFF")


                        //Purchase Requests (Y/C Mua) (POST): Staff chỉ được POST create; các POST khác chỉ Manager/Admin
                        .requestMatchers(HttpMethod.POST, "/admin/purchase-requests").hasAnyRole("MANAGER", "STAFF") // create PostMapping không path
                        .requestMatchers(HttpMethod.POST, "/admin/purchase-requests/**").hasAnyRole("MANAGER")      // /{id}/status, /{id}/update, /{id}/generate-po


                        // Fallback
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
