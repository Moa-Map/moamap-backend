package com.moamap.place.service;

import java.math.BigDecimal;
import java.util.List;
import com.moamap.common.exception.BusinessException;
import com.moamap.place.dto.PlaceBulkCreateRequest;
import com.moamap.place.dto.PlaceBulkCreateResponse;
import com.moamap.place.entity.Place;
import com.moamap.place.entity.PlaceSourceType;
import com.moamap.place.entity.PlaceStatus;
import com.moamap.place.event.PlaceCountEventPublisher;
import com.moamap.place.exception.PlaceErrorCode;
import com.moamap.place.map.MapClient;
import com.moamap.place.map.dto.MapMemberResponse;
import com.moamap.place.map.dto.MapMemberRole;
import com.moamap.place.map.dto.MapType;
import com.moamap.place.repository.PlaceRepository;
import com.moamap.place.user.UserClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

/**
 * 실제 H2 DB에 대해 일괄 등록의 건별 부분 성공을 검증한다.
 *
 * 테스트를 트랜잭션으로 감싸면 안 된다. @DataJpaTest는 기본적으로 테스트마다
 * 트랜잭션을 열고 롤백하는데, 그러면 REQUIRES_NEW가 실제로 커밋됐는지를 검증할 수 없다.
 * Propagation.NOT_SUPPORTED로 테스트 트랜잭션을 끄고, 커밋된 데이터는 @BeforeEach에서 직접 지운다.
 */
@DataJpaTest
@Import({PlaceService.class, PlaceBulkRegistrar.class})
@TestPropertySource(properties = "spring.jpa.properties.hibernate.default_schema=")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlaceBulkCreateTest {

    private static final Long MAP_ID = 10L;
    private static final Long USER_ID = 1L;

    @Autowired
    private PlaceService placeService;

    @Autowired
    private PlaceRepository placeRepository;

    @MockitoBean
    private MapClient mapClient;

    // createBulk는 UserClient를 호출하지 않는다. PlaceService 생성자 의존성을 채우기 위한 mock이다.
    @MockitoBean
    private UserClient userClient;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // createBulk는 이 발행기를 루프 종료 후 직접 호출한다(청사진 3-3(가)). 이 테스트 스위트는
    // 부분 성공/커밋 자체가 관심사라 발행 여부는 PlaceServiceTest에서 다루고, 여기서는 컨텍스트가
    // 뜨는 데 필요한 협력자만 mock으로 채운다.
    @MockitoBean
    private PlaceCountEventPublisher placeCountEventPublisher;

    @BeforeEach
    void setUp() {
        placeRepository.deleteAll();
        given(mapClient.getMemberInfo(anyLong(), anyLong()))
            .willReturn(new MapMemberResponse(MapType.PRIVATE, MapMemberRole.MEMBER));
    }

    // 이 테스트는 NOT_SUPPORTED라 실제로 커밋한다. 공유 컨텍스트의 H2에 행을 남기면
    // 뒤에 도는 다른 테스트(예: 동시성 테스트의 count 검증)를 오염시키므로 직접 지운다.
    @AfterEach
    void tearDown() {
        placeRepository.deleteAll();
    }

    private PlaceBulkCreateRequest.Item item(String name, String kakaoPlaceId) {
        return new PlaceBulkCreateRequest.Item(name, "서울 동작구 상도동", "서울 동작구 상도로 369",
            new BigDecimal("37.495853"), new BigDecimal("126.957818"), "음식점 > 분식",
            kakaoPlaceId, PlaceSourceType.NAVER_MAP, "https://naver.me/xAbC1234", "메모", List.of(), List.of());
    }

    private PlaceBulkCreateRequest.Item itemWithPhotos(String name, String kakaoPlaceId, List<String> photoUrls) {
        return new PlaceBulkCreateRequest.Item(name, "서울 동작구 상도동", "서울 동작구 상도로 369",
            new BigDecimal("37.495853"), new BigDecimal("126.957818"), "음식점 > 분식",
            kakaoPlaceId, PlaceSourceType.NAVER_MAP, "https://naver.me/xAbC1234", "메모", List.of(), photoUrls);
    }

    /*
     * 이 스위트는 REQUIRES_NEW 커밋을 확인하려고 테스트 트랜잭션을 끈 상태(NOT_SUPPORTED)라,
     * 지연 로딩인 @ElementCollection을 그냥 읽으면 LazyInitializationException이 난다.
     * 사진 검증은 세션 안에서 읽어야 한다.
     */
    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    /*
     * photoUrls는 tags와 달리 "인덱스 0 = 대표 사진" 계약이 있어(청사진 5장) 받은 순서가
     * 그대로 저장돼야 한다. 단건 등록과 같은 결과가 나오는지 실제 커밋까지 태워 확인한다.
     */

    @Test
    void createBulk는_사진을_받은_순서_그대로_저장한다() {
        List<String> photos = List.of(
            "https://cdn.moamap.com/places/10/rep.jpg",
            "https://cdn.moamap.com/places/10/2.jpg",
            "https://cdn.moamap.com/places/10/3.jpg");
        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID,
            List.of(itemWithPhotos("청년다방", "111", photos)));

        placeService.createBulk(request, USER_ID);

        List<String> savedPhotos = inTransaction(
            () -> List.copyOf(placeRepository.findAll().get(0).getPhotoUrls()));
        assertThat(savedPhotos).containsExactlyElementsOf(photos);
    }

    @Test
    void createBulk는_사진을_안_보내면_빈_리스트로_저장한다() {
        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID,
            List.of(itemWithPhotos("청년다방", "111", null)));

        placeService.createBulk(request, USER_ID);

        List<String> savedPhotos = inTransaction(
            () -> List.copyOf(placeRepository.findAll().get(0).getPhotoUrls()));
        assertThat(savedPhotos).isEmpty();
    }

    @Test
    void createBulk는_건별로_다른_사진을_각자에게_저장한다() {
        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID, List.of(
            itemWithPhotos("청년다방", "111", List.of("https://cdn.moamap.com/places/10/a.jpg")),
            itemWithPhotos("숭실대학교", "222", List.of(
                "https://cdn.moamap.com/places/10/b.jpg", "https://cdn.moamap.com/places/10/c.jpg"))));

        placeService.createBulk(request, USER_ID);

        assertThat(inTransaction(() -> placeRepository.findAll().stream()
                .map(p -> java.util.Map.entry(p.getKakaoPlaceId(), List.copyOf(p.getPhotoUrls())))
                .toList()))
            .extracting(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue)
            .containsExactlyInAnyOrder(
                tuple("111", List.of("https://cdn.moamap.com/places/10/a.jpg")),
                tuple("222", List.of(
                    "https://cdn.moamap.com/places/10/b.jpg", "https://cdn.moamap.com/places/10/c.jpg")));
    }

    @Test
    void createBulk는_모두_새_장소면_전부_등록한다() {
        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID,
            List.of(item("청년다방", "111"), item("숭실대학교", "222")));

        PlaceBulkCreateResponse response = placeService.createBulk(request, USER_ID);

        assertThat(response.requested()).isEqualTo(2);
        assertThat(response.created()).isEqualTo(2);
        assertThat(response.skipped()).isZero();
        assertThat(placeRepository.count()).isEqualTo(2);
    }

    @Test
    void createBulk는_중복이_섞여_있어도_나머지를_실제로_커밋한다() {
        // given - 이 테스트가 이 Task의 핵심이다.
        // PlaceBulkRegistrar에 REQUIRES_NEW가 없으면 유니크 제약 위반이 트랜잭션 전체를
        // rollback-only로 만들어, 나머지 건이 커밋되지 않고 여기서 깨진다.
        placeService.createBulk(new PlaceBulkCreateRequest(MAP_ID, List.of(item("이미있음", "111"))), USER_ID);

        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID, List.of(
            item("새장소A", "333"),
            item("이미있음", "111"),
            item("새장소B", "444")
        ));

        // when
        PlaceBulkCreateResponse response = placeService.createBulk(request, USER_ID);

        // then
        assertThat(response.created()).isEqualTo(2);
        assertThat(response.skipped()).isEqualTo(1);
        assertThat(response.results()).extracting(PlaceBulkCreateResponse.Result::status)
            .containsExactly(
                PlaceBulkCreateResponse.Status.CREATED,
                PlaceBulkCreateResponse.Status.DUPLICATE,
                PlaceBulkCreateResponse.Status.CREATED);

        // DB에 실제로 남았는지 확인한다. 응답만 보면 롤백을 못 잡는다.
        assertThat(placeRepository.count()).isEqualTo(3);
        assertThat(placeRepository.existsByMapIdAndKakaoPlaceIdAndDeletedAtIsNull(MAP_ID, "333")).isTrue();
        assertThat(placeRepository.existsByMapIdAndKakaoPlaceIdAndDeletedAtIsNull(MAP_ID, "444")).isTrue();
    }

    @Test
    void createBulk는_결과에_원래_순서의_index를_담는다() {
        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID,
            List.of(item("A", "111"), item("B", "222")));

        PlaceBulkCreateResponse response = placeService.createBulk(request, USER_ID);

        assertThat(response.results()).extracting(PlaceBulkCreateResponse.Result::index)
            .containsExactly(0, 1);
    }

    @Test
    void createBulk는_PRIVATE_지도면_바로_APPROVED로_등록한다() {
        placeService.createBulk(new PlaceBulkCreateRequest(MAP_ID, List.of(item("A", "111"))), USER_ID);

        assertThat(placeRepository.findAll()).extracting(Place::getStatus)
            .containsExactly(PlaceStatus.APPROVED);
    }

    @Test
    void createBulk는_COMMUNITY_일반_멤버면_PENDING으로_등록한다() {
        given(mapClient.getMemberInfo(anyLong(), anyLong()))
            .willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.MEMBER));

        placeService.createBulk(new PlaceBulkCreateRequest(MAP_ID, List.of(item("A", "111"))), USER_ID);

        assertThat(placeRepository.findAll()).extracting(Place::getStatus)
            .containsExactly(PlaceStatus.PENDING);
    }

    @Test
    void createBulk는_멤버가_아니면_전체를_거부한다() {
        given(mapClient.getMemberInfo(anyLong(), anyLong()))
            .willReturn(new MapMemberResponse(MapType.COMMUNITY, MapMemberRole.NONE));

        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID, List.of(item("A", "111")));

        assertThatThrownBy(() -> placeService.createBulk(request, USER_ID))
            .isInstanceOf(BusinessException.class)
            .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                .isEqualTo(PlaceErrorCode.NOT_MAP_MEMBER));
        assertThat(placeRepository.count()).isZero();
    }

    @Test
    void createBulk는_지도_권한을_맵_단위로_한_번만_확인한다() {
        // 20건 등록에 map-service를 20번 두드리지 않는다.
        PlaceBulkCreateRequest request = new PlaceBulkCreateRequest(MAP_ID,
            List.of(item("A", "111"), item("B", "222"), item("C", "333")));

        placeService.createBulk(request, USER_ID);

        org.mockito.Mockito.verify(mapClient, org.mockito.Mockito.times(1))
            .getMemberInfo(any(), any());
    }
}
