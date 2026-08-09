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
}
