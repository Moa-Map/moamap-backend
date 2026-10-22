package com.moamap.user.term.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TermMarkdownPolicyTest {

    @Test
    void 제목_목록_허용된_링크로_된_본문은_통과한다() {
        String content = """
                # 서비스 이용약관

                ## 제1조 (목적)
                1. 첫째 항목
                - [공지 보기](https://moamap.example/notice)
                - [메일 문의](mailto:help@moamap.example), [전화](tel:0212345678)
                - <https://moamap.example/policy>
                - 3 < 5, 1 <= 2 처럼 비교 기호가 문장에 섞여도 된다

                [참고]: https://moamap.example/reference
                """;

        assertThat(TermMarkdownPolicy.contentViolations(content)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "<!-- 숨긴 내용 -->",
            "</div>",
            "<iframe src=\"https://evil.example\"></iframe>",
            "<javascript:alert(1)>",
            "a <b>굵게</b>"
    })
    void HTML_태그는_쓸_수_없다(String html) {
        assertThat(TermMarkdownPolicy.contentViolations("# 약관\n" + html)).contains("HTML 태그는 쓸 수 없습니다");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[클릭](javascript:alert(1))",
            "[클릭](JavaScript:alert(1))",
            "[클릭]( javascript:alert(1))",
            "[클릭](data:text/html;base64,PHNjcmlwdD4=)",
            "[클릭](/relative/path)",
            "[클릭](#anchor)",
            "[클릭]()",
            "[참고]: javascript:alert(1)"
    })
    void 허용되지_않은_링크는_쓸_수_없다(String link) {
        assertThat(TermMarkdownPolicy.contentViolations("# 약관\n" + link))
                .anyMatch(violation -> violation.startsWith("허용되지 않은 링크입니다"));
    }

    @Test
    void 이미지는_쓸_수_없다() {
        assertThat(TermMarkdownPolicy.contentViolations("# 약관\n![로고](https://moamap.example/logo.png)"))
                .contains("이미지는 쓸 수 없습니다");
    }

    @Test
    void 빈_본문은_안_된다() {
        assertThat(TermMarkdownPolicy.contentViolations("  \n ")).contains("본문이 비어 있습니다");
    }

    @Test
    void 크기_상한을_넘으면_안_된다() {
        byte[] bytes = ("# 약관\n" + "가".repeat(TermMarkdownPolicy.MAX_BYTES)).getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> TermMarkdownPolicy.validate("big.md", bytes))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("200KB");
    }

    @Test
    void UTF_8이_아니면_안_된다() {
        byte[] eucKr = {(byte) 0xB0, (byte) 0xA1, (byte) 0xFF};

        assertThatThrownBy(() -> TermMarkdownPolicy.validate("euc-kr.md", eucKr))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void 파일_앞의_BOM은_떼고_돌려준다() {
        byte[] withBom = "﻿# 약관".getBytes(StandardCharsets.UTF_8);

        assertThat(TermMarkdownPolicy.validate("bom.md", withBom)).isEqualTo("# 약관");
    }
}
