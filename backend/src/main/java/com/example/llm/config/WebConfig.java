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
                .allowedOriginPatterns("http://localhost:5173", "http://localhost:5174")// 环境化来源白名单
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")// 添加PATCH方法
                .allowedHeaders("*")// 所有请求头都支持跨域
                .allowCredentials(false)// 使用Bearer Token，不依赖Cookie
                .maxAge(3600);
    }
}
