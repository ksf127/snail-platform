package com.snail.platform.control.service;

import com.snail.platform.common.api.BizException;
import com.snail.platform.control.dto.LoginRequest;
import com.snail.platform.control.dto.LoginResponse;
import com.snail.platform.control.entity.UserEntity;
import com.snail.platform.control.mapper.UserMapper;
import com.snail.platform.control.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    /** 与 Flyway V1 种子数据中的 hash 保持一致。 */
    private static final String SEED_HASH = "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2";

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtService jwtService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, jwtService, passwordEncoder);
    }

    @Test
    void seedPasswordMatchesAdmin123() {
        assertThat(passwordEncoder.matches("admin123", SEED_HASH)).isTrue();
    }

    @Test
    void loginReturnsTokenAndUserView() {
        when(userMapper.selectOne(any())).thenReturn(user(true));
        when(jwtService.generate(any(UserEntity.class))).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("admin", "admin123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().username()).isEqualTo("admin");
        assertThat(response.user().role()).isEqualTo("ADMIN");
        assertThat(response.user().displayName()).isEqualTo("平台管理员");
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(user(true));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名或密码错误");
        verify(jwtService, never()).generate(any());
    }

    @Test
    void loginRejectsDisabledUser() {
        when(userMapper.selectOne(any())).thenReturn(user(false));

        assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "admin123")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "admin123")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    private UserEntity user(boolean enabled) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setDisplayName("平台管理员");
        user.setRole("ADMIN");
        user.setEnabled(enabled);
        user.setPasswordHash(SEED_HASH);
        return user;
    }
}
