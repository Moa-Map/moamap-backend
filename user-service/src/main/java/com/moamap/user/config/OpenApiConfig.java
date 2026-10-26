package com.moamap.user.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Swagger UI에서 로그인 후 발급받은 JWT를 Authorize 버튼으로 넣을 수 있게 함.
@Configuration
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {

    // 실제 클라이언트는 gateway를 거치므로, Swagger의 "Try it out"도 gateway 주소로 나가야 함.
    @Value("${PUBLIC_GATEWAY_URL:http://localhost:8080}")
    private String publicGatewayUrl;

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .addServersItem(new Server().url(publicGatewayUrl).description("gateway (외부 접근용)"));
    }
}
