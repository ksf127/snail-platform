package com.snail.platform.control.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.snail.platform.common.api.BizException;
import com.snail.platform.control.dto.LoginRequest;
import com.snail.platform.control.dto.LoginResponse;
import com.snail.platform.control.dto.PageView;
import com.snail.platform.control.dto.UserView;
import com.snail.platform.control.entity.UserEntity;
import com.snail.platform.control.mapper.UserMapper;
import com.snail.platform.control.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    /** 供成员选择等场景按关键字分页搜索启用中的用户。 */
    public PageView<UserView> users(int page, int size, String keyword) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getEnabled, true);
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(UserEntity::getUsername, value)
                    .or().like(UserEntity::getDisplayName, value));
        }
        wrapper.orderByAsc(UserEntity::getId);
        return PageView.of(userMapper.selectPage(PageView.paging(page, size), wrapper), this::toView);
    }

    private UserView toView(UserEntity user) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
