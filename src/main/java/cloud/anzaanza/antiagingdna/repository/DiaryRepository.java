package cloud.anzaanza.antiagingdna.repository;

import cloud.anzaanza.antiagingdna.entity.Diary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, String> {

    /** 1인 1일 1건 제약(uk_diary_author_log_date)에 대응하는 조회 */
    Optional<Diary> findByAuthorIdAndLogDate(String authorId, LocalDate logDate);

    List<Diary> findByAuthorIdAndLogDateBetweenOrderByLogDateAsc(
            String authorId, LocalDate from, LocalDate to);

    boolean existsByAuthorIdAndLogDate(String authorId, LocalDate logDate);
}
