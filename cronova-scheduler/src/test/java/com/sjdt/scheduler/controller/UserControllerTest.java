package com.sjdt.scheduler.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.config.AuthConfig;
import com.sjdt.scheduler.domain.dto.UserUpdateDTO;
import com.sjdt.scheduler.domain.vo.UserVO;
import com.sjdt.scheduler.service.IUserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController测试类
 */
@WebMvcTest(value = UserController.class, 
           excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, 
                                                classes = {AuthConfig.class, com.sjdt.scheduler.interceptor.AKSKInterceptor.class}))
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private IUserService userService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetUserList() throws Exception {
        // 模拟服务层返回结果
        IPage<UserVO> userPage = new Page<>();
        Result<IPage<UserVO>> result = Result.success(userPage);
        Mockito.when(userService.getUserList(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyString()))
               .thenReturn(result);
        
        // 发送GET请求
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.get("/api/users")
                .param("page", "1")
                .param("size", "10")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        // 验证响应
        String content = mvcResult.getResponse().getContentAsString();
        Assertions.assertNotNull(content);
        
        if (!content.isEmpty()) {
            Result<?> responseResult = objectMapper.readValue(content, Result.class);
            Assertions.assertEquals(200, responseResult.getCode());
        }
    }

    @Test
    void testGetUserById() throws Exception {
        // 模拟服务层返回结果
        UserVO userVO = new UserVO();
        userVO.setId("test-id");
        userVO.setUsername("testuser");
        Result<UserVO> result = Result.success(userVO);
        Mockito.when(userService.getUserById(Mockito.anyString())).thenReturn(result);
        
        // 发送GET请求
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.get("/api/users/test-id")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        // 验证响应
        String content = mvcResult.getResponse().getContentAsString();
        Assertions.assertNotNull(content);
        
        if (!content.isEmpty()) {
            Result<?> responseResult = objectMapper.readValue(content, Result.class);
            Assertions.assertEquals(200, responseResult.getCode());
        }
    }

    @Test
    void testCreateUser() throws Exception {
        // 创建测试用户数据
        UserVO userVO = new UserVO();
        userVO.setId("test-id");
        userVO.setUsername("testuser");
        userVO.setNickname("测试用户");
        
        // 模拟服务层返回结果
        Result<UserVO> result = Result.success(userVO);
        Mockito.when(userService.createUser(Mockito.any())).thenReturn(result);
        
        // 发送POST请求
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userVO)))
                .andExpect(status().isOk())
                .andReturn();
        
        // 验证响应
        String content = mvcResult.getResponse().getContentAsString();
        Assertions.assertNotNull(content);
        
        if (!content.isEmpty()) {
            Result<?> responseResult = objectMapper.readValue(content, Result.class);
            Assertions.assertEquals(200, responseResult.getCode());
        }
    }

    @Test
    void testUpdateUser() throws Exception {
        // 创建测试更新数据
        UserUpdateDTO updateDTO = new UserUpdateDTO();
        updateDTO.setNickname("更新后的测试用户");
        
        // 创建测试用户VO
        UserVO userVO = new UserVO();
        userVO.setId("test-id");
        userVO.setUsername("testuser");
        userVO.setNickname("更新后的测试用户");
        
        // 模拟服务层返回结果
        Result<UserVO> result = Result.success(userVO);
        Mockito.when(userService.updateUser(Mockito.anyString(), Mockito.any(UserUpdateDTO.class))).thenReturn(result);
        
        // 发送PUT请求
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.put("/api/users/test-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andReturn();
        
        // 验证响应
        String content = mvcResult.getResponse().getContentAsString();
        Assertions.assertNotNull(content);
        
        if (!content.isEmpty()) {
            Result<?> responseResult = objectMapper.readValue(content, Result.class);
            Assertions.assertEquals(200, responseResult.getCode());
        }
    }

    @Test
    void testDeleteUser() throws Exception {
        // 模拟服务层返回结果
        Result<Void> result = Result.success();
        Mockito.when(userService.deleteUser(Mockito.anyString())).thenReturn(result);
        
        // 发送DELETE请求
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.delete("/api/users/test-id")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        // 验证响应
        String content = mvcResult.getResponse().getContentAsString();
        Assertions.assertNotNull(content);
        
        if (!content.isEmpty()) {
            Result<?> responseResult = objectMapper.readValue(content, Result.class);
            Assertions.assertEquals(200, responseResult.getCode());
        }
    }

    @Test
    void testAssignRoles() throws Exception {
        // 创建测试角色ID列表
        List<Integer> roleIds = List.of(1, 2);
        
        // 模拟服务层返回结果
        Result<Void> result = Result.success();
        Mockito.when(userService.assignRoles(Mockito.anyString(), Mockito.anyList())).thenReturn(result);
        
        // 发送POST请求
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post("/api/users/test-id/roles")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(roleIds)))
                .andExpect(status().isOk())
                .andReturn();
        
        // 验证响应
        String content = mvcResult.getResponse().getContentAsString();
        Assertions.assertNotNull(content);
        
        if (!content.isEmpty()) {
            Result<?> responseResult = objectMapper.readValue(content, Result.class);
            Assertions.assertEquals(200, responseResult.getCode());
        }
    }

    @Test
    void testGetUserRoles() throws Exception {
        // 创建测试角色ID列表
        List<Integer> roleIds = List.of(1, 2);
        
        // 模拟服务层返回结果
        Result<List<Integer>> result = Result.success(roleIds);
        Mockito.when(userService.getUserRoles(Mockito.anyString())).thenReturn(result);
        
        // 发送GET请求
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.get("/api/users/test-id/roles")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();
        
        // 验证响应
        String content = mvcResult.getResponse().getContentAsString();
        Assertions.assertNotNull(content);
        
        if (!content.isEmpty()) {
            Result<?> responseResult = objectMapper.readValue(content, Result.class);
            Assertions.assertEquals(200, responseResult.getCode());
        }
    }
}