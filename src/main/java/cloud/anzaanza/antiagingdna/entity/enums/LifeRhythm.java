package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 평소 생활 리듬(규칙성) — 초기 진단 Q10. 정신·감정 영역 baseline.
 *
 * <p>검증된 표준 척도가 없는 문항이라 Ⓐ 4점으로 통일했고(기획 §2), 앵커는 투명·해석 가능한
 * 단순안을 택했다(기획 §7 원칙). 매핑은 기획 §A-1.
 */
@Getter
public enum LifeRhythm implements ScoredOption {

    /** 매우 규칙적 */
    VERY_REGULAR(100),

    /** 대체로 규칙적 */
    MOSTLY_REGULAR(75),

    /** 다소 불규칙 */
    SOMEWHAT_IRREGULAR(45),

    /** 매우 불규칙 */
    VERY_IRREGULAR(15);

    private final int score;

    LifeRhythm(int score) {
        this.score = score;
    }
}
