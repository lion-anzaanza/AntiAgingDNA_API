package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 잠들기까지 걸린 시간(수면 잠복기) — 일지. 근거: PSQI (Buysse et al., 1989) — 15분 이하 양호.
 *
 * <p>방향: <b>역(단조 감소)</b> — 길수록 나쁘다. 앵커는 기획 §8.
 */
@Getter
public enum SleepLatency implements ScoredOption {

    /** 5분 이내 */
    WITHIN_5(100),

    /** 15분 이내 */
    WITHIN_15(75),

    /** 30분 이내 */
    WITHIN_30(45),

    /** 1시간 이상 */
    OVER_60(10);

    private final int score;

    SleepLatency(int score) {
        this.score = score;
    }
}
