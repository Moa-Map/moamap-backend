package com.moamap.map;

import com.moamap.map.client.SeoulOpenApiProperties;
import com.moamap.map.recommendation.RecommendationProperties;
import com.moamap.map.user.UserServiceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableConfigurationProperties({SeoulOpenApiProperties.class, UserServiceProperties.class, RecommendationProperties.class})
@SpringBootApplication
public class MapServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MapServiceApplication.class, args);
    }
}
