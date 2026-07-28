package com.dipendra.test.demo.settings;

import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dipendra.test.demo.settings.AppSettingsService.SettingsUpdate;
import com.dipendra.test.demo.settings.AppSettingsService.SettingsView;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/settings")
public class AppSettingsController {
    private final AppSettingsService settingsService;

    public AppSettingsController(AppSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SettingsView> getSettings() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settingsService.view());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SettingsView> updateSettings(@Valid @RequestBody SettingsUpdate request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(settingsService.update(request));
    }
}
