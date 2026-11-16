package com.cronova.scheduler.controller;

import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.dto.UserRegisterDTO;
import com.cronova.scheduler.domain.vo.UserVO;
import com.cronova.scheduler.service.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private IUserService userService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userService);
    }

    @Test
    void rejectsRegistrationWhenDisabled() {
        ReflectionTestUtils.setField(controller, "registrationEnabled", false);

        Result<UserVO> result = controller.register(registerRequest());

        assertEquals(403, result.getCode());
        verifyNoInteractions(userService);
    }

    @Test
    void delegatesRegistrationWhenEnabled() {
        ReflectionTestUtils.setField(controller, "registrationEnabled", true);
        Result<UserVO> expected = Result.success(UserVO.builder().username("alice").build());
        when(userService.register(registerRequest())).thenReturn(expected);

        Result<UserVO> result = controller.register(registerRequest());

        assertEquals(expected, result);
        verify(userService).register(registerRequest());
    }

    private UserRegisterDTO registerRequest() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("alice");
        dto.setNickname("Alice");
        dto.setPassword("password123");
        return dto;
    }
}
