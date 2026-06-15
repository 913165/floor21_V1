package com.floor21.config;

import com.floor21.service.MilestoneSampleTemplateService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MilestoneSampleTemplateSeeder implements ApplicationRunner {

    private final MilestoneSampleTemplateService milestoneSampleTemplateService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            milestoneSampleTemplateService.seedDefaultsIfEmpty();
        } catch (IOException ex) {
            log.warn("Could not seed milestone sample templates: {}", ex.getMessage());
        }
    }
}
