package com.moamap.user.term.catalog;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 레포의 terms/terms.yml(application.yml에서 import). 약관 목록의 정본이다.
 */
@Validated
@ConfigurationProperties(prefix = "terms")
public record TermsProperties(@Valid List<TermDefinition> documents) {

    public TermsProperties {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
