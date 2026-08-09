package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 당일 카페인 섭취량(잔 수) — 일지. 근거: EFSA 2015 1일 400mg ≈ 4잔.
 *
 * <p>방향: <b>역</b>. 앵커는 기획 §8. 감점분에 {@code k_caffeine} 을 곱한다.
 *
 * <p>Phase 2 — 목업 `01_CREATE_DIARY` 에 카페인 입력이 없다. 이 항목이 없으면 온보딩에서
 * 받은 {@link SensitivityLevel} 기반 {@code k_caffeine} 이 곱할 대상을 잃어 죽은 값이 된다.
 */
@Getter
public enum CaffeineCups implements ScoredOption {

    /** 0잔 */
    NONE(100),

    /** 1~2잔 */
    ONE_TO_TWO(80),

    /** 3~4잔 (EFSA 상한 부근) */
    THREE_TO_FOUR(50),

    /** 5잔 이상 */
    FIVE_OR_MORE(15);

    private final int score;

    CaffeineCups(int score) {
        this.score = score;
    }
}
