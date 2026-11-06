package com.adn.dabaeum;

import com.adn.dabaeum.common.config.LocalSecretsPresenceCheck;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DabaeumApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DabaeumApplication.class);
        application.addListeners(new LocalSecretsPresenceCheck());
        application.run(args);
    }
}
