package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.dto.UserLoginDTO;
import com.sjdt.scheduler.domain.dto.UserRegisterDTO;
import com.sjdt.scheduler.domain.dto.UserUpdateDTO;
import com.sjdt.scheduler.domain.po.User;
import com.sjdt.scheduler.domain.vo.LoginResponseVO;
import com.sjdt.scheduler.domain.vo.UserVO;

import java.util.List;

/**
 * 用户服务接口
 */
public interface IUserService extends IService<User> {
    
    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 用户信息
     */
    Result<UserVO> register(UserRegisterDTO registerDTO);
    
    /**
     * 用户登录
     * @param loginDTO 登录信息
     * @return 登录响应，包含用户信息和token
     */
    Result<LoginResponseVO> login(UserLoginDTO loginDTO);
    
    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户信息
     */
    User findByUsername(String username);

    /**
     * 分页查询用户列表
     * @param page 页码
     * @param size 每页大小
     * @param username 用户名模糊查询
     * @return 用户分页列表
     */
    Result<IPage<UserVO>> getUserList(int page, int size, String username);

    /**
     * 根据ID查询用户详情
     * @param id 用户ID
     * @return 用户详情
     */
    Result<UserVO> getUserById(String id);

    /**
     * 创建用户
     * @param user 用户信息
     * @return 创建结果
     */
    Result<UserVO> createUser(User user);

    /**
     * 更新用户信息
     * @param id 用户ID
     * @param userUpdateDTO 更新信息
     * @return 更新结果
     */
    Result<UserVO> updateUser(String id, UserUpdateDTO userUpdateDTO);

    /**
     * 删除用户
     * @param id 用户ID
     * @return 删除结果
     */
    Result<Void> deleteUser(String id);

    /**
     * 为用户分配角色
     * @param userId 用户ID
     * @param roleIds 角色ID列表
     * @return 分配结果
     */
    Result<Void> assignRoles(String userId, List<Integer> roleIds);

    /**
     * 获取用户的角色列表
     * @param userId 用户ID
     * @return 角色ID列表
     */
    Result<List<Integer>> getUserRoles(String userId);
}