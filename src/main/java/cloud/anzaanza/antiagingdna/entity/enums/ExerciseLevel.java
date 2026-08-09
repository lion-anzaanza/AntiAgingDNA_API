package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 평소 운동량(중강도 분/주) — 초기 진단 Q6. 근거: WHO 2020 신체활동 권고(주 150~300분).
 *
 * <p>검증 표준의 원본 척도를 그대로 쓰는 문항이므로(★ 하이브리드 원칙, 기획 §2) 목업의
 * "매주 N회" 빈도 척도가 아니라 WHO 분/주 4구간을 사용한다. 앵커는 기획 §A-1.
 *
 * <p>권고 상한(300분)을 넘겨도 감점하지 않는다 — WHO 권고에 상한 페널티 근거가 없다.
 */
@Getter
public enum ExerciseLevel implements ScoredOption {

    /** 거의 안 함 */
    NONE(15),

    /** 주 150분 미만 */
    UNDER_150(55),

    /** 주 150~300분 (권고 구간) */
    FROM_150_TO_300(100),

    /** 주 300분 초과 */
    OVER_300(100);

    private final int score;

    ExerciseLevel(int score) {
        this.score = score;
    }
}
