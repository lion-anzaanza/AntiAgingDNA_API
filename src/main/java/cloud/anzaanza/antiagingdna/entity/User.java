package cloud.anzaanza.antiagingdna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User {

    @Id
    @UuidGenerator
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "login_id", length = 64, nullable = false)
    private String loginId;

    @Column(name = "password", length = 255, nullable = false)
    private String password;
}
