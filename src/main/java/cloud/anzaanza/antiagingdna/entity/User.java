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
 * <p>로그인 식별자는 <b>이메일</b>이다 — 목업 STEP 2 에 별도 로그인 아이디 입력이 없다.
 *
 * <p>STEP 4 의 약관 동의는 {@link UserAgreement} 가 항목별로 보관한다.
 */
@Entity
@Table(
        name = "user",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_email", columnNames = "email"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends BaseTimeEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 64)
    private String id;

    /** 로그인 식별자 */
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
