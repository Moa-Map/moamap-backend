package com.moamap.place.mapshare;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.exception.PlaceErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 네이버·카카오·구글의 공개 공유 리스트를 읽는 전용 HTTP 클라이언트.
 *
 * RestClient가 아니라 java.net.http.HttpClient를 쓰는 이유는, 3사 모두 단축링크를
 * 리다이렉트로 풀어 최종 URL에서 ID를 뽑는 것이 추출의 전제이기 때문이다.
 * HttpClient는 response.uri()로 최종 URL을 주지만 RestClient로는 깔끔하게 꺼낼 수 없다.
 */
@Slf4j
@Component
public class MapShareHttpClient {

    /**
     * 모바일 브라우저 UA. 반드시 이걸 써야 한다.
     *
     * 데스크톱 Chrome UA를 보내면 maps.app.goo.gl이 302 대신 JS 인터스티셜 HTML을
     * 반환해서 리다이렉트 추적이 실패한다(실측 확인).
     */
    static final String MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) "
            + "Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .connectTimeout(CONNECT_TIMEOUT)
        .build();

    /** 응답 본문과, 리다이렉트를 모두 따라간 뒤의 최종 URL. */
    public record Result(int status, String body, String finalUrl) {
    }

    /** GET 요청을 보낸다. referer가 null이면 Referer 헤더를 붙이지 않는다. */
    public Result get(String url, String referer) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", MOBILE_UA)
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .timeout(READ_TIMEOUT)
            .GET();
        if (referer != null) {
            builder.header("Referer", referer);
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), response.body(), response.uri().toString());
        } catch (IOException e) {
            log.warn("지도 공유 링크 요청 실패: url={}", url, e);
            throw new BusinessException(PlaceErrorCode.SHARE_LINK_EXTRACTION_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(PlaceErrorCode.SHARE_LINK_EXTRACTION_FAILED);
        }
    }

    /** 리다이렉트를 따라간 최종 URL만 필요할 때. */
    public String resolveFinalUrl(String url) {
        return get(url, null).finalUrl();
    }

    /**
     * GET 후 200이 아니면 예외를 던지고, 200이면 본문을 반환한다.
     * apiLabel은 로그에 남길 API 이름이다(예: "네이버 bookmarks API").
     */
    public String getBodyOrThrow(String url, String referer, String apiLabel) {
        Result result = get(url, referer);
        if (result.status() != 200) {
            log.warn("{} 응답이 {}입니다. 응답 앞부분: {}", apiLabel, result.status(),
                result.body().substring(0, Math.min(500, result.body().length())));
            throw new BusinessException(PlaceErrorCode.SHARE_LINK_EXTRACTION_FAILED);
        }
        return result.body();
    }
}
