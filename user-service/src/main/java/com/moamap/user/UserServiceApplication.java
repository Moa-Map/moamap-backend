package com.moamap.user;

import com.moamap.user.auth.jwt.JwtProperties;
import com.moamap.user.auth.oauth.KakaoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, KakaoProperties.class})
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
