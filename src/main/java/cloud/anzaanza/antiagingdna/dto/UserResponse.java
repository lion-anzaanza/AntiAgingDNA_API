package cloud.anzaanza.antiagingdna.dto;

import cloud.anzaanza.antiagingdna.entity.User;

/**
 * 클라이언트에 노출해도 되는 계정 정보. 비밀번호 해시는 절대 포함하지 않는다.
 *
 * @param streakDays 연속 기록일(마이페이지 프로필 카드, FE backend-backlog.md #24/#28).
 *     정의는 {@link cloud.anzaanza.antiagingdna.service.AuthService#currentStreak} 참고.
 */
public record UserResponse(
        String id, String loginId, String email, String nickname, Integer birthYear, long streakDays) {

    public static UserResponse from(User user, long streakDays) {
        return new UserResponse(
                user.getId(),
                user.getLoginId(),
                user.getEmail(),
                user.getNickname(),
                user.getBirthYear(),
                streakDays);
    }
}
