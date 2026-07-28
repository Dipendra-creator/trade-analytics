package com.dipendra.test.demo.settings;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dipendra.test.demo.stock.config.DhanProperties;
import com.dipendra.test.demo.stock.service.DhanLiveFeedService;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Size;

@Service
public class AppSettingsService {
    static final String DHAN_ACCESS_TOKEN = "dhan.access-token";
    static final String OPENAI_API_KEY = "openai.api-key";

    private final AppSettingRepository repository;
    private final SecretCipher cipher;
    private final DhanProperties dhanProperties;
    private final DhanLiveFeedService liveFeedService;

    public AppSettingsService(AppSettingRepository repository, SecretCipher cipher,
            DhanProperties dhanProperties, DhanLiveFeedService liveFeedService) {
        this.repository = repository;
        this.cipher = cipher;
        this.dhanProperties = dhanProperties;
        this.liveFeedService = liveFeedService;
    }

    @PostConstruct
    public void applyPersistedDhanToken() {
        read(DHAN_ACCESS_TOKEN).ifPresent(dhanProperties::setAccessToken);
    }

    @Transactional(readOnly = true)
    public SettingsView view() {
        Optional<String> storedDhan = read(DHAN_ACCESS_TOKEN);
        String effectiveDhan = storedDhan.orElse(dhanProperties.getAccessToken());
        Optional<String> openAi = read(OPENAI_API_KEY);
        return new SettingsView(
                configured(effectiveDhan), mask(effectiveDhan),
                openAi.filter(AppSettingsService::configured).isPresent(), mask(openAi.orElse("")));
    }

    @Transactional(readOnly = true)
    public Optional<String> getOpenAiApiKey() {
        return read(OPENAI_API_KEY).filter(AppSettingsService::configured);
    }

    @Transactional
    public SettingsView update(SettingsUpdate request) {
        boolean dhanChanged = false;
        if (request.clearDhanAccessToken()) {
            write(DHAN_ACCESS_TOKEN, "");
            dhanProperties.setAccessToken("");
            dhanChanged = true;
        } else if (configured(request.dhanAccessToken())) {
            String value = request.dhanAccessToken().trim();
            write(DHAN_ACCESS_TOKEN, value);
            dhanProperties.setAccessToken(value);
            dhanChanged = true;
        }

        if (request.clearOpenAiApiKey()) {
            write(OPENAI_API_KEY, "");
        } else if (configured(request.openAiApiKey())) {
            write(OPENAI_API_KEY, request.openAiApiKey().trim());
        }

        if (dhanChanged) {
            liveFeedService.credentialsChanged();
        }
        return view();
    }

    private Optional<String> read(String name) {
        return repository.findByName(name).map(AppSetting::getEncryptedValue).map(cipher::decrypt);
    }

    private void write(String name, String value) {
        AppSetting setting = repository.findByName(name)
                .orElseGet(() -> new AppSetting(name, ""));
        setting.setEncryptedValue(cipher.encrypt(value));
        repository.save(setting);
    }

    private static boolean configured(String value) {
        return value != null && !value.isBlank();
    }

    private static String mask(String value) {
        if (!configured(value)) {
            return "Not configured";
        }
        int visible = Math.min(4, value.length());
        return "••••••••" + value.substring(value.length() - visible);
    }

    public record SettingsView(
            boolean dhanAccessTokenConfigured,
            String dhanAccessTokenMasked,
            boolean openAiApiKeyConfigured,
            String openAiApiKeyMasked) { }

    public record SettingsUpdate(
            @Size(max = 8192) String dhanAccessToken,
            @Size(max = 8192) String openAiApiKey,
            boolean clearDhanAccessToken,
            boolean clearOpenAiApiKey) { }
}
