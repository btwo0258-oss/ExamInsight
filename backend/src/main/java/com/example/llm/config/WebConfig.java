package com.example.llm.config;

import com.example.llm.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")// 所有 /api 开头的接口都需要认证
                .excludePathPatterns("/api/user/register", "/api/user/login", "/api/user/forgot-password", "/api/user/reset-password");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")// 所有接口都支持跨域
                .allowedOriginPatterns("*")// 所有域名都支持跨域
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")// 所有请求头都支持跨域
                .allowCredentials(true)
                .maxAge(3600);
    }
}
