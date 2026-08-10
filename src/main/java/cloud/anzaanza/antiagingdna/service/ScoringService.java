package cloud.anzaanza.antiagingdna.service;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.exception.DiagnosisNotFoundException;
import cloud.anzaanza.antiagingdna.repository.DailyScoreRepository;
import cloud.anzaanza.antiagingdna.repository.DiaryRepository;
import cloud.anzaanza.antiagingdna.repository.DnaInfoRepository;
import cloud.anzaanza.antiagingdna.service.scoring.Area;
import cloud.anzaanza.antiagingdna.service.scoring.AreaScores;
import cloud.anzaanza.antiagingdna.service.scoring.BaselineCalculator;
import cloud.anzaanza.antiagingdna.service.scoring.DiaryScorer;
import cloud.anzaanza.antiagingdna.service.scoring.ScoreCombiner;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종합점수 산출 — 기획 [종합점수 산출 수식] 6단계 파이프라인의 ③~⑥ 단계.
 *
 * <p>{@code daily_score} 는 원본이 아니라 <b>파생 캐시(read model)</b>다. 언제든 일지와 초기
 * 진단으로부터 다시 만들 수 있고, 파라미터를 재보정하면 실제로 다시 만든다.
 *
 * <p><b>일지 도메인과의 접점</b> — 일지를 저장·수정·삭제한 트랜잭션에서
 * {@link #recalculate(String, LocalDate)} 를 그 날짜로 호출하면 된다. 그 외에 점수 쪽이
 * 일지 쪽에 요구하는 것은 없다.
 *
 * <p><b>이동평균을 두 번 적용하지 않는다</b> — 기획 §1 파이프라인은 ④baseline 결합과
 * ⑥7일 이동평균을 따로 적고, §5 는 결합식 안에 이미 {@code mean7} 을 넣어 두었다. 문자 그대로
 * 하면 같은 평활을 두 번 하게 된다. §C 의 결합식이 최종형이라 보고 그쪽을 따랐다. 대신 §7 이
 * 말하는 "오브=당일값 · 추세=이동평균" 구분은 {@code daily_total}(당일 가중합)과
 * {@code display_total}(결합 후)로 그대로 남는다.
 */
@Service
public class ScoringService {

    private final DnaInfoRepository dnaInfoRepository;
    private final DiaryRepository diaryRepository;
    private final DailyScoreRepository dailyScoreRepository;
    private final ScoringProperties properties;

    public ScoringService(
            DnaInfoRepository dnaInfoRepository,
            DiaryRepository diaryRepository,
            DailyScoreRepository dailyScoreRepository,
            ScoringProperties properties) {
        this.dnaInfoRepository = dnaInfoRepository;
        this.diaryRepository = diaryRepository;
        this.dailyScoreRepository = dailyScoreRepository;
        this.properties = properties;
    }

    /**
     * 해당 날짜의 점수를 (재)산출해 저장한다. 같은 날짜 행이 있으면 덮어쓴다.
     *
     * <p>일지가 없는 날도 호출할 수 있다 — 그런 날의 표시 점수는 baseline 만으로 나온다.
     * 가입 당일(day-0)이 바로 그 경우다.
     */
    @Transactional
    public DailyScore recalculate(String userId, LocalDate date) {
        return recalculate(
                dnaInfoRepository.findById(userId).orElseThrow(() -> new DiagnosisNotFoundException(userId)),
                date);
    }

    /**
     * 초기 진단을 이미 손에 들고 있을 때 쓴다 — 가입 트랜잭션이 그렇다. 방금 저장한 엔티티를
     * 다시 조회하면 플러시 시점에 의존하게 되므로 그대로 넘긴다.
     */
    @Transactional
    public DailyScore recalculate(DnaInfo dna, LocalDate date) {
        String userId = dna.getUser().getId();

        AreaScores baseline = BaselineCalculator.of(dna);
        AreaScores today = diaryRepository
                .findByAuthorIdAndLogDate(userId, date)
                .map(diary -> DiaryScorer.of(diary, dna))
                .orElseGet(() -> AreaScores.builder().build());

        AreaScores combined = combineWithHistory(userId, date, baseline, today);

        Double dailyTotal = today.weightedTotal(properties.weights());
        Double displayTotal = combined.weightedTotal(properties.weights());
        if (displayTotal == null) {
            // baseline 은 초기 진단 필수 문항으로 항상 신체 영역이 채워지므로 여기 오면 안 된다.
            throw new IllegalStateException("표시 점수를 산출할 근거가 없다: user=" + userId + " date=" + date);
        }

        Optional<DailyScore> existing = dailyScoreRepository.findByUserIdAndScoreDate(userId, date);
        return dailyScoreRepository.save(DailyScore.builder()
                // 있으면 같은 행을 갱신한다. 파생 캐시라 덮어써도 잃는 원본이 없다.
                .id(existing.map(DailyScore::getId).orElse(null))
                .user(dna.getUser())
                .scoreDate(date)
                .physicalScore(AreaScores.toColumn(today.get(Area.PHYSICAL)))
                .mentalScore(AreaScores.toColumn(today.get(Area.MENTAL)))
                .emotionScore(AreaScores.toColumn(today.get(Area.EMOTION)))
                .socialScore(AreaScores.toColumn(today.get(Area.SOCIAL)))
                .environmentScore(AreaScores.toColumn(today.get(Area.ENVIRONMENT)))
                .dailyTotal(AreaScores.toColumn(dailyTotal))
                .displayTotal(AreaScores.toColumn(displayTotal))
                .scoringVersion(properties.version())
                .build());
    }

    /**
     * 그날의 점수를 돌려준다. 행이 없으면 그 자리에서 산출해 저장한다(read-through).
     *
     * <p>일지를 쓰지 않은 날에도 표시 점수는 존재해야 하는데(기획 §C), 그 행을 만들어 줄 주체가
     * 필요하다. 야간 배치를 두는 대신 조회 시점에 채운다 — 결과가 같은 계산이라 몇 번을 불러도
     * 값이 변하지 않는다. GET 이 쓰기를 하는 건 캐시 채우기이지 상태 변경이 아니다.
     */
    @Transactional
    public DailyScore scoreOn(String userId, LocalDate date) {
        return dailyScoreRepository
                .findByUserIdAndScoreDate(userId, date)
                .orElseGet(() -> recalculate(userId, date));
    }

    /** 구간 조회. 비어 있는 날짜는 채우지 않는다 — 추이 그래프는 기록이 있는 날만 그리면 된다 */
    @Transactional(readOnly = true)
    public List<DailyScore> scoresBetween(String userId, LocalDate from, LocalDate to) {
        return dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(userId, from, to);
    }

    /**
     * {@code C_c(t) = (1 − α) × baseline_c + α × mean7(일지_c)}
     *
     * <p>이동평균 창은 오늘을 포함한 최근 {@code movingAverageDays} 일이다. 오늘 값은 아직
     * 저장 전이므로 DB 에서 읽은 어제까지의 값들과 메모리에서 합친다.
     */
    private AreaScores combineWithHistory(
            String userId, LocalDate date, AreaScores baseline, AreaScores today) {

        LocalDate windowStart = date.minusDays(properties.movingAverageDays() - 1L);
        List<DailyScore> previous = dailyScoreRepository
                .findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(userId, windowStart, date.minusDays(1));

        Map<Area, Long> recordedDays = recordedDaysBefore(userId, date);

        AreaScores.Builder combined = AreaScores.builder();
        for (Area area : Area.values()) {
            Double todayScore = today.get(area);
            Double recentMean = meanOf(previous, area, todayScore);

            // 오늘 값이 있으면 그것도 기록 1일로 센다 — 저장 전이라 DB 집계에 안 잡힌다.
            long days = recordedDays.get(area) + (todayScore == null ? 0 : 1);

            combined.put(area, ScoreCombiner.combine(baseline.get(area), recentMean, days, properties.alpha()));
        }
        return combined.build();
    }

    private static Double meanOf(List<DailyScore> previous, Area area, Double todayScore) {
        List<Double> values = new ArrayList<>();
        for (DailyScore score : previous) {
            BigDecimal value = areaScoreOf(score, area);
            if (value != null) {
                values.add(value.doubleValue());
            }
        }
        if (todayScore != null) {
            values.add(todayScore);
        }
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private Map<Area, Long> recordedDaysBefore(String userId, LocalDate date) {
        Map<Area, Long> counts = new EnumMap<>(Area.class);
        counts.put(Area.PHYSICAL,
                dailyScoreRepository.countByUserIdAndScoreDateLessThanAndPhysicalScoreIsNotNull(userId, date));
        counts.put(Area.MENTAL,
                dailyScoreRepository.countByUserIdAndScoreDateLessThanAndMentalScoreIsNotNull(userId, date));
        counts.put(Area.EMOTION,
                dailyScoreRepository.countByUserIdAndScoreDateLessThanAndEmotionScoreIsNotNull(userId, date));
        counts.put(Area.SOCIAL,
                dailyScoreRepository.countByUserIdAndScoreDateLessThanAndSocialScoreIsNotNull(userId, date));
        counts.put(Area.ENVIRONMENT,
                dailyScoreRepository.countByUserIdAndScoreDateLessThanAndEnvironmentScoreIsNotNull(userId, date));
        return counts;
    }

    private static BigDecimal areaScoreOf(DailyScore score, Area area) {
        return switch (area) {
            case PHYSICAL -> score.getPhysicalScore();
            case MENTAL -> score.getMentalScore();
            case EMOTION -> score.getEmotionScore();
            case SOCIAL -> score.getSocialScore();
            case ENVIRONMENT -> score.getEnvironmentScore();
        };
    }
}
