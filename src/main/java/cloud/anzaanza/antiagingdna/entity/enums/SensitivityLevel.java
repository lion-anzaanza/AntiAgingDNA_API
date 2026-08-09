package cloud.anzaanza.antiagingdna.entity.enums;

import lombok.Getter;

/**
 * 민감도 4점 척도(Ⓐ) — 초기 진단 Q3(당분) · Q4(카페인) · Q5(스트레스).
 *
 * <p>점수 문항이 아니라 <b>계수 문항</b>이다. 여기서 나온 계수 {@code k} 를 일지의 해당
 * 항목 <b>감점분</b>에 곱한다(기획 §3):
 *
 * <pre>s_i = 100 − k × (100 − s_raw)   (0~100 clamp)</pre>
 *
 * <p>매핑은 선형 확정이다(기획 §7①): step 0.2, '보통'을 중립보다 살짝 위(1.1)에 둔다.
 * 비선형 곡선은 근거가 없어 과적합·불투명 방지를 위해 미채택.
 */
@Getter
public enum SensitivityLevel {

    /** 전혀 아님 */
    NONE(0.7),

    /** 약간 */
    SLIGHT(0.9),

    /** 보통 */
    MODERATE(1.1),

    /** 매우 */
    HIGH(1.3);

    /** 감점 가중 계수 k */
    private final double coefficient;

    SensitivityLevel(double coefficient) {
        this.coefficient = coefficient;
    }
}
