package com.floor21;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires PostgreSQL with Flyway migrations applied")
class Floor21ApplicationTests {

    @Test
    void contextLoads() {}
}
