package cloud.anzaanza.antiagingdna.config;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.repository.DailyScoreRepository;
import cloud.anzaanza.antiagingdna.repository.DiaryRepository;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
import cloud.anzaanza.antiagingdna.service.AuthService;
import cloud.anzaanza.antiagingdna.service.DiaryService;
import cloud.anzaanza.antiagingdna.support.IntegrationTest;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link IntegrationTest} 는 다른 테스트가 느려지지 않도록 시딩을 꺼둔다 — 여기서만 다시 켜서
 * 실제로 부팅될 때 끝까지 실행되는지(가입 검증·일지 저장·day-0 채점) 실 MySQL 로 확인한다.
 * 운영 DB에 그대로 나갈 로직이라 로컬/CI 에서 한 번은 통째로 돌려보는 것이 안전하다.
 */
@TestPropertySource(properties = "app.seed-demo.enabled=true")
class DemoAccountSeederTest extends IntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private DiaryRepository diaryRepository;
    @Autowired private DailyScoreRepository dailyScoreRepository;
    @Autowired private AuthService authService;
    @Autowired private DiaryService diaryService;
    @Autowired private Clock clock;

    @Test
    void 부팅하면_데모_계정과_14일치_일지_점수가_생긴다() {
        User demo = userRepository
                .findByLoginId("demo")
                .orElseThrow(() -> new AssertionError("데모 계정이 생성되지 않았다"));

        LocalDate today = LocalDate.now(clock);
        assertThat(diaryRepository.findByAuthorIdAndLogDateBetweenOrderByLogDateAsc(
                        demo.getId(), today.minusDays(13), today))
                .describedAs("14일치 일지")
                .hasSize(14);
        assertThat(dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
                        demo.getId(), today.minusDays(13), today))
                .describedAs("14일치 점수 — 일지를 쓴 트랜잭션마다 재채점된다")
                .hasSize(14);
    }

    /**
     * 계정이 이미 있으면 다시 가입시키지 않는다(재부팅마다 새로 심으면 uk_user_login_id 위반으로
     * 부팅이 깨진다). 모든 날짜가 이미 있으므로 일지도 다시 쓰지 않는다 — authService/diaryService
     * 를 실제로는 호출하지 않는다는 걸, 두 빈을 넘기고도(mock 이 아니라 진짜 빈) 문제없이 통과하는
     * 것으로 간접 확인한다(호출됐다면 로그인 아이디 중복으로 예외가 났을 것이다).
     */
    @Test
    void 완전한_상태에서_재실행하면_아무것도_다시_쓰지_않는다() {
        assertThat(userRepository.existsByLoginId("demo")).isTrue();

        new DemoAccountSeeder(userRepository, diaryRepository, authService, diaryService, clock).run(null);

        LocalDate today = LocalDate.now(clock);
        assertThat(diaryRepository.findByAuthorIdAndLogDateBetweenOrderByLogDateAsc(
                        userRepository.findByLoginId("demo").orElseThrow().getId(), today.minusDays(13), today))
                .hasSize(14);
    }

    /**
     * 가입은 커밋됐는데 일지 14건 중 일부만 쓰고 프로세스가 죽는 상황을 흉내낸다 — 재부팅 후
     * 계정 존재만 보고 끝냈다면 빠진 날짜가 영영 채워지지 않는다. 이 테스트가 그 회귀를 잡는다.
     */
    @Test
    void 일부_날짜만_있는_상태에서_재실행하면_빠진_날짜만_채운다() {
        User demo = userRepository.findByLoginId("demo").orElseThrow();
        LocalDate today = LocalDate.now(clock);

        // 최근 3일치를 지워 "일부만 쓰고 죽은 뒤 재부팅" 상황을 만든다
        for (int i = 0; i < 3; i++) {
            diaryRepository
                    .findByAuthorIdAndLogDate(demo.getId(), today.minusDays(i))
                    .ifPresent(diaryRepository::delete);
        }
        diaryRepository.flush();
        assertThat(diaryRepository.findByAuthorIdAndLogDateBetweenOrderByLogDateAsc(
                        demo.getId(), today.minusDays(13), today))
                .hasSize(11);

        new DemoAccountSeeder(userRepository, diaryRepository, authService, diaryService, clock).run(null);

        assertThat(diaryRepository.findByAuthorIdAndLogDateBetweenOrderByLogDateAsc(
                        demo.getId(), today.minusDays(13), today))
                .describedAs("빠졌던 3일이 다시 채워져 14일 전부여야 한다")
                .hasSize(14)
                .extracting(Diary::getLogDate)
                .contains(today, today.minusDays(1), today.minusDays(2));
    }
}
