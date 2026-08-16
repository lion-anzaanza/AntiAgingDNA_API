package cloud.anzaanza.antiagingdna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * 사용자 계정. 회원가입 STEP 2(개인정보 입력) 에 대응한다.
 *
 * <p>로그인 식별자는 <b>아이디({@link #loginId})</b>다(기획 결정, 2026-08-16 — FE
 * backend-backlog.md #2). 이메일은 계정에 남아있지만 로그인에는 쓰지 않는다 — 비밀번호 찾기
 * 등 복구 수단으로 필요해서다. {@code login_id} 는 기존 행을 백필하기 전까지 nullable 이다
 * (V2 마이그레이션 참고) — 신규 가입은 {@link cloud.anzaanza.antiagingdna.dto.SignUpRequest}
 * 의 {@code @NotBlank} 가 필수로 강제한다.
 *
 * <p>STEP 4 의 약관 동의는 {@link UserAgreement} 가 항목별로 보관한다.
 */
@Entity
@Table(
        name = "user",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_user_email", columnNames = "email"),
            @UniqueConstraint(name = "uk_user_login_id", columnNames = "login_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends BaseTimeEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 64)
    private String id;

    /** 로그인 식별자(아이디). 기존 행 백필 전까지는 nullable — {@link #email} 위 Javadoc 참고 */
    @Column(name = "login_id", length = 32)
    private String loginId;

    /** 복구용 연락처. 더 이상 로그인 식별자가 아니다 */
    @Column(name = "email", length = 255, nullable = false)
    private String email;

    /** BCrypt 등으로 해싱된 값. 평문 저장 금지 */
    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "nickname", length = 32, nullable = false)
    private String nickname;

    /** 출생연도(4자리). 만 14세 확인 및 연령대 통계용 */
    @Column(name = "birth_year", nullable = false)
    private Integer birthYear;
}
