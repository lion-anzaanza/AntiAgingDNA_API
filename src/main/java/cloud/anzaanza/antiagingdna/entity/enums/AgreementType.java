package cloud.anzaanza.antiagingdna.entity.enums;

import java.util.Arrays;
import java.util.List;

/**
 * 약관 종류 — 회원가입 STEP 4 (목업 {@code 03_TERMS}) 의 항목 4개를 그대로 옮긴 것이다.
 *
 * <p>필수 여부를 상수마다 들고 있다. 가입 로직에 "이 셋은 필수"라고 목록을 적어두면 약관이
 * 늘어날 때 두 곳을 고쳐야 하고, 한쪽만 고치면 조용히 통과한다.
 */
public enum AgreementType {

    /** [필수] 서비스 이용약관 */
    TERMS_OF_SERVICE(true),

    /** [필수] 개인정보 민감정보 처리 동의 */
    PRIVACY_SENSITIVE(true),

    /**
     * 마케팅 정보 수신.
     *
     * <p>목업은 이 항목에도 [필수] 라벨을 붙였으나, 국내법상 마케팅 수신동의는 선택이고
     * 철회가 가능해야 한다. 화면 수정이 필요한 지점이다.
     */
    MARKETING(false),

    /** [필수] 만 14세 이상입니다 */
    AGE_OVER_14(true);

    private final boolean required;

    AgreementType(boolean required) {
        this.required = required;
    }

    /** 동의하지 않으면 가입이 성립하지 않는 항목인가 */
    public boolean isRequired() {
        return required;
    }

    public static List<AgreementType> required() {
        return Arrays.stream(values()).filter(AgreementType::isRequired).toList();
    }
}
