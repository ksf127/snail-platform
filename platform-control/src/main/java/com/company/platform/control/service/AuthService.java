package com.company.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.platform.common.api.BizException;
import com.company.platform.control.dto.LoginRequest;
import com.company.platform.control.dto.LoginResponse;
import com.company.platform.control.dto.UserView;
import com.company.platform.control.entity.UserEntity;
import com.company.platform.control.mapper.UserMapper;
import com.company.platform.control.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, request.username()));
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        return new LoginResponse(jwtService.generate(user), toView(user));
    }

    public UserView me(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        return toView(user);
    }

    private UserView toView(UserEntity user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
