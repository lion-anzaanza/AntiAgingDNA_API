package cloud.anzaanza.antiagingdna.exception;

/** 이미 가입된 이메일 — 409 Conflict */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("이미 사용 중인 이메일입니다: " + email);
    }
}
