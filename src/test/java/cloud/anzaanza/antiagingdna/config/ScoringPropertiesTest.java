package cloud.anzaanza.antiagingdna.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cloud.anzaanza.antiagingdna.config.ScoringProperties.Alpha;
import cloud.anzaanza.antiagingdna.config.ScoringProperties.Weights;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** {@code scoring.grade.*} 가 빠지면 첫 요청의 NPE 가 아니라 여기서(부팅 시점) 먼저 터져야 한다 */
class ScoringPropertiesTest {

    @Test
    void grade_설정이_없으면_생성_시점에_거부한다() {
        assertThatThrownBy(() -> new ScoringProperties(
                        "v1",
                        new Weights(
                                new BigDecimal("0.333"),
                                new BigDecimal("0.278"),
                                new BigDecimal("0.222"),
                                new BigDecimal("0.167"),
                                BigDecimal.ZERO),
                        new Alpha(7, new BigDecimal("0.95")),
                        7,
                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scoring.grade");
    }
}
