package com.xenon.qldapm.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Additive, versioned payment tables; the existing sales schema is preserved. */
@Component
@Getter
@RequiredArgsConstructor
public class SePayConfig {
    private final JdbcTemplate jdbc;
    @Value("${app.sepay.enabled:false}") private boolean enabled;
    @Value("${app.sepay.test-mode:true}") private boolean testMode;
    @Value("${app.sepay.bank:Vietcombank}") private String bank;
    @Value("${app.sepay.account:}") private String account;
    @Value("${app.sepay.account-name:}") private String accountName;
    @Value("${app.sepay.webhook-secret:}") private String secret;
    @Value("${app.sepay.expire-minutes:15}") private int expireMinutes;

    @PostConstruct
    void initialize() throws IOException {
        jdbc.execute(new ClassPathResource("db/sepay.sql").getContentAsString(StandardCharsets.UTF_8));
        if (enabled && (secret.length()<24 || !bank.matches("[A-Za-z0-9-]{2,40}")
                || !account.matches("[0-9]{6,40}") || expireMinutes<1 || expireMinutes>60)) {
            throw new IllegalStateException("Invalid SePay configuration: check secret, bank, account and expiry in .env");
        }
    }
}
