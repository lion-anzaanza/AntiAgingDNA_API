package cloud.anzaanza.antiagingdna.repository;

import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DnaInfoRepository extends JpaRepository<DnaInfo, String> {
}
