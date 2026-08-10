package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 회원가입 요청 — 목업 STEP 2·3·4 를 한 번에 담는다.
 *
 * <p>계정은 STEP 4 의 "가입하고 내 LifeDAN 만들기" 에서 비로소 만들어진다. 화면이 3단계로
 * 나뉘어 있다고 API 도 3번 부를 이유는 없다 — 중간에 이탈하면 진단 답변만 있고 계정은 없는
 * 행이 남고, {@code dna_info} 의 not-null 컬럼들을 채울 방법이 없어진다.
 *
 * @param agreements 약관 종류 → 동의 여부. 화면의 체크박스 4개를 그대로 보낸다.
 */
public record SignUpRequest(
        @NotBlank @Email @Size(max = 255) String email,

        // 상한 72 는 취향이 아니다. BCrypt 는 입력의 앞 72바이트만 쓰므로 그 뒤는 검증에
        // 관여하지 않는다. 길이를 막지 않으면 사용자가 없는 안전을 믿게 된다.
        @NotBlank @Size(min = 8, max = 72) String password,

        @NotBlank @Size(max = 32) String nickname,

        @NotNull @Min(1900) Integer birthYear,

        @NotNull @Valid DiagnosisRequest diagnosis,

        @NotEmpty Map<AgreementType, Boolean> agreements) {}
