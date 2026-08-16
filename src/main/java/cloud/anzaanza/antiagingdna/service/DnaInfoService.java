package cloud.anzaanza.antiagingdna.service;

import cloud.anzaanza.antiagingdna.config.ScoringProperties;
import cloud.anzaanza.antiagingdna.dto.DnaInfoResponse;
import cloud.anzaanza.antiagingdna.entity.DnaInfo;
import cloud.anzaanza.antiagingdna.exception.DiagnosisNotFoundException;
import cloud.anzaanza.antiagingdna.repository.DnaInfoRepository;
import cloud.anzaanza.antiagingdna.service.scoring.BaselineCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초기 진단 조회.
 *
 * <p>수정 기능은 없다. 기획·목업 어디에도 재진단 흐름이 없고, 답변을 갈아끼우면 과거
 * {@code daily_score} 가 어떤 baseline 으로 산출됐는지 복원할 수 없게 된다. 재진단을 넣으려면
 * {@code dna_info} 를 이력 테이블로 바꾸는 설계 변경이 먼저다.
 */
@Service
public class DnaInfoService {

    private final DnaInfoRepository dnaInfoRepository;
    private final ScoringProperties properties;

    public DnaInfoService(DnaInfoRepository dnaInfoRepository, ScoringProperties properties) {
        this.dnaInfoRepository = dnaInfoRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public DnaInfoResponse myDna(String userId) {
        DnaInfo dna = dnaInfoRepository
                .findById(userId)
                .orElseThrow(() -> new DiagnosisNotFoundException(userId));
        return DnaInfoResponse.from(dna, BaselineCalculator.of(dna), properties.grade());
    }
}
