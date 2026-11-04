package com.elderly.controller;

import com.elderly.common.BizException;
import com.elderly.dto.LoginResult;
import com.elderly.entity.SysUser;
import com.elderly.service.SysUserService;
import com.elderly.service.impl.PersistentLoginService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

class PersistentLoginControllerTest {
    MockMvc mvc;
    PersistentLoginService sessions;
    @BeforeEach void setup() {
        var users=mock(SysUserService.class);sessions=mock(PersistentLoginService.class);
        var result=new LoginResult();var user=new SysUser();user.setId(1L);result.setUserInfo(user);result.setRoleKey("user");result.setToken("access");
        when(users.login(any())).thenReturn(result);
        when(sessions.issue(1L)).thenReturn("p".repeat(43));
        var controller=new LoginController(users,sessions);
        ReflectionTestUtils.setField(controller,"secureCookie",true);
        mvc=MockMvcBuilders.standaloneSetup(controller).build();
    }
    @Test void keepLoginCookieIsHttpOnlySecureAndExplicitLogoutClearsIt() throws Exception {
        mvc.perform(post("/api/login").contentType("application/json")
                .content("{\"username\":\"learner\",\"password\":\"test123\",\"rememberMe\":true}"))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie",containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie",containsString("Secure")))
                .andExpect(header().string("Set-Cookie",containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie",containsString("Max-Age=2592000")));
        mvc.perform(post("/api/logout").cookie(new Cookie("elderly_session","p".repeat(43))))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie",containsString("Max-Age=0")));
        verify(sessions).revoke("p".repeat(43));
    }
    @Test void renewalRequiresCustomHeaderAndInvalidCredentialReturns401() throws Exception {
        mvc.perform(post("/api/session/refresh")).andExpect(status().isForbidden());
        when(sessions.refresh(any())).thenThrow(new BizException("expired"));
        mvc.perform(post("/api/session/refresh").header("X-Session-Request","1").cookie(new Cookie("elderly_session","bad")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
}
