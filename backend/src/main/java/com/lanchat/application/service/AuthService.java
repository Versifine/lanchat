package com.lanchat.application.service;

import com.lanchat.application.dto.AuthResult;
import com.lanchat.application.dto.UserView;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.exception.ErrorCodes;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserAccount;
import com.lanchat.domain.port.LoginAttemptLimiter;
import com.lanchat.domain.port.PasswordHasher;
import com.lanchat.domain.port.TokenIssuer;
import com.lanchat.domain.port.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 认证用例服务：注册、登录、查询当前用户。
 *
 * <p>安全要点：</p>
 * <ul>
 *   <li>密码使用 PBKDF2WithHmacSHA256 哈希后入库，明文密码不落库、不打日志；</li>
 *   <li>登录失败按用户名限流（5 次/分钟 → 锁定 1 分钟），不区分“用户不存在”与“密码错误”，防用户枚举；</li>
 *   <li>JWT 由 {@link TokenIssuer} 端口签发，本类不依赖 Spring / Servlet。</li>
 * </ul>
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final int PASSWORD_MAX_LENGTH = 32;
    private static final int NICKNAME_MAX_LENGTH = 20;

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TokenIssuer tokenIssuer;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(UserRepository userRepository,
                       PasswordHasher passwordHasher,
                       TokenIssuer tokenIssuer,
                       LoginAttemptLimiter loginAttemptLimiter) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    /**
     * 注册新用户，成功后直接返回 JWT（与契约一致，注册即登录）。
     */
    public AuthResult register(String username, String password, String nickname) {
        validateUsername(username);
        validatePassword(password);
        String safeNickname = normalizeNickname(nickname, username);
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCodes.USERNAME_TAKEN, "用户名已存在");
        }
        String passwordHash = passwordHasher.hash(password);
        long userId = userRepository.insert(username, safeNickname, passwordHash, System.currentTimeMillis());
        log.info("新用户注册成功: id={}, username={}", userId, username);
        String token = tokenIssuer.issue(userId, username);
        return new AuthResult(token, new UserView(userId, username, safeNickname));
    }

    /**
     * 用户名 + 密码登录，成功返回 JWT。
     */
    public AuthResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "用户名和密码不能为空");
        }
        if (loginAttemptLimiter.isLocked(username)) {
            log.info("登录被限流: username={}", username);
            throw new BusinessException(ErrorCodes.RATE_LIMITED, "登录失败次数过多，请 1 分钟后再试");
        }
        Optional<UserAccount> account = userRepository.findByUsername(username);
        if (account.isEmpty() || !passwordHasher.matches(password, account.get().passwordHash())) {
            loginAttemptLimiter.recordFailure(username);
            // 注意：不打印密码，也不区分“用户不存在/密码错误”，避免用户枚举
            log.info("登录失败: username={}", username);
            throw new BusinessException(ErrorCodes.BAD_CREDENTIALS, "用户名或密码错误");
        }
        UserAccount user = account.get();
        loginAttemptLimiter.recordSuccess(username);
        String token = tokenIssuer.issue(user.id(), user.username());
        log.info("用户登录成功: id={}, username={}", user.id(), user.username());
        return new AuthResult(token, new UserView(user.id(), user.username(), user.nickname()));
    }

    /**
     * 查询当前登录用户信息（GET /api/auth/me）。
     */
    public UserView me(long userId) {
        User user = userRepository.findPublicById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.USER_NOT_FOUND, "用户不存在"));
        return UserView.from(user);
    }

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "用户名需为 3-20 位字母、数字或下划线");
        }
    }

    private void validatePassword(String password) {
        if (password == null
                || password.length() < PASSWORD_MIN_LENGTH
                || password.length() > PASSWORD_MAX_LENGTH) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "密码长度需为 6-32 位");
        }
    }

    private String normalizeNickname(String nickname, String fallbackUsername) {
        String value = (nickname == null || nickname.isBlank()) ? fallbackUsername : nickname.strip();
        if (value.length() > NICKNAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCodes.INVALID_ARGUMENT, "昵称长度不能超过 20 个字符");
        }
        return value;
    }
}
