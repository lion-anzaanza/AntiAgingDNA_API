package cloud.anzaanza.antiagingdna.config;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.repository.DailyScoreRepository;
import cloud.anzaanza.antiagingdna.repository.DiaryRepository;
import cloud.anzaanza.antiagingdna.repository.UserRepository;
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

    /** 재부팅해도 계정이 하나만 있어야 한다 — 매번 새로 심으면 uk_user_login_id 위반으로 부팅이 깨진다 */
    @Test
    void 이미_있으면_다시_심지_않는다() {
        DemoAccountSeeder seeder = new DemoAccountSeeder(userRepository, null, null, clock);
        assertThat(userRepository.existsByLoginId("demo")).isTrue();
        // signUp/diaryService 를 null 로 넘겨도 예외 없이 조기 반환되는지 — 재실행 시
        // 이미 있으면 두 서비스를 아예 건드리지 않는다는 계약을 코드로 고정한다.
        seeder.run(null);
    }
}
