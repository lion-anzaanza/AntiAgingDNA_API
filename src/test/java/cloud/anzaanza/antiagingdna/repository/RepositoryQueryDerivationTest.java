package cloud.anzaanza.antiagingdna.repository;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.entity.Diary;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.entity.User;
import cloud.anzaanza.antiagingdna.entity.UserAgreement;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * 파생 쿼리(메서드 이름) 가 실제 엔티티 속성으로 풀리는지 <b>DB 없이</b> 검증한다.
 *
 * <p>이름에 오타가 있으면 스프링은 부팅 시점에야 {@code PropertyReferenceException} 을 던진다.
 * 이 프로젝트는 CI 에 DB 가 없어 부팅 시점이 곧 배포다 — 오타 하나가 크래시 루프가 된다는 뜻이고,
 * 2026-08-10 의 Flyway 사고가 정확히 그 형태였다.
 *
 * <p>{@link PartTree} 는 스프링 데이터가 실제로 쓰는 파서다. 여기서 통과하면 부팅 때도 통과한다.
 */
class RepositoryQueryDerivationTest {

    /** 파생 쿼리를 쓰는 리포지토리와 그 도메인 타입 */
    private static final Map<Class<?>, Class<?>> REPOSITORIES = new LinkedHashMap<>() {
        {
            put(UserRepository.class, User.class);
            put(UserAgreementRepository.class, UserAgreement.class);
            put(DnaInfoRepository.class, DnaInfo.class);
            put(DiaryRepository.class, Diary.class);
            put(DailyScoreRepository.class, DailyScore.class);
        }
    };

    private static final String[] DERIVED_PREFIXES = {"find", "count", "exists", "delete", "get"};

    @Test
    void 모든_파생_쿼리의_속성_경로가_엔티티와_맞는다() {
        SoftAssertions softly = new SoftAssertions();
        int checked = 0;

        for (Map.Entry<Class<?>, Class<?>> entry : REPOSITORIES.entrySet()) {
            for (Method method : entry.getKey().getDeclaredMethods()) {
                if (!isDerivedQuery(method)) {
                    continue;
                }
                checked++;
                softly.assertThatCode(() -> new PartTree(method.getName(), entry.getValue()))
                        .describedAs("%s#%s", entry.getKey().getSimpleName(), method.getName())
                        .doesNotThrowAnyException();
            }
        }

        softly.assertAll();
        assertThat(checked).describedAs("검사한 파생 쿼리 수 — 0 이면 이 테스트가 아무것도 안 본 것이다").isPositive();
    }

    private static boolean isDerivedQuery(Method method) {
        for (String prefix : DERIVED_PREFIXES) {
            if (method.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
