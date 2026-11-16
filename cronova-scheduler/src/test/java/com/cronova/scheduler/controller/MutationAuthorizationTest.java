package com.cronova.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MutationAuthorizationTest {

    @Test
    void backofficeMutationEndpointsRequireAdminRole() {
        List<Class<?>> controllers = List.of(
                CallbackJobController.class,
                CallbackJobRunController.class,
                HealthCheckController.class,
                ScriptController.class,
                TagController.class,
                TaskController.class,
                TaskRunController.class,
                UserController.class,
                WorkerFrontController.class
        );

        for (Class<?> controller : controllers) {
            SaCheckRole controllerRole = controller.getAnnotation(SaCheckRole.class);
            for (Method method : controller.getDeclaredMethods()) {
                if (!isMutation(method)) {
                    continue;
                }
                SaCheckRole methodRole = method.getAnnotation(SaCheckRole.class);
                SaCheckRole effectiveRole = methodRole != null ? methodRole : controllerRole;
                boolean requiresAdmin = effectiveRole != null
                        && List.of(effectiveRole.value()).contains("admin");
                assertTrue(requiresAdmin,
                        controller.getSimpleName() + "#" + method.getName() + " must require the admin role");
            }
        }
    }

    private boolean isMutation(Method method) {
        return method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class);
    }
}
