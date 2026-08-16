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

    // ── 재채점 (scoring.version 이 바뀐 뒤) ────────────────────────

    /** 현재 파라미터 버전이 아닌 행이 남아 있는가 — 재채점이 필요한지 판단한다 */
    boolean existsByUserIdAndScoringVersionNot(String userId, String scoringVersion);

    /**
     * 재채점은 <b>반드시 날짜 오름차순</b>이어야 한다. 결합값이 앞선 날짜들의 영역 점수
     * (7일 이동평균 창)에 의존하므로, 뒤에서부터 고치면 아직 옛 값인 이웃을 읽는다.
     */
    List<DailyScore> findByUserIdOrderByScoreDateAsc(String userId);

    /**
     * 어떤 날짜의 값이 바뀌면 <b>그 뒤의 모든 점수</b>가 영향을 받는다 — 7일 이동평균 창이
     * 앞선 날짜를 읽고, {@code α(n)} 의 n 도 {@code scoreDate < date} 로 세기 때문이다.
     */
    List<DailyScore> findByUserIdAndScoreDateGreaterThanOrderByScoreDateAsc(
            String userId, LocalDate date);

    /** 회원탈퇴 시 계정보다 먼저 지운다 (FK: daily_score.user_id → user.id) */
    void deleteByUserId(String userId);
}
