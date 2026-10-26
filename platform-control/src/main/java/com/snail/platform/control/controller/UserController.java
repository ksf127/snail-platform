package com.snail.platform.control.controller;

import com.snail.platform.common.api.ApiResult;
import com.snail.platform.control.dto.PageView;
import com.snail.platform.control.dto.UserView;
import com.snail.platform.control.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /** 成员选择弹框使用：按用户名/显示名分页搜索启用中的用户。 */
    @GetMapping
    public ApiResult<PageView<UserView>> list(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              @RequestParam(required = false) String keyword) {
        return ApiResult.ok(authService.users(page, size, keyword));
    }
}
