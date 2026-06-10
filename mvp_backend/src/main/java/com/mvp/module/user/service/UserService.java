package com.mvp.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mvp.common.exption.BusinessException;
import com.mvp.common.utils.JwtUtil;
import com.mvp.module.user.dto.LoginDto;
import com.mvp.module.user.entity.User;
import com.mvp.module.user.mapper.UserMapper;
import com.mvp.common.utils.BCryptUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserService {
    
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public User selectById(Long id) {
        return userMapper.selectById(id);
    }

    public User selectByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return userMapper.selectOne(wrapper);
    }

    public String login(LoginDto dto) {
        String username = dto.getUsername();
        String password = dto.getPassword();
        User user = selectByUsername(username);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!BCryptUtils.matches(password, user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("id", user.getId());
        String token = JwtUtil.genToken(claims);
        ValueOperations<String, String> stringStringValueOperations = stringRedisTemplate.opsForValue();
        stringStringValueOperations.set(token, token, 1, TimeUnit.HOURS);
        log.info("登录成功，token为{}", token);
        return token;
    }

    public void register(LoginDto dto) {
        User existUser = selectByUsername(dto.getUsername());
        if (existUser != null) {
            throw new BusinessException("用户已存在");
        }
        String username = dto.getUsername();
        String password = BCryptUtils.encode(dto.getPassword());
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        userMapper.insert(user);
    }
}