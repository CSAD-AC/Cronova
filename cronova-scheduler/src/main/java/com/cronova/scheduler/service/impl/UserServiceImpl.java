package com.cronova.scheduler.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cronova.scheduler.common.ErrorCodeEnum;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.dto.UserLoginDTO;
import com.cronova.scheduler.domain.dto.UserRegisterDTO;
import com.cronova.scheduler.domain.dto.UserUpdateDTO;
import com.cronova.scheduler.domain.enums.RoleType;
import com.cronova.scheduler.domain.po.User;
import com.cronova.scheduler.domain.vo.LoginResponseVO;
import com.cronova.scheduler.domain.vo.UserVO;
import com.cronova.scheduler.exception.UserNotFoundException;
import com.cronova.scheduler.mapper.RoleMapper;
import com.cronova.scheduler.mapper.UserMapper;
import com.cronova.scheduler.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户服务实现类
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    @Autowired
    private RoleMapper roleMapper;

    @Override
    public Result<UserVO> register(UserRegisterDTO registerDTO) {
        // 验证用户名是否已存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, registerDTO.getUsername());
        User existingUser = getOne(queryWrapper);
        if (existingUser != null) {
            log.warn("用户名已存在, 拒绝注册");
            return Result.error(ErrorCodeEnum.USER_IS_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(registerDTO.getPassword());

        User user = User.builder()
                .username(registerDTO.getUsername())
                .password(encodedPassword)
                .nickname(registerDTO.getNickname())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        save(user);
        roleMapper.addRolesForUser(user.getId(), RoleType.USER.getId());

        log.info("{}用户注册成功,已分配{}角色 ID为{}",
                user.getUsername(), RoleType.USER.getName(), user.getId());

        return Result.success(UserVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .username(user.getUsername())
                .build());
    }

    @Override
    public Result<LoginResponseVO> login(UserLoginDTO loginDTO) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, loginDTO.getUsername());
        User user = getOne(queryWrapper);
        if (user == null) {
            throw new UserNotFoundException();
        }

        // 验证密码是否正确
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            log.warn("登录操作-用户密码错误");
            return Result.error(401, "账号或密码错误, 请重试");
        }

        // 使用SaToken生成token
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        UserVO userVO = UserVO.builder().build();
        BeanUtils.copyProperties(user, userVO);

        LoginResponseVO responseVO = LoginResponseVO.builder()
                .user(userVO)
                .token(token)
                .build();
        log.info("{}用户登录成功, token:{}",userVO.getUsername(), responseVO.getToken());
        return Result.success(responseVO);
    }

    @Override
    public User findByUsername(String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, username);
        return getOne(queryWrapper);
    }

    @Override
    public Result<IPage<UserVO>> getUserList(int page, int size, String username) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            queryWrapper.like(User::getUsername, username);
        }
        queryWrapper.orderByDesc(User::getCreateTime);

        IPage<User> userPage = this.page(new Page<>(page, size), queryWrapper);
        
        IPage<UserVO> userVOPage = userPage.convert(user -> {
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            return userVO;
        });

        return Result.success(userVOPage);
    }

    @Override
    public Result<UserVO> getUserById(String id) {
        User user = this.getById(id);
        if (user == null) {
            return Result.error(ErrorCodeEnum.USER_NOT_FOUND);
        }

        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        return Result.success(userVO);
    }

    @Override
    public Result<UserVO> createUser(User user) {
        // 验证用户名是否已存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, user.getUsername());
        User existingUser = this.getOne(queryWrapper);
        if (existingUser != null) {
            return Result.error(ErrorCodeEnum.USER_IS_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(encodedPassword);
        
        LocalDateTime now = LocalDateTime.now();
        user.setCreateTime(now);
        user.setUpdateTime(now);

        this.save(user);
        
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        return Result.success(userVO);
    }

    @Override
    public Result<UserVO> updateUser(String id, UserUpdateDTO userUpdateDTO) {
        User user = this.getById(id);
        if (user == null) {
            return Result.error(ErrorCodeEnum.USER_NOT_FOUND);
        }

        if (userUpdateDTO.getNickname() != null) {
            user.setNickname(userUpdateDTO.getNickname());
        }
        if (userUpdateDTO.getPassword() != null && !userUpdateDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userUpdateDTO.getPassword()));
        }
        user.setUpdateTime(LocalDateTime.now());

        this.updateById(user);
        
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);

        return Result.success(userVO);
    }

    @Override
    public Result<Void> deleteUser(String id) {
        User user = this.getById(id);
        if (user == null) {
            return Result.error(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 同步清除用户角色关联
        roleMapper.clearUserRoles(id);

        this.removeById(id);

        return Result.success();
    }

    @Override
    public Result<Void> assignRoles(String userId, List<Integer> roleIds) {
        // 验证用户是否存在
        User user = this.getById(userId);
        if (user == null) {
            return Result.error(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 清除用户现有角色
        roleMapper.clearUserRoles(userId);
        
        // 批量添加新角色
        if (roleIds != null && !roleIds.isEmpty()) {
            roleMapper.batchAddRolesForUser(userId, roleIds);
        }

        return Result.success();
    }

    @Override
    public Result<List<Integer>> getUserRoles(String userId) {
        // 验证用户是否存在
        User user = this.getById(userId);
        if (user == null) {
            return Result.error(ErrorCodeEnum.USER_NOT_FOUND);
        }

        // 获取用户角色列表
        List<Integer> roleIds = roleMapper.selectRoleIdsByUserId(userId);

        return Result.success(roleIds);
    }
}