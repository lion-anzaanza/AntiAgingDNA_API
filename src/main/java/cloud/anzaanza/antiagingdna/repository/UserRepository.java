package cloud.anzaanza.antiagingdna.repository;

import cloud.anzaanza.antiagingdna.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
}
