package com.moamap.user.term.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * terms/ 아래 약관 본문(.md)을 읽는다. 파일은 배포 단위로 고정이라 한 번 읽으면 메모리에 둔다.
 */
@Component
public class TermContentLoader {

    static final String BASE_PATH = "terms/";

    /** terms.yml 검증과 같은 규칙. 경로 이동(../)이 섞일 수 없게 파일 이름만 받는다. */
    private static final Pattern FILE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,90}\\.md");

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * @throws IllegalStateException 파일이 없거나 형식 규칙(TermMarkdownPolicy)을 어긴 경우
     */
    public String load(String file) {
        return cache.computeIfAbsent(file, TermContentLoader::read);
    }

    private static String read(String file) {
        if (!FILE_NAME.matcher(file).matches()) {
            throw new IllegalStateException("약관 파일 이름이 올바르지 않습니다: " + file);
        }
        ClassPathResource resource = new ClassPathResource(BASE_PATH + file);
        if (!resource.exists()) {
            throw new IllegalStateException("약관 파일이 없습니다: " + BASE_PATH + file);
        }
        try (InputStream in = resource.getInputStream()) {
            // 상한보다 1바이트만 더 읽어, 큰 파일을 통째로 메모리에 올리지 않고도 초과를 알아챈다.
            byte[] bytes = in.readNBytes(TermMarkdownPolicy.MAX_BYTES + 1);
            return TermMarkdownPolicy.validate(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("약관 파일을 읽지 못했습니다: " + file, e);
        }
    }
}
