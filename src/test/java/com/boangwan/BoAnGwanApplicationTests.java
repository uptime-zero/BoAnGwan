package com.boangwan;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "anthropic.api-key=dummy",
    "discord.webhook-url=https://discord.com/api/webhooks/dummy/dummy",
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class BoAnGwanApplicationTests {

    @Test
    void contextLoads() {
    }

}
