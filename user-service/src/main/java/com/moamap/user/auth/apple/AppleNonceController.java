package com.moamap.user.auth.apple;

import com.moamap.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/apple")
@ConditionalOnProperty(prefix = "apple", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AppleNonceController {
    private final AppleNonceService nonces;

    @PostMapping("/nonce")
    @Operation(summary = "Apple 로그인 nonce 발급",
            description = "유효기간은 300초이며 한 번만 사용할 수 있습니다. 반환값을 추가 해싱 없이 Apple 인증 요청의 nonce에 전달합니다.")
    public ApiResponse<AppleNonceResponse> nonce() {
        return ApiResponse.success(nonces.issue());
    }
}
