package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 수분 섭취량(1잔 ≈ 200ml) — 일지. 근거: EFSA 2010 총수분 여 2.0L / 남 2.5L·1일.
 *
 * <p>방향: <b>하한형</b> — 적으면 나쁘고 많아도 무해하므로 8잔 이상은 만점을 유지한다
 * (U자가 아니다. 진짜 U자는 수면시간뿐 — 기획 일지 §5). 앵커는 기획 §8.
 *
 * <p>EFSA 기준은 성별로 다르지만 이 척도는 잔 수 구간이라 성별 분기가 필요 없다 —
 * 그래서 {@code user} 에 성별 컬럼을 두지 않았다.
 */
@Getter
public enum WaterIntake implements ScoredOption {

    /** 2잔 이하 */
    UNDER_2(30),

    /** 3~5잔 */
    THREE_TO_FIVE(60),

    /** 6~7잔 */
    SIX_TO_SEVEN(85),

    /** 8잔 이상 */
    EIGHT_OR_MORE(100);

    private final int score;

    WaterIntake(int score) {
        this.score = score;
    }
}
