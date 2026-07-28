package com.dipendra.test.demo.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretCipherTests {
    private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    void encryptsWithRandomIvAndDecryptsWithoutPlaintextLeakage() {
        SecretCipher cipher = new SecretCipher(KEY);
        String secret = "credential-that-must-stay-private";

        String first = cipher.encrypt(secret);
        String second = cipher.encrypt(secret);

        assertThat(first).doesNotContain(secret).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(secret);
        assertThat(cipher.decrypt(second)).isEqualTo(secret);
    }
}
