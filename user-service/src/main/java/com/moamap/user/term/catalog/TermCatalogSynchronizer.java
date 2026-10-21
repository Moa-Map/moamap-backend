package com.moamap.user.term.catalog;

import com.moamap.user.term.entity.TermKind;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 서버가 시작할 때 terms.yml을 검사하고 term 테이블에 반영한다.
 *
 * 목록이 잘못됐거나(같은 약관의 버전이 둘 다 켜짐 등) 본문 파일이 규칙을 어기면 서버가 뜨지 않는다.
 * 같은 검사를 CI 테스트에서도 돌리므로 정상 흐름에서는 여기까지 오지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TermCatalogSynchronizer implements ApplicationRunner {

    private final TermsProperties properties;
    private final TermContentLoader contentLoader;
    private final TermCatalogWriter writer;

    @Override
    public void run(ApplicationArguments args) {
        List<TermDefinition> definitions = properties.documents();
        validate(definitions);
        // 본문을 미리 읽어 형식을 확인한다. 지난 버전도 "그때 동의한 문구"로 조회되므로 함께 확인한다.
        definitions.forEach(definition -> contentLoader.load(definition.file()));

        try {
            writer.sync(definitions);
        } catch (DataIntegrityViolationException e) {
            // 서버 여러 대가 동시에 떠서 다른 서버가 먼저 같은 줄을 등록한 경우. 같은 목록이라 결과는 같다.
            log.warn("다른 서버가 먼저 약관을 등록해 이번 반영은 건너뛴다.");
        }
    }

    static void validate(List<TermDefinition> definitions) {
        Set<String> keys = new HashSet<>();
        Set<String> activeCodes = new HashSet<>();
        Map<String, TermKind> kindByCode = new HashMap<>();
        for (TermDefinition definition : definitions) {
            if (!keys.add(definition.key())) {
                throw new IllegalStateException("terms.yml에 같은 약관 버전이 두 번 있습니다: " + definition.key());
            }
            if (definition.active() && !activeCodes.add(definition.code())) {
                throw new IllegalStateException("terms.yml에서 한 약관은 한 버전만 켤 수 있습니다: " + definition.code());
            }
            TermKind kind = kindByCode.putIfAbsent(definition.code(), definition.kind());
            if (kind != null && kind != definition.kind()) {
                throw new IllegalStateException("한 약관의 버전들은 성격(kind)이 같아야 합니다: " + definition.code());
            }
        }
    }
}
