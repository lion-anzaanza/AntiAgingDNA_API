package cloud.anzaanza.antiagingdna.entity;

import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * 약관 동의 — 사용자 × 약관 종류 1행. 목업 {@code 03_TERMS} 의 체크박스 구조를 그대로 옮겼다.
 *
 * <p>동의 항목을 {@code user} 의 컬럼으로 펼치지 않는 이유는 두 가지다. 약관이 추가되면
 * 컬럼을 늘려야 하고, 항목별 동의 시각이 남지 않는다. 개인정보 처리 동의는 <b>무엇에</b>
 * <b>언제</b> 동의했는지가 기록되어야 하므로 행으로 관리한다.
 */
@Entity
@Table(
        name = "user_agreement",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_user_agreement_user_type",
                        columnNames = {"user_id", "agreement_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserAgreement {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_agreement_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agreement_type", length = 32, nullable = false)
    private AgreementType agreementType;

    /** 현재 동의 상태. 마케팅은 철회될 수 있으므로 false 로 바뀔 수 있다 */
    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    /** 이 상태로 마지막으로 바뀐 시각 */
    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;
}
