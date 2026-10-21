package com.moamap.user.term.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약관 한 줄 = 한 버전. 약관을 개정하면 새 줄을 넣고 이전 줄을 끈다(active = false).
 * 그러면 새 줄에 대한 동의 기록이 없는 회원만 다시 동의하게 된다.
 *
 * 줄은 레포의 terms/terms.yml이 정본이고, 서버가 시작할 때 TermCatalogSynchronizer가 맞춘다.
 * 본문은 DB에 넣지 않고 terms/ 아래 .md 파일 경로만 둔다.
 *
 * 성격(kind)과 본문 파일은 등록 후 바꾸지 않는다. 동의 기록이 "이 줄에 동의했다"를 뜻하므로,
 * 의미가 바뀌면 새 버전 줄을 만들어야 한다. 제목·순서·활성 여부만 바꿀 수 있다.
 */
@Entity
@Table(name = "term", uniqueConstraints = @UniqueConstraint(name = "uk_term_code_version", columnNames = {"code", "version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Term {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 약관 종류 식별자(예: SERVICE). 버전이 달라도 같은 약관이면 같은 값이다. */
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 20)
    private String version;

    @Convert(converter = TermKindConverter.class)
    @Column(nullable = false, length = 20)
    private TermKind kind;

    @Column(nullable = false)
    private boolean active;

    /** terms/ 아래 본문 파일 이름(예: service-v1.md). */
    @Column(name = "content_file", nullable = false, length = 100)
    private String contentFile;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Term(String code, String title, String version, TermKind kind, boolean active,
                 String contentFile, int displayOrder) {
        this.code = code;
        this.title = title;
        this.version = version;
        this.kind = kind;
        this.active = active;
        this.contentFile = contentFile;
        this.displayOrder = displayOrder;
    }

    public static Term register(String code, String title, String version, TermKind kind, boolean active,
                                String contentFile, int displayOrder) {
        return new Term(code, title, version, kind, active, contentFile, displayOrder);
    }

    /** 표시 정보와 활성 여부만 바꾼다. 법적 의미가 있는 성격·본문은 바꾸지 않는다. */
    public void updateDisplay(String title, int displayOrder, boolean active) {
        this.title = title;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isRequired() {
        return kind == TermKind.REQUIRED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
