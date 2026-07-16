package com.moamap.place.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.ai.dto.ExtractedPlace;
import com.moamap.place.exception.PlaceErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Google Gemini API(generateContent)로 인스타그램 설명글에서 장소 단서를 추출한다.
 * 모델에 JSON 배열만 반환하도록 지시하고, 응답을 파싱한다.
 */
@Slf4j
@Component
public class GeminiPlaceExtractor {

    private static final String SYSTEM_PROMPT = """
        너는 인스타그램 릴스/게시물의 설명글에서 '실제 방문 가능한 장소'를 찾아내는 도우미다.
        입력으로 게시물 설명글이 주어진다. 설명글에 등장하는 음식점, 카페, 명소 등 장소를 찾아라.
        반드시 아래 형식의 JSON 배열만 출력한다. 다른 설명, 마크다운, 코드블록을 절대 포함하지 마라.
        [{"name": "장소 이름", "region": "지역(동/구/시) 또는 빈 문자열"}]
        장소를 찾을 수 없으면 빈 배열 []을 출력한다.
        장소 이름은 검색 가능한 고유명사로, 지역은 '성수동', '서울 강남' 처럼 검색에 도움이 되는 형태로 적는다.
        """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiProperties properties;

    public GeminiPlaceExtractor(RestClient.Builder builder, GeminiProperties properties) {
        // LLM 응답은 다른 외부 호출보다 오래 걸릴 수 있어, 공용 read-timeout(3s)보다 넉넉하게 잡는다.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
            .withReadTimeout(Duration.ofSeconds(15));
        this.restClient = builder
            .baseUrl("https://generativelanguage.googleapis.com")
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .build();
        this.properties = properties;
    }

    public List<ExtractedPlace> extract(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        return parsePlaces(invokeModel(description));
    }

    private String invokeModel(String description) {
        try {
            Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", SYSTEM_PROMPT))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", description)))),
                "generationConfig", Map.of("temperature", 0.0, "maxOutputTokens", 1024)
            );

            String response = restClient.post()
                .uri("/v1beta/models/{model}:generateContent?key={key}", properties.model(), properties.key())
                .body(body)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();
        } catch (RestClientException e) {
            log.error("Gemini 장소 추출 호출 실패", e);
            throw new BusinessException(PlaceErrorCode.GEMINI_EXTRACTION_FAILED);
        } catch (Exception e) {
            log.error("Gemini 응답 파싱 실패", e);
            throw new BusinessException(PlaceErrorCode.GEMINI_EXTRACTION_FAILED);
        }
    }

    /**
     * 모델 출력에서 JSON 배열 부분만 잘라 파싱한다. 모델이 부가 텍스트를 덧붙여도 견딜 수 있게 방어적으로 처리한다.
     */
    private List<ExtractedPlace> parsePlaces(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        int start = output.indexOf('[');
        int end = output.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            log.warn("Gemini 응답에서 JSON 배열을 찾지 못했습니다: {}", output);
            return List.of();
        }
        try {
            ExtractedPlace[] places = objectMapper.readValue(output.substring(start, end + 1), ExtractedPlace[].class);
            return List.of(places);
        } catch (Exception e) {
            log.warn("Gemini 응답 JSON 파싱 실패: {}", output, e);
            return List.of();
        }
    }
}
