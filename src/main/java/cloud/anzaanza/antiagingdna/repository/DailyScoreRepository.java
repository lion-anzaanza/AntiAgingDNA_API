package cloud.anzaanza.antiagingdna.repository;

import cloud.anzaanza.antiagingdna.entity.DailyScore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyScoreRepository extends JpaRepository<DailyScore, String> {

    Optional<DailyScore> findByUserIdAndScoreDate(String userId, LocalDate scoreDate);

    /** 7일 이동평균·α(n) 산출용 구간 조회 */
    List<DailyScore> findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(
            String userId, LocalDate from, LocalDate to);

    // ── α(n) 의 n — 영역별 "기록된 날 수" (기획 §5) ─────────────────
    // 영역마다 세는 이유는 기록 밀도가 다르기 때문이다. 수면은 매일 적고 사람 만남은 가끔
    // 적는 사용자에게, 수면 기록량으로 사회 영역의 신뢰도를 판단할 수는 없다.
    //
    // JPQL 문자열(@Query) 대신 파생 쿼리로 쓴다. 문자열은 부팅 시점에야 검증되는데 이 프로젝트는
    // CI 에 DB 가 없어 그 시점이 곧 배포다 — 오타 하나가 크래시 루프가 된다.

    long countByUserIdAndScoreDateLessThanAndPhysicalScoreIsNotNull(String userId, LocalDate date);

    long countByUserIdAndScoreDateLessThanAndMentalScoreIsNotNull(String userId, LocalDate date);

    long countByUserIdAndScoreDateLessThanAndEmotionScoreIsNotNull(String userId, LocalDate date);

    long countByUserIdAndScoreDateLessThanAndSocialScoreIsNotNull(String userId, LocalDate date);

    long countByUserIdAndScoreDateLessThanAndEnvironmentScoreIsNotNull(String userId, LocalDate date);
}
