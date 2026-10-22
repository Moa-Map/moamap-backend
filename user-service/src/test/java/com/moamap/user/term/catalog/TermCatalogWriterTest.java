package com.moamap.user.term.catalog;

import static com.moamap.user.term.catalog.TermCatalogSynchronizerTest.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.moamap.user.term.entity.Term;
import com.moamap.user.term.entity.TermKind;
import com.moamap.user.term.repository.TermRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TermCatalogWriter.class)
class TermCatalogWriterTest {

    @Autowired private TermCatalogWriter writer;
    @Autowired private TermRepository termRepository;

    @Test
    void 목록에_있는_약관을_등록한다() {
        writer.sync(List.of(definition("SERVICE", "v1", TermKind.REQUIRED, true),
                definition("POLICY", "v1", TermKind.VIEW_ONLY, true)));

        assertThat(termRepository.findAll())
                .extracting(Term::getCode, Term::getVersion, Term::getKind, Term::isActive, Term::getContentFile)
                .containsExactlyInAnyOrder(
                        tuple("SERVICE", "v1", TermKind.REQUIRED, true, "service-v1.md"),
                        tuple("POLICY", "v1", TermKind.VIEW_ONLY, true, "policy-v1.md"));
    }

    @Test
    void 같은_목록으로_다시_돌려도_결과가_같다() {
        List<TermDefinition> catalog = List.of(definition("SERVICE", "v1", TermKind.REQUIRED, true));

        writer.sync(catalog);
        writer.sync(catalog);

        assertThat(termRepository.findAll()).hasSize(1);
    }

    @Test
    void 개정하면_새_버전이_등록되고_이전_버전은_꺼지며_기존_ID는_그대로다() {
        writer.sync(List.of(definition("SERVICE", "v1", TermKind.REQUIRED, true)));
        Long v1Id = termRepository.findAll().get(0).getId();

        writer.sync(List.of(definition("SERVICE", "v1", TermKind.REQUIRED, false),
                definition("SERVICE", "v2", TermKind.REQUIRED, true)));

        assertThat(termRepository.findById(v1Id).orElseThrow().isActive()).isFalse();
        assertThat(termRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc())
                .extracting(Term::getVersion).containsExactly("v2");
    }

    @Test
    void 제목과_순서는_바꿀_수_있다() {
        writer.sync(List.of(definition("SERVICE", "v1", TermKind.REQUIRED, true)));

        writer.sync(List.of(new TermDefinition("SERVICE", "새 제목", "v1", TermKind.REQUIRED, true, "service-v1.md", 7)));

        Term term = termRepository.findAll().get(0);
        assertThat(term.getTitle()).isEqualTo("새 제목");
        assertThat(term.getDisplayOrder()).isEqualTo(7);
    }

    @Test
    void 등록된_버전의_성격과_본문_파일은_바뀌지_않는다() {
        writer.sync(List.of(definition("SERVICE", "v1", TermKind.REQUIRED, true)));

        writer.sync(List.of(new TermDefinition("SERVICE", "제목", "v1", TermKind.VIEW_ONLY, true, "other.md", 1)));

        Term term = termRepository.findAll().get(0);
        assertThat(term.getKind()).isEqualTo(TermKind.REQUIRED);
        assertThat(term.getContentFile()).isEqualTo("service-v1.md");
    }

    @Test
    void 목록에서_빠진_약관은_지우지_않고_끈다() {
        writer.sync(List.of(definition("SERVICE", "v1", TermKind.REQUIRED, true),
                definition("POLICY", "v1", TermKind.VIEW_ONLY, true)));

        writer.sync(List.of(definition("SERVICE", "v1", TermKind.REQUIRED, true)));

        assertThat(termRepository.findAll()).hasSize(2);
        assertThat(termRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc())
                .extracting(Term::getCode).containsExactly("SERVICE");
    }
}
