package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 당일 카페인 마지막 섭취 시각대 — 일지.
 *
 * <p>근거: Drake et al., 2013 — 취침 6시간 전 섭취도 총수면을 1시간 이상 줄인다.
 * 즉 <b>양과 별개로 타이밍이 독립적인 감점 요인</b>이라 잔 수와 다른 컬럼으로 받는다.
 * 방향: 역(늦을수록 나쁨). 앵커는 기획 §8.
 *
 * <p>Phase 2 — {@link CaffeineCups} 와 같은 사유.
 */
@Getter
public enum CaffeineLastTime implements ScoredOption {

    /** 안 마심 */
    NONE(100),

    /** 오전 */
    MORNING(100),

    /** 오후 (~5시) */
    AFTERNOON(80),

    /** 저녁 (6시 이후) */
    EVENING(40);

    private final int score;

    CaffeineLastTime(int score) {
        this.score = score;
    }
}
