package com.moamap.user.term.catalog;

import com.moamap.user.term.entity.Term;
import com.moamap.user.term.repository.TermRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * terms.yml 내용으로 term 테이블을 맞춘다. 같은 목록으로 몇 번을 돌려도 결과가 같다.
 *
 * - 목록에 있고 DB에 없는 줄 → 등록
 * - 둘 다 있는 줄 → 제목·순서·활성 여부만 반영. 성격·본문 파일이 다르면 반영하지 않고 경고한다(새 버전으로 올려야 한다)
 * - DB에만 있는 줄 → 끈다. 기록은 남는다
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TermCatalogWriter {

    private final TermRepository termRepository;

    @Transactional
    public void sync(List<TermDefinition> definitions) {
        Map<String, Term> existing = termRepository.findAll().stream()
                .collect(Collectors.toMap(TermCatalogWriter::key, Function.identity()));

        for (TermDefinition definition : definitions) {
            Term term = existing.remove(definition.key());
            if (term == null) {
                termRepository.save(Term.register(definition.code(), definition.title(), definition.version(),
                        definition.kind(), definition.active(), definition.file(), definition.displayOrder()));
                log.info("약관 등록: {} {}", definition.code(), definition.version());
                continue;
            }
            if (term.getKind() != definition.kind() || !term.getContentFile().equals(definition.file())) {
                log.warn("등록된 약관의 성격·본문 파일은 바꿀 수 없어 무시한다. 새 버전으로 올려야 한다: {} {}",
                        definition.code(), definition.version());
            }
            term.updateDisplay(definition.title(), definition.displayOrder(), definition.active());
        }

        existing.values().stream().filter(Term::isActive).forEach(term -> {
            term.deactivate();
            log.warn("terms.yml에 없는 약관을 끈다: {} {}", term.getCode(), term.getVersion());
        });
    }

    private static String key(Term term) {
        return term.getCode() + "@" + term.getVersion();
    }
}
