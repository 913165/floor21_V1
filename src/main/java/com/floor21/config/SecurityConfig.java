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
                                        .requestMatchers(HttpMethod.GET, "/admin/builder-pricing-slabs", "/admin/builder-pricing-slabs/")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/admin/builder-pricing-slabs/import-template")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/admin/milestone-sample-templates",
                                                "/admin/milestone-sample-templates/",
                                                "/admin/milestone-sample-templates/*/download")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/bookings/payment-schedule", "/bookings/payment-schedule/**")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/bookings/allottee-ledger", "/bookings/allottee-ledger/**")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/receipts", "/receipts/", "/receipts/**")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/bookings", "/bookings/")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/bookings/*")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/bookings/*/remove",
                                                "/bookings/*/cancel/confirm")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/admin/projects", "/admin/projects/")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/admin/projects/*/edit")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/admin/projects/*/layout-defaults")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/admin/projects/*/snapshot-buildings")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/admin/projects/new")
                                        .hasRole("SUPER_ADMIN")
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
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/remove-top-floors")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/floor/*/details")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/floor/*/parking-config")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/ground-floor-config")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/ground-floor/layout")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/ground-floor/grid-row")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/ground-floor/grid-col")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/ground-floor-layout-image")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/buildings/*/ground-floor-layout-image")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement/*/config")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement-config")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.DELETE, "/buildings/*/basement/*")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.DELETE, "/buildings/*/basement")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/buildings/*/basements/next-floor")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement/*/layout")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement/layout")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement/*/grid-row")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement/grid-row")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement/*/grid-col")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement/grid-col")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement/*/layout-image")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/basement-layout-image")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/buildings/*/basement/*/layout-image")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.GET, "/buildings/*/basement-layout-image")
                                        .authenticated()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/buildings/*/unit-type-defaults",
                                                "/buildings/*/unit-type-defaults/apply",
                                                "/buildings/*/column-type-defaults",
                                                "/buildings/*/column-type-defaults/apply")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/floor/*/parking-layout")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/floor/*/parking-grid-row")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/flats/floor/*/parking-grid-col")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/parking-layout-image/*")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/layout-image")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/details")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/price")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/partner")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/split-duplex")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/split-merge")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/merge")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/parking-link")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/flats/*/merge-candidates")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/flats/*/linked-parking")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/flats/*/layout-image")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.DELETE, "/flats/*")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/flats/*/activation")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/save")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/buildings/*/edit")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN")
                                        .requestMatchers("/admin/projects/*/buildings/**")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers("/admin/builders/**")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/floor-plans")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN")
                                        .requestMatchers(HttpMethod.POST, "/buildings/*/partner-flats")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers(HttpMethod.GET, "/buildings/*/sales-partners")
                                        .hasRole("SUPER_ADMIN")
                                        .requestMatchers("/buildings", "/buildings/**")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/clients/new")
                                        .hasAnyRole("BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/clients/*/edit")
                                        .hasAnyRole("BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.POST, "/clients/**")
                                        .hasAnyRole("BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/clients")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers(HttpMethod.GET, "/clients/**")
                                        .hasAnyRole("SUPER_ADMIN", "BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers("/settings/vault-access/**")
                                        .hasRole("BUILDER_ADMIN")
                                        .requestMatchers("/vault/**")
                                        .hasAnyRole("BUILDER_ADMIN", "EXECUTIVE")
                                        .requestMatchers("/docs-locker/**")
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
