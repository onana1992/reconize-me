package com.kyc;

import com.kyc.config.KycProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = RedisRepositoriesAutoConfiguration.class)
@EnableConfigurationProperties(KycProperties.class)
public class RecognizMeApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecognizMeApplication.class, args);
    }
}
