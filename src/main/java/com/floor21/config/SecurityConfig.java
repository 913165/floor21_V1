package com.floor21.config;

import com.floor21.security.Floor21AuthenticationEntryPoint;
import com.floor21.security.Floor21LoginSuccessHandler;
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
    private final Floor21LoginSuccessHandler loginSuccessHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/actuator/prometheus",
                                                "/actuator/info")
                                        .permitAll()
                                        .requestMatchers("/css/**", "/js/**", "/vendor/**", "/images/**", "/login", "/error")
                                        .permitAll()
                                        .requestMatchers("/admin/**")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers("/dashboard")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers("/profile/**")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.POST, "/impersonate/**")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/generate")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/add-floors")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/floor/*/details")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/details")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/partner")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/split-duplex")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/split-merge")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/merge")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/flats/*/merge-candidates")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.DELETE, "/flats/*")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/activation")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/save")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/buildings/*/edit")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN")
                                        .requestMatchers("/admin/builders/*/buildings/**")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/floor-plans")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/partner-flats")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/buildings/*/sales-partners")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers("/buildings", "/buildings/**")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers("/vault/**")
                                        .hasAnyRole("BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers("/expenses/**")
                                        .hasRole("BUILDER_ADMIN")
                                        .anyRequest()
                                        .hasAnyRole("BUILDER_ADMIN", "EXECUTIVE"))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(new Floor21AuthenticationEntryPoint()))
                .sessionManagement(
                        session -> session.invalidSessionUrl("/login?relogin=true"))
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .loginProcessingUrl("/login")
                                        .successHandler(loginSuccessHandler)
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
