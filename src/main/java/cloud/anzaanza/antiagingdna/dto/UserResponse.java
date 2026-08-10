package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.User;

/** 클라이언트에 노출해도 되는 계정 정보. 비밀번호 해시는 절대 포함하지 않는다. */
public record UserResponse(String id, String email, String nickname, Integer birthYear) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getEmail(), user.getNickname(), user.getBirthYear());
    }
}
