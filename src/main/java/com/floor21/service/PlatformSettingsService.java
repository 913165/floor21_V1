package com.floor21.service;

import com.floor21.entity.PlatformSetting;
import com.floor21.repository.PlatformSettingRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    public static final String KEY_VAULT_DEFAULT = "default_vault_enabled";
    public static final String KEY_EXPENSES_DEFAULT = "default_expenses_enabled";
    public static final String KEY_RECEIPT_PREFIX = "default_receipt_prefix";
    public static final String KEY_SUPPORT_EMAIL = "support_email";

    private final PlatformSettingRepository settingRepository;
    private final PlatformAuditService auditService;

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        settingRepository.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return settingRepository.findById(key).map(PlatformSetting::getValue).orElse(defaultValue);
    }

    @Transactional
    public void saveAll(Map<String, String> values) {
        values.forEach(this::saveOne);
        auditService.log("SETTINGS_UPDATED", "platform_settings", null, null, null);
    }

    private void saveOne(String key, String value) {
        PlatformSetting setting =
                settingRepository.findById(key).orElseGet(() -> {
                    PlatformSetting s = new PlatformSetting();
                    s.setKey(key);
                    return s;
                });
        setting.setValue(value != null ? value.trim() : "");
        setting.setUpdatedAt(Instant.now());
        settingRepository.save(setting);
    }
}
