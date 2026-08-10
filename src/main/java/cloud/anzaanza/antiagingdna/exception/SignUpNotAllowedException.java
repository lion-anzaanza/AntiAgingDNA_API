package cloud.anzaanza.antiagingdna.exception;

/**
 * 형식은 맞지만 가입 조건을 만족하지 못한 요청 — 400 Bad Request.
 *
 * <p>필수 약관 미동의, 연령 미달처럼 필드 하나만 봐서는 판정할 수 없어 Bean Validation 으로
 * 잡히지 않는 규칙을 담는다.
 */
public class SignUpNotAllowedException extends RuntimeException {

    public SignUpNotAllowedException(String message) {
        super(message);
    }
}
