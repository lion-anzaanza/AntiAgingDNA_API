package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 회원가입 요청 — 목업 STEP 2·3·4 를 한 번에 담는다.
 *
 * <p>계정은 STEP 4 의 "가입하고 내 LifeDAN 만들기" 에서 비로소 만들어진다. 화면이 3단계로
 * 나뉘어 있다고 API 도 3번 부를 이유는 없다 — 중간에 이탈하면 진단 답변만 있고 계정은 없는
 * 행이 남고, {@code dna_info} 의 not-null 컬럼들을 채울 방법이 없어진다.
 *
 * @param loginId 로그인 식별자(아이디). 형식(영문/숫자/언더스코어, 4~32자)은 <b>잠정 규칙</b>
 *     이다 — 가입 화면에 아이디 입력란이 아직 없어(FE backend-backlog.md #18) 기획에서 확정된
 *     형식이 없다. 화면이 정해지면 이 제약을 맞춰 조정한다.
 * @param email 더 이상 로그인에 쓰이지 않는다. 비밀번호 찾기 등 복구 수단으로 계속 받는다.
 * @param agreements 약관 종류 → 동의 여부. 화면의 체크박스 4개를 그대로 보낸다. 키는 {@link
 *     AgreementType} 의 상수명 그대로다(예: {@code "TERMS_OF_SERVICE"}) — 동의 시각은 서버가
 *     찍으므로 보낼 필요 없다.
 */
public record SignUpRequest(
        @NotBlank @Size(min = 4, max = 32) @Pattern(regexp = "^[A-Za-z0-9_]+$") String loginId,
        @NotBlank @Email @Size(max = 255) String email,

        // 상한 72 는 취향이 아니다. BCrypt 는 입력의 앞 72바이트만 쓰므로 그 뒤는 검증에
        // 관여하지 않는다. 길이를 막지 않으면 사용자가 없는 안전을 믿게 된다.
        // 문자 종류 제약은 목업의 placeholder("8자리 이상, 영문 숫자 포함")를 그대로 반영한다.
        @NotBlank
                @Size(min = 8, max = 72)
                @Pattern(
                        regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                        message = "영문과 숫자를 모두 포함해야 합니다")
                String password,
        @NotBlank @Size(max = 32) String nickname,
        @NotNull
                @Min(1900)
                @Schema(
                        description =
                                "출생연도(4자리). 상한은 스펙에 고정 숫자로 없다 — 매년 바뀌는 값이라"
                                        + " 서버가 (가입 시점 연도 − 14) 로 동적으로 계산해 만 14세 미만·미래"
                                        + " 연도를 400(가입 조건 미충족)으로 거부한다.")
                Integer birthYear,
        @NotNull @Valid DiagnosisRequest diagnosis,
        @NotEmpty
                @Schema(
                        example =
                                "{\"TERMS_OF_SERVICE\": true, \"PRIVACY_SENSITIVE\": true, "
                                        + "\"MARKETING\": false, \"AGE_OVER_14\": true}")
                Map<AgreementType, Boolean> agreements) {}
