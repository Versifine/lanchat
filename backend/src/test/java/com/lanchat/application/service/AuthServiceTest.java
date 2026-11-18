package com.lanchat.application.service;

import com.lanchat.application.dto.AuthResult;
import com.lanchat.domain.exception.BusinessException;
import com.lanchat.domain.model.User;
import com.lanchat.domain.model.UserAccount;
import com.lanchat.domain.port.LoginAttemptLimiter;
import com.lanchat.domain.port.PasswordHasher;
import com.lanchat.domain.port.TokenIssuer;
import com.lanchat.domain.port.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 认证用例单元测试：参数校验、重名、登录失败/锁定、token 签发（EV-FUNC-01~05、EV-SEC-10）。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String HASH = "pbkdf2$120000$00112233445566778899aabbccddeeff$"
            + "ab".repeat(32);

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private TokenIssuer tokenIssuer;
    @Mock
    private LoginAttemptLimiter loginAttemptLimiter;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordHasher, tokenIssuer, loginAttemptLimiter);
    }

    @Test
    void registerShouldHashPasswordAndIssueToken() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordHasher.hash("abc123")).thenReturn(HASH);
        when(userRepository.insert(eq("alice"), eq("爱丽丝"), eq(HASH), anyLong())).thenReturn(42L);
        when(tokenIssuer.issue(42L, "alice")).thenReturn("jwt-token");

        AuthResult result = authService.register("alice", "abc123", "爱丽丝");

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.user().id()).isEqualTo(42L);
        assertThat(result.user().username()).isEqualTo("alice");
        assertThat(result.user().nickname()).isEqualTo("爱丽丝");
        verify(passwordHasher).hash("abc123");
        verify(userRepository).insert(eq("alice"), eq("爱丽丝"), eq(HASH), anyLong());
    }

    @Test
    void registerShouldFallbackNicknameToUsernameWhenBlank() {
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordHasher.hash("abc123")).thenReturn(HASH);
        when(userRepository.insert(eq("bob"), eq("bob"), eq(HASH), anyLong())).thenReturn(1L);
        when(tokenIssuer.issue(1L, "bob")).thenReturn("token");

        authService.register("bob", "abc123", "   ");

        verify(userRepository).insert(eq("bob"), eq("bob"), eq(HASH), anyLong());
    }

    @Test
    void registerShouldRejectInvalidUsername() {
        String[] invalidUsernames = {"ab", "a".repeat(21), "bad name", "用户名", "a-b", "a.b"};
        for (String username : invalidUsernames) {
            BusinessException error = businessError(() -> authService.register(username, "abc123", null));

            assertThat(error.code()).as("用户名应被拒绝: %s", username).isEqualTo(1004);
            assertThat(error.getMessage()).contains("用户名");
        }
        verifyNoInteractions(passwordHasher, tokenIssuer);
    }

    @Test
    void registerShouldRejectInvalidPasswordLength() {
        String[] invalidPasswords = {"12345", "a".repeat(33)};
        for (String password : invalidPasswords) {
            BusinessException error = businessError(() -> authService.register("alice", password, null));

            assertThat(error.code()).isEqualTo(1004);
            assertThat(error.getMessage()).contains("密码");
        }
    }

    @Test
    void registerShouldRejectTooLongNickname() {
        BusinessException error = businessError(
                () -> authService.register("alice", "abc123", "x".repeat(21)));

        assertThat(error.code()).isEqualTo(1004);
        assertThat(error.getMessage()).contains("昵称");
    }

    @Test
    void registerShouldRejectDuplicateUsernameWith1002() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        BusinessException error = businessError(() -> authService.register("alice", "abc123", null));

        assertThat(error.code()).isEqualTo(1002);
        verify(passwordHasher, never()).hash(anyString());
        verify(userRepository, never()).insert(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void loginShouldRejectBlankCredentials() {
        assertThat(businessError(() -> authService.login(null, "abc123")).code()).isEqualTo(1004);
        assertThat(businessError(() -> authService.login("  ", "abc123")).code()).isEqualTo(1004);
        assertThat(businessError(() -> authService.login("alice", null)).code()).isEqualTo(1004);
        assertThat(businessError(() -> authService.login("alice", "")).code()).isEqualTo(1004);
    }

    @Test
    void loginShouldReturnRateLimitedWhenUsernameIsLocked() {
        when(loginAttemptLimiter.isLocked("alice")).thenReturn(true);

        BusinessException error = businessError(() -> authService.login("alice", "abc123"));

        assertThat(error.code()).isEqualTo(1009);
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void loginWithUnknownUserShouldReturnBadCredentialsAndRecordFailure() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        BusinessException error = businessError(() -> authService.login("nobody", "abc123"));

        assertThat(error.code()).isEqualTo(1003);
        verify(loginAttemptLimiter).recordFailure("nobody");
    }

    @Test
    void loginWithWrongPasswordShouldReturnBadCredentials() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(new UserAccount(1L, "alice", "爱丽丝", HASH, 0L)));
        when(passwordHasher.matches("wrong", HASH)).thenReturn(false);

        BusinessException error = businessError(() -> authService.login("alice", "wrong"));

        assertThat(error.code()).isEqualTo(1003);
        verify(loginAttemptLimiter).recordFailure("alice");
        verify(tokenIssuer, never()).issue(anyLong(), anyString());
    }

    @Test
    void loginWithCorrectPasswordShouldClearFailuresAndIssueToken() {
        when(loginAttemptLimiter.isLocked("alice")).thenReturn(false);
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(new UserAccount(1L, "alice", "爱丽丝", HASH, 0L)));
        when(passwordHasher.matches("abc123", HASH)).thenReturn(true);
        when(tokenIssuer.issue(1L, "alice")).thenReturn("jwt");

        AuthResult result = authService.login("alice", "abc123");

        assertThat(result.token()).isEqualTo("jwt");
        assertThat(result.user().id()).isEqualTo(1L);
        verify(loginAttemptLimiter).recordSuccess("alice");
        verify(loginAttemptLimiter, never()).recordFailure(anyString());
    }

    @Test
    void meShouldReturnCurrentUserOrUserNotFound() {
        when(userRepository.findPublicById(1L))
                .thenReturn(Optional.of(new User(1L, "alice", "爱丽丝")));

        assertThat(authService.me(1L).nickname()).isEqualTo("爱丽丝");

        when(userRepository.findPublicById(2L)).thenReturn(Optional.empty());
        BusinessException error = businessError(() -> authService.me(2L));
        assertThat(error.code()).isEqualTo(1005);
    }

    private static BusinessException businessError(Executable action) {
        Throwable throwable = catchThrowable(action::execute);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }
}
