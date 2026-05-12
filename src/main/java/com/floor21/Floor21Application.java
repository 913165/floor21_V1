package com.floor21;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Floor21Application {

    public static void main(String[] args) {
        // PostgreSQL rejects the legacy JVM default ID "Asia/Calcutta" on some server builds.
        // "Asia/Kolkata" is the canonical IANA zone for the same offset.
        if ("Asia/Calcutta".equals(ZoneId.systemDefault().getId())) {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Kolkata")));
        }
        SpringApplication.run(Floor21Application.class, args);
    }
}
