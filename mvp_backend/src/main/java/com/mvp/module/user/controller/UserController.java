package com.mvp.module.user.controller;

import com.mvp.common.utils.ThreadLocalUtil;
import com.mvp.module.user.dto.LoginDto;
import com.mvp.module.user.entity.User;
import com.mvp.module.user.service.UserService;
import com.mvp.common.utils.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public Result<String> login(@RequestBody @Validated LoginDto loginDto) {
        String token = userService.login(loginDto);
        return Result.success(token);
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody @Validated LoginDto registerDto) {
        userService.register(registerDto);
        log.info("用户注册成功：{}", registerDto.getUsername());
        return Result.success("注册成功");
    }

    @GetMapping("/info")
    public Result<User> getUserInfo() {
        Long userId = ThreadLocalUtil.getUserId();
        User user = userService.selectById(userId);
        log.info("获取用户信息：{}", user);
        return Result.success(user);
    }
}