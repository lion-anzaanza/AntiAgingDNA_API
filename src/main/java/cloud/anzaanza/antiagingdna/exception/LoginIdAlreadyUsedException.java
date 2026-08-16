package cloud.anzaanza.antiagingdna.exception;

/** 이미 사용 중인 아이디 — 409 Conflict */
public class LoginIdAlreadyUsedException extends RuntimeException {

    public LoginIdAlreadyUsedException(String loginId) {
        super("이미 사용 중인 아이디입니다: " + loginId);
    }
}
