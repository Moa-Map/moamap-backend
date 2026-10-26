package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.*;
import com.moamap.common.exception.BusinessException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppleTokenCipherTest {
    static final String KEY_V1 = Base64.getEncoder().encodeToString(new byte[32]);
    static final String KEY_V2 = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private AppleTokenCipher cipher(String version, Map<String, String> keys) {
        return new AppleTokenCipher(new AppleCredentialProperties(version, keys));
    }

    @Test
    void 같은_토큰도_매번_다른_암호문이_되고_원래_내용으로_복호화한다() {
        AppleTokenCipher cipher = cipher("v1", Map.of("v1", KEY_V1));
        var first = cipher.encrypt(1L, "com.moamap.ios", "apple-refresh-secret");
        var second = cipher.encrypt(1L, "com.moamap.ios", "apple-refresh-secret");
        assertThat(first.ciphertext()).doesNotContain("apple-refresh-secret").isNotEqualTo(second.ciphertext());
        assertThat(first.keyVersion()).isEqualTo("v1");
        assertThat(cipher.decrypt(1L, "com.moamap.ios", first)).isEqualTo("apple-refresh-secret");
    }

    @Test
    void 암호문을_변조하거나_다른_회원과_앱에_옮기면_복호화되지_않는다() {
        AppleTokenCipher cipher = cipher("v1", Map.of("v1", KEY_V1));
        var encrypted = cipher.encrypt(1L, "com.moamap.ios", "refresh");
        assertThatThrownBy(() -> cipher.decrypt(2L, "com.moamap.ios", encrypted)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> cipher.decrypt(1L, "other-app", encrypted)).isInstanceOf(BusinessException.class);
        byte[] bytes = Base64.getDecoder().decode(encrypted.ciphertext());
        bytes[bytes.length - 1] ^= 1;
        var changed = new AppleTokenCipher.EncryptedToken(Base64.getEncoder().encodeToString(bytes), "v1");
        assertThatThrownBy(() -> cipher.decrypt(1L, "com.moamap.ios", changed)).isInstanceOf(BusinessException.class);
    }

    @Test
    void 키_교체_후에도_이전_버전을_읽고_새_버전으로_저장한다() {
        var old = cipher("v1", Map.of("v1", KEY_V1)).encrypt(1L, "app", "old-token");
        var rotated = cipher("v2", Map.of("v1", KEY_V1, "v2", KEY_V2));
        assertThat(rotated.decrypt(1L, "app", old)).isEqualTo("old-token");
        assertThat(rotated.encrypt(1L, "app", "new-token").keyVersion()).isEqualTo("v2");
        assertThatThrownBy(() -> cipher("v2", Map.of("v2", KEY_V2)).decrypt(1L, "app", old))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 손상된_암호문과_키버전은_안전한_오류로_거부한다() {
        var cipher = cipher("v1", Map.of("v1", KEY_V1));
        for (var value : new AppleTokenCipher.EncryptedToken[] {
                new AppleTokenCipher.EncryptedToken("bad-ciphertext", "v1"),
                new AppleTokenCipher.EncryptedToken("AAAA", "v1"),
                new AppleTokenCipher.EncryptedToken("AAAA", null)}) {
            assertThatThrownBy(() -> cipher.decrypt(1L, "app", value)).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void 잘못된_암호화_설정은_즉시_거부하고_키를_노출하지_않는다() {
        assertThatThrownBy(() -> cipher("v2", Map.of("v1", KEY_V1))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher("v1", Map.of("v1", "bad-secret")))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("bad-secret");
        assertThatThrownBy(() -> cipher("v1", Map.of("v1", Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new AppleCredentialProperties("v1", Map.of("v1", KEY_V1)).toString()).doesNotContain(KEY_V1);
    }
}
