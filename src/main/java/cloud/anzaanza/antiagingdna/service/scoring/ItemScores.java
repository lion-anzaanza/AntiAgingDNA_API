package cloud.anzaanza.antiagingdna.service.scoring;

import cloud.anzaanza.antiagingdna.entity.enums.ScoredOption;
import cloud.anzaanza.antiagingdna.entity.enums.SensitivityLevel;
import java.time.Duration;
import java.time.LocalTime;

/**
 * 항목 단위 0~100 정규화 (기획 [종합점수 산출 수식] §2 · §8 · §A-1).
 *
 * <p>선택지 enum 은 앵커를 자기가 들고 있으므로({@link ScoredOption}) 여기 없다. 이 클래스는
 * <b>선택지로 표현되지 않는</b> 항목 — 시각 두 개에서 파생되는 수면시간, 1~10 슬라이더,
 * 5점 척도, 체크박스 개수, WHO-5 합산 — 만 다룬다.
 */
public final class ItemScores {

    /** 이상 수면시간 하한 (AASM·NSF) */
    private static final double IDEAL_SLEEP_MIN_HOURS = 7;

    /** 이상 수면시간 상한 */
    private static final double IDEAL_SLEEP_MAX_HOURS = 9;

    /** 이상구간 밖 1시간당 감점 (기획 §8) */
    private static final double SLEEP_PENALTY_PER_HOUR = 15;

    /** 수면 질 자가진단 체크 1개당 감점 (기획 §A-1) */
    private static final double SLEEP_SELF_CHECK_PENALTY = 25;

    /** WHO-5 는 5문항 각 0~5(합 0~25) → ×4 로 0~100 (표준 채점) */
    private static final int WHO5_SCALE = 4;

    private ItemScores() {}

    /**
     * 취침·기상 시각 → 수면 시간(분). 저장하지 않고 그때그때 계산한다
     * (기획 일지 §2 "Ⓘ 시각 입력 → 자동 계산").
     *
     * <p>기상 시각이 취침 시각보다 이르거나 같으면 자정을 넘긴 것으로 본다. 일지는 하루 1건이고
     * 날짜 없이 시각만 받으므로, 그 외의 해석이 없다.
     *
     * <p>채점과 조회 응답이 <b>같은 규칙</b>을 써야 하므로 여기 한 곳에만 둔다.
     *
     * @return 둘 중 하나라도 없으면 {@code null}
     */
    public static Long sleepMinutes(LocalTime bedtime, LocalTime wakeTime) {
        if (bedtime == null || wakeTime == null) {
            return null;
        }
        Duration slept = Duration.between(bedtime, wakeTime);
        if (!slept.isPositive()) {
            slept = slept.plusDays(1);
        }
        return slept.toMinutes();
    }

    /**
     * 수면시간 — 진짜 U자 항목은 이것 하나뿐이다 (기획 일지 §5).
     * {@code s = 100 − 15 × (이상구간 7~9h 밖 거리)}
     */
    public static Double sleepDuration(LocalTime bedtime, LocalTime wakeTime) {
        Long minutes = sleepMinutes(bedtime, wakeTime);
        if (minutes == null) {
            return null;
        }
        double hours = minutes / 60.0;
        double distance = Math.max(0, Math.max(IDEAL_SLEEP_MIN_HOURS - hours, hours - IDEAL_SLEEP_MAX_HOURS));
        return clamp(100 - SLEEP_PENALTY_PER_HOUR * distance);
    }

    /** 5점 척도(1~5) → 0 / 25 / 50 / 75 / 100 (기획 §2 "이산 등간격") */
    public static Double fivePointScale(Integer value) {
        return value == null ? null : clamp((value - 1) * 25.0);
    }

    /** 스트레스 1~10 역채점 {@code s = 100 × (10 − x) / 9} (기획 §8 · 0~10 NRS) */
    public static Double stress(Integer level) {
        return level == null ? null : clamp(100.0 * (10 - level) / 9);
    }

    /** 수면 질 자가진단 {@code 100 − 25 × 체크수} (기획 §A-1) */
    public static Double sleepSelfCheck(boolean... checks) {
        int checked = 0;
        for (boolean check : checks) {
            if (check) {
                checked++;
            }
        }
        return clamp(100 - SLEEP_SELF_CHECK_PENALTY * checked);
    }

    /**
     * WHO-5 웰빙 지수 — 5문항 합계 × 4.
     *
     * <p>한 문항이라도 비면 {@code null} 이다. WHO-5 는 5문항 합계로만 타당도가 확보된 척도라
     * 일부만 더해 환산하면 다른 지표가 된다.
     */
    public static Double who5(Integer... answers) {
        int sum = 0;
        for (Integer answer : answers) {
            if (answer == null) {
                return null;
            }
            sum += answer;
        }
        return clamp(sum * (double) WHO5_SCALE);
    }

    /** 선택지 enum 의 앵커. {@code null}(미입력)은 결측으로 전달한다 */
    public static Double anchor(ScoredOption option) {
        return option == null ? null : (double) option.getScore();
    }

    /**
     * 민감도 계수 적용 — <b>감점분에만</b> 곱한다 (기획 §3).
     * {@code s = 100 − k × (100 − s_raw)}
     *
     * <p>만점(100)은 계수와 무관하게 만점으로 남는다. 민감한 사람이 잘 지킨 날을 깎지 않는다.
     */
    public static Double applySensitivity(Double rawScore, SensitivityLevel sensitivity) {
        if (rawScore == null) {
            return null;
        }
        if (sensitivity == null) {
            return rawScore;
        }
        return clamp(100 - sensitivity.getCoefficient() * (100 - rawScore));
    }

    /**
     * 기록된 값만 평균 — 결측은 0점이 아니라 제외 후 재정규화 (기획 §4 · 일지 §5).
     *
     * @return 전부 결측이면 {@code null}
     */
    public static Double mean(Double... values) {
        double sum = 0;
        int count = 0;
        for (Double value : values) {
            if (value != null) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? null : sum / count;
    }

    private static double clamp(double score) {
        return Math.max(0, Math.min(100, score));
    }
}
