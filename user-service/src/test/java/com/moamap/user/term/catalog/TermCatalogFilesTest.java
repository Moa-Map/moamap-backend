package com.moamap.user.term.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 레포에 실제로 들어 있는 약관 목록(terms/terms.yml)과 본문(.md)을 검사한다.
 * 약관을 추가·개정하는 PR에서 규칙을 어기면 여기서 CI가 실패한다.
 */
class TermCatalogFilesTest {

    private static List<TermDefinition> definitions;

    @BeforeAll
    static void loadCatalog() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("terms", new ClassPathResource("terms/terms.yml"));
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(environment.getPropertySources()::addFirst);
        definitions = new Binder(ConfigurationPropertySources.get(environment))
                .bind("terms", TermsProperties.class)
                .map(TermsProperties::documents)
                .orElse(List.of());
    }

    @Test
    void 약관이_하나_이상_있다() {
        assertThat(definitions).isNotEmpty();
    }

    @Test
    void 각_줄의_형식이_올바르다() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(definitions).allSatisfy(definition -> assertThat(validator.validate(definition)).isEmpty());
    }

    @Test
    void 같은_버전이_두_번_없고_약관마다_켜진_버전은_하나이며_성격이_같다() {
        assertThatCode(() -> TermCatalogSynchronizer.validate(definitions)).doesNotThrowAnyException();
    }

    @Test
    void 모든_본문_파일이_있고_형식_규칙을_지킨다() {
        TermContentLoader loader = new TermContentLoader();

        assertThat(definitions).allSatisfy(definition ->
                assertThatCode(() -> loader.load(definition.file())).as(definition.file()).doesNotThrowAnyException());
    }
}
