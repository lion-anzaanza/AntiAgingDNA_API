package cloud.anzaanza.antiagingdna.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 닉네임 정규화 — NFD 로 분해된 한글도 완성형과 같은 글자로 취급하는지. */
class SignUpRequestTest {

    @Test
    void NFD로_분해된_한글_닉네임도_NFC로_바뀌어_패턴을_통과한다() {
        String decomposed = Normalizer.normalize("안자안자", Normalizer.Form.NFD);

        SignUpRequest request =
                new SignUpRequest("nosleep_dev", "nosleep@gmail.com", "password1234", decomposed, 2002, null, Map.of());

        assertThat(request.nickname()).isEqualTo("안자안자");
        assertThat(request.nickname()).matches(SignUpRequest.NICKNAME_PATTERN);
    }
}
