package com.moamap.user.term.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moamap.user.term.entity.TermKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class TermCatalogSynchronizerTest {

    @Test
    void 같은_약관_버전이_두_번_있으면_서버가_뜨지_않는다() {
        assertThatThrownBy(() -> TermCatalogSynchronizer.validate(List.of(
                definition("SERVICE", "v1", TermKind.REQUIRED, true),
                definition("SERVICE", "v1", TermKind.REQUIRED, false))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("두 번");
    }

    @Test
    void 한_약관의_버전이_둘_다_켜져_있으면_서버가_뜨지_않는다() {
        // 둘 다 켜지면 같은 약관에 두 번 동의해야 하는 상태가 된다.
        assertThatThrownBy(() -> TermCatalogSynchronizer.validate(List.of(
                definition("SERVICE", "v1", TermKind.REQUIRED, true),
                definition("SERVICE", "v2", TermKind.REQUIRED, true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("한 버전만");
    }

    @Test
    void 한_약관의_버전끼리_성격이_다르면_서버가_뜨지_않는다() {
        assertThatThrownBy(() -> TermCatalogSynchronizer.validate(List.of(
                definition("POLICY", "v1", TermKind.VIEW_ONLY, false),
                definition("POLICY", "v2", TermKind.REQUIRED, true))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("성격");
    }

    static TermDefinition definition(String code, String version, TermKind kind, boolean active) {
        return new TermDefinition(code, code + " 제목", version, kind, active,
                code.toLowerCase() + "-" + version + ".md", 1);
    }
}
