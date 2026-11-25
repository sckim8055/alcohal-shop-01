package me.nexthub.sool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HealthControllerTest {

    @Autowired
    HealthController controller;

    @Test
    void healthReportsDbAndJavaVersion() {
        var health = controller.health();

        assertEquals("ok", health.status());
        assertEquals(5, health.categoryCount());
    }
}
