package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 흡연 상태 — 초기 진단 Q9. 근거: KNHANES 표준 4분류(비흡연/과거/현재 가끔/현재 매일).
 *
 * <p>{@code NEVER}/{@code FORMER} 가 "비흡연" 상태를 겸하므로 별도의 {@code is_smoker}
 * 플래그를 두지 않는다({@link DrinkFrequency} 와 같은 이유).
 */
@Getter
public enum SmokingStatus implements ScoredOption {

    /** 비흡연 */
    NEVER(100),

    /** 과거 흡연 */
    FORMER(80),

    /** 현재 가끔 */
    CURRENT_OCCASIONAL(40),

    /** 현재 매일 */
    CURRENT_DAILY(10);

    private final int score;

    SmokingStatus(int score) {
        this.score = score;
    }
}
