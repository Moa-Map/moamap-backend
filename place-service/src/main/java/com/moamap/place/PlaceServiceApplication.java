package com.moamap.place;

import com.moamap.place.ai.GeminiProperties;
import com.moamap.place.map.MapServiceProperties;
import com.moamap.place.kakao.KakaoLocalProperties;
import com.moamap.place.user.UserServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({KakaoLocalProperties.class, MapServiceProperties.class, GeminiProperties.class,
    UserServiceProperties.class})
public class PlaceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlaceServiceApplication.class, args);
    }
}
