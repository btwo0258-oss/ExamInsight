package com.example.llm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能知识库问答 API")
                        .version("1.0")
                        .description("基于 RAG 的文档问答平台后端接口文档")
                        .contact(new Contact().name("xdx").email("xdx20050329@gmail.com")))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("请填入登录获取的Token（不需要加Bearer前缀，直接粘贴token即可）")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }
}
