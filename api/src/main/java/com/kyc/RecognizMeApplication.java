package com.kyc;

import com.kyc.config.KycProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
@EnableConfigurationProperties(KycProperties.class)
@EnableScheduling
public class RecognizMeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecognizMeApplication.class, args);
    }
}
