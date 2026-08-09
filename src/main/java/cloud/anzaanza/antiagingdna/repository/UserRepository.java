package cloud.anzaanza.antiagingdna.repository;

import cloud.anzaanza.antiagingdna.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    /** 이메일이 로그인 식별자다 */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
