package cloud.anzaanza.antiagingdna.entity.enums;

/**
 * 약관 종류 — 회원가입 STEP 4 (목업 {@code 03_TERMS}) 의 항목 4개를 그대로 옮긴 것이다.
 */
public enum AgreementType {

    /** [필수] 서비스 이용약관 */
    TERMS_OF_SERVICE,

    /** [필수] 개인정보 민감정보 처리 동의 */
    PRIVACY_SENSITIVE,

    /**
     * 마케팅 정보 수신.
     *
     * <p>목업은 이 항목에도 [필수] 라벨을 붙였으나, 국내법상 마케팅 수신동의는 선택이고
     * 철회가 가능해야 한다. 화면 수정이 필요한 지점이다.
     */
    MARKETING,

    /** [필수] 만 14세 이상입니다 */
    AGE_OVER_14
}
