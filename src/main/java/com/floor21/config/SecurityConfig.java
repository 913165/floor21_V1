package com.floor21.config;

import com.floor21.security.Floor21UserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final Floor21UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/css/**", "/js/**", "/login", "/error")
                                        .permitAll()
                                        .requestMatchers("/admin/**")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers("/dashboard")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers("/slabs/**")
                                        .hasRole("BUILDER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/save")
                                        .hasRole("BUILDER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/buildings/new", "/buildings/*/edit")
                                        .hasRole("BUILDER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/generate")
                                        .hasRole("BUILDER_ADMIN")
                                        .anyRequest()
                                        .hasAnyRole("BUILDER_ADMIN", "EXECUTIVE"))
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .loginProcessingUrl("/login")
                                        .defaultSuccessUrl("/dashboard", true)
                                        .failureUrl("/login?error=true")
                                        .permitAll())
                .logout(
                        logout ->
                                logout.logoutUrl("/logout")
                                        .logoutSuccessUrl("/login?logout=true")
                                        .invalidateHttpSession(true)
                                        .deleteCookies("JSESSIONID"))
                .csrf(Customizer.withDefaults())
                .userDetailsService(userDetailsService);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
