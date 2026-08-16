package cloud.anzaanza.antiagingdna.repository;

import cloud.anzaanza.antiagingdna.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    /** 아이디가 로그인 식별자다 (email 은 더 이상 로그인에 쓰이지 않는다) */
    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);
}
