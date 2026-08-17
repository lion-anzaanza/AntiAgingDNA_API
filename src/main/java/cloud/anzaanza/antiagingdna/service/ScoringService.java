package cloud.anzaanza.antiagingdna.service;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.entity.DailyScore;
import cloud.anzaanza.antiagingdna.entity.Diary;
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
import java.time.Clock;
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
    private final Clock clock;

    public ScoringService(
            DnaInfoRepository dnaInfoRepository,
            DiaryRepository diaryRepository,
            DailyScoreRepository dailyScoreRepository,
            ScoringProperties properties,
            Clock clock) {
        this.dnaInfoRepository = dnaInfoRepository;
        this.diaryRepository = diaryRepository;
        this.dailyScoreRepository = dailyScoreRepository;
        this.properties = properties;
        this.clock = clock;
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
        String existingId = dailyScoreRepository
                .findByUserIdAndScoreDate(userId, date)
                .map(DailyScore::getId)
                .orElse(null);
        Optional<Diary> diaryEntry = diaryRepository.findByAuthorIdAndLogDate(userId, date);
        return dailyScoreRepository.save(compute(dna, date, existingId, diaryEntry));
    }

    /**
     * 그 날짜의 점수를 계산만 한다 — 저장은 호출부의 몫이다.
     *
     * <p>{@code existingId}·{@code diaryEntry} 를 인자로 받는 이유 — 호출부(
     * {@link #recalculate}·{@link #scoreOn})가 저장 여부를 결정하려면 이미 기존 행·일지
     * 유무를 알아야 하므로, 같은 조회를 여기서 또 하지 않는다.
     */
    private DailyScore compute(DnaInfo dna, LocalDate date, String existingId, Optional<Diary> diaryEntry) {
        String userId = dna.getUser().getId();

        AreaScores baseline = BaselineCalculator.of(dna);
        AreaScores today = diaryEntry
                .map(diary -> DiaryScorer.of(diary, dna))
                .orElseGet(() -> AreaScores.builder().build());

        AreaScores combined = combineWithHistory(userId, date, baseline, today);

        Double dailyTotal = today.weightedTotal(properties.weights());
        Double displayTotal = combined.weightedTotal(properties.weights());
        if (displayTotal == null) {
            // baseline 은 초기 진단 필수 문항으로 항상 신체 영역이 채워지므로 여기 오면 안 된다.
            throw new IllegalStateException("표시 점수를 산출할 근거가 없다: user=" + userId + " date=" + date);
        }

        return DailyScore.builder()
                // 있으면 같은 행을 갱신한다. 파생 캐시라 덮어써도 잃는 원본이 없다.
                .id(existingId)
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
                .build();
    }

    /**
     * 그 날짜와 <b>그 뒤에 이미 존재하는 모든 점수</b>를 다시 산출한다. 일지를 쓰거나 지운
     * 트랜잭션에서 호출한다.
     *
     * <p>해당 날짜만 고치면 안 되는 이유 — 결합값의 7일 이동평균 창이 앞선 날짜의 영역 점수를
     * 읽고, {@code α(n)} 의 n 도 {@code scoreDate < date} 로 센다. 어제 일지를 자정 넘겨
     * 쓰는 것은 정상 경로인데(하루 1건 · {@code log_date} 가 작성 시각과 별개), 그때 오늘 점수가
     * 어제 값을 반영하지 않은 채 남는다.
     *
     * <p>없는 날짜를 만들지는 않는다. 이미 조회된 적 있는 행만 갱신하고, 나머지는 조회 시점에
     * read-through 로 채워지면서 그때의 최신 값으로 계산된다.
     *
     * @return 다시 산출한 행 수 (해당 날짜 포함)
     */
    @Transactional
    public int recalculateFrom(String userId, LocalDate date) {
        DnaInfo dna = dnaInfoRepository
                .findById(userId)
                .orElseThrow(() -> new DiagnosisNotFoundException(userId));

        recalculate(dna, date);

        List<LocalDate> later =
                dailyScoreRepository.findByUserIdAndScoreDateGreaterThanOrderByScoreDateAsc(userId, date).stream()
                        .map(DailyScore::getScoreDate)
                        .toList();
        for (LocalDate affected : later) {
            recalculate(dna, affected);
        }
        return later.size() + 1;
    }

    /**
     * 그날의 점수를 돌려준다. 일지가 있거나 오늘 날짜라면 행이 없을 때 그 자리에서 산출해
     * 저장한다(read-through) — 결과가 같은 계산이라 몇 번을 불러도 값이 변하지 않으므로,
     * 실제 기록이 있는 날과 홈 화면이 매번 조회하는 오늘은 캐시해 둘 가치가 있다.
     *
     * <p><b>그 외(일지 없는 과거·미래) 날짜는 저장하지 않는다</b> — 2026-08-17,
     * FE backend-backlog.md #31. 원래는 무조건 저장했다("일지를 쓰지 않은 날에도 표시
     * 점수는 존재해야 한다", 기획 §C). 그런데 그 표시 점수는 항상 baseline 으로부터 다시
     * 계산 가능한 값이라 저장이 필수가 아니었고, 캘린더처럼 조회 API 를 여러 날짜에 반복
     * 호출하는 화면에서 단순 조회가 "그 사용자의 그 달이 통째로 기록 있는 달"이 되는 부작용만
     * 낳았다(되돌릴 방법도 없었다). 오늘만 예외로 남긴 이유는 가입 당일(day-0)·홈 오브 카드가
     * 원래도 이 캐시에 기대고 있던 자리라, 그 경로까지 매번 재계산하게 하면 홈 화면의 흔한
     * 반복 호출마다 불필요하게 비용이 커진다.
     */
    @Transactional
    public DailyScore scoreOn(String userId, LocalDate date) {
        rescoreIfStale(userId);
        Optional<DailyScore> existing = dailyScoreRepository.findByUserIdAndScoreDate(userId, date);
        if (existing.isPresent()) {
            return existing.get();
        }

        DnaInfo dna = dnaInfoRepository
                .findById(userId)
                .orElseThrow(() -> new DiagnosisNotFoundException(userId));
        Optional<Diary> diaryEntry = diaryRepository.findByAuthorIdAndLogDate(userId, date);
        DailyScore computed = compute(dna, date, null, diaryEntry);
        if (diaryEntry.isPresent() || date.equals(LocalDate.now(clock))) {
            return dailyScoreRepository.save(computed);
        }
        return computed;
    }

    /** 구간 조회. 비어 있는 날짜는 채우지 않는다 — 추이 그래프는 기록이 있는 날만 그리면 된다 */
    @Transactional
    public List<DailyScore> scoresBetween(String userId, LocalDate from, LocalDate to) {
        rescoreIfStale(userId);
        return dailyScoreRepository.findByUserIdAndScoreDateBetweenOrderByScoreDateAsc(userId, from, to);
    }

    /**
     * 파라미터 버전이 바뀐 뒤 남아 있는 옛 점수를 다시 만든다.
     *
     * <p>배치나 관리자 기능을 두지 않고 <b>조회 시점에</b> 처리한다. 이유:
     * <ul>
     *   <li>{@code daily_score} 는 파생 캐시라 언제 다시 만들어도 결과가 같다. 이미 같은 이유로
     *       read-through 를 쓰고 있으므로 새로운 종류의 동작이 아니다.</li>
     *   <li>부팅 시 일괄 처리는 전체 사용자 수에 비례해 부팅을 막고, 컨테이너를 재시작할 때마다
     *       다시 판단해야 한다.</li>
     *   <li>관리자 엔드포인트는 이 프로젝트에 없는 권한 체계를 새로 만들어야 한다.</li>
     * </ul>
     *
     * <p>사용자 한 명당 파라미터 변경 1회에 한 번만 실행되고, 범위는 그 사람의 가입 이후
     * 점수 행 수로 한정된다.
     */
    private void rescoreIfStale(String userId) {
        if (dailyScoreRepository.existsByUserIdAndScoringVersionNot(userId, properties.version())) {
            rescore(userId);
        }
    }

    /**
     * 사용자 한 명의 점수 이력을 현재 파라미터로 전부 다시 만든다.
     *
     * <p><b>날짜 오름차순으로 도는 것이 정확성 요건이다.</b> 결합값
     * {@code C_c(t) = (1−α)·baseline + α·mean7} 의 {@code mean7} 이 앞선 6일의 영역 점수를
     * 읽으므로, 뒤에서부터 고치면 아직 옛 파라미터로 계산된 이웃을 평균에 넣는다.
     * {@code α(n)} 의 n 도 {@code scoreDate < date} 조건이라 같은 방향에 의존한다.
     *
     * <p>원본({@code dna_info} + {@code diary})은 건드리지 않는다. 몇 번을 돌려도 같은 결과다.
     *
     * @return 다시 만든 행 수
     */
    @Transactional
    public int rescore(String userId) {
        DnaInfo dna = dnaInfoRepository
                .findById(userId)
                .orElseThrow(() -> new DiagnosisNotFoundException(userId));

        List<LocalDate> dates = dailyScoreRepository.findByUserIdOrderByScoreDateAsc(userId).stream()
                .map(DailyScore::getScoreDate)
                .toList();

        for (LocalDate date : dates) {
            recalculate(dna, date);
        }
        return dates.size();
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
