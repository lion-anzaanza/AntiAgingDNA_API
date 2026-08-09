package cloud.anzaanza.antiagingdna.repository;

import cloud.anzaanza.antiagingdna.entity.UserAgreement;
import cloud.anzaanza.antiagingdna.entity.enums.AgreementType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, String> {

    List<UserAgreement> findByUserId(String userId);

    Optional<UserAgreement> findByUserIdAndAgreementType(String userId, AgreementType agreementType);
}
