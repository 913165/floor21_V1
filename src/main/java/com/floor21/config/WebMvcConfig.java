package com.floor21.config;

import com.floor21.interceptor.ExpensesAccessInterceptor;
import com.floor21.interceptor.TenantInterceptor;
import com.floor21.interceptor.VaultAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantInterceptor tenantInterceptor;
    private final VaultAccessInterceptor vaultAccessInterceptor;
    private final ExpensesAccessInterceptor expensesAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/login", "/css/**", "/js/**", "/error");
        registry.addInterceptor(vaultAccessInterceptor).addPathPatterns("/vault/**");
        registry.addInterceptor(expensesAccessInterceptor).addPathPatterns("/expenses/**");
    }
}
