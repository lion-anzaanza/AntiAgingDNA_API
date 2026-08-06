package cloud.anzaanza.antiagingdna.repository;

import cloud.anzaanza.antiagingdna.entity.Diary;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, String> {
}
