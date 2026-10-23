package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.common.api.BizException;
import com.snail.platform.control.dto.LoginRequest;
import com.snail.platform.control.dto.LoginResponse;
import com.snail.platform.control.dto.UserView;
import com.snail.platform.control.security.AuthUser;
import com.snail.platform.control.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResult<UserView> me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            throw BizException.unauthorized("未登录");
        }
        return ApiResult.ok(authService.me(user.id()));
    }
}
