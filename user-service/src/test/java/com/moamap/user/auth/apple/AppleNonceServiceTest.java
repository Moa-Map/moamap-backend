package com.moamap.user.auth.apple;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.moamap.common.exception.BusinessException;
import com.moamap.user.exception.UserErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class AppleNonceServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final AppleNonceService service = new AppleNonceService(redis);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void 난수_nonce는_5분간_해시된_키로_보관한다() {
        AppleNonceResponse first = service.issue();
        AppleNonceResponse second = service.issue();
        assertThat(first.nonce()).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second.nonce());
        assertThat(first.expiresIn()).isEqualTo(300);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(values, times(2)).set(keys.capture(), eq("1"), eq(Duration.ofMinutes(5)));
        assertThat(keys.getAllValues()).allSatisfy(key ->
                assertThat(key).matches("apple:nonce:[a-f0-9]{64}").doesNotContain(first.nonce()));
    }

    @Test
    void 조회와_삭제를_원자적으로_수행해_두번째_소비는_거부한다() {
        when(values.getAndDelete(anyString())).thenReturn("1").thenReturn(null);
        String nonce = "a".repeat(43);
        service.consume(nonce);
        assertThatThrownBy(() -> service.consume(nonce)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.INVALID_APPLE_TOKEN));
        verify(values, times(2)).getAndDelete(anyString());
        verify(values, never()).get(anyString());
    }

    @Test
    void 만료되거나_발급되지_않은_nonce는_거부한다() {
        assertThatThrownBy(() -> service.consume("a".repeat(43))).isInstanceOf(BusinessException.class);
    }

    @Test
    void Redis_장애는_인증실패와_구분한다() {
        doThrow(new org.springframework.data.redis.RedisConnectionFailureException("unavailable"))
                .when(values).set(anyString(), eq("1"), any(Duration.class));
        assertThatThrownBy(service::issue).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.APPLE_AUTH_UNAVAILABLE));
        when(values.getAndDelete(anyString()))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("unavailable"));
        assertThatThrownBy(() -> service.consume("a".repeat(43)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.APPLE_AUTH_UNAVAILABLE));
    }

    @Test
    void 잘못된_nonce_형식은_Redis에_접근하지_않는다() {
        assertThatThrownBy(() -> service.consume("invalid")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(values);
    }
}
