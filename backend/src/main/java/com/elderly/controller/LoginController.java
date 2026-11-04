package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.dto.LoginDTO;
import com.elderly.dto.LoginResult;
import com.elderly.dto.RegisterDTO;
import com.elderly.service.SysUserService;
import com.elderly.service.impl.PersistentLoginService;
import com.elderly.common.BizException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录 / 注册 / 退出
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LoginController {

    private final SysUserService sysUserService;
    private final PersistentLoginService persistentLoginService;
    @Value("${app.session.secure-cookie:true}") private boolean secureCookie;
    private static final String COOKIE = "elderly_session";

    private void cookie(HttpServletResponse response, String token) {
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE,token==null ? "" : token)
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api")
                .maxAge(token==null ? 0 : PersistentLoginService.LIFETIME_SECONDS).build().toString());
    }

    /** 登录：返回 token + 用户信息 + 角色标识 */
    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginDTO loginDTO,
                                     @CookieValue(value=COOKIE,required=false) String previous,
                                     HttpServletResponse response) {
        LoginResult result = sysUserService.login(loginDTO);
        persistentLoginService.revoke(previous);
        cookie(response,loginDTO.isRememberMe() ? persistentLoginService.issue(result.getUserInfo().getId()) : null);
        return Result.success(result);
    }

    /** 退出登录（后端无状态，直接成功） */
    @PostMapping("/logout")
    public Result<Void> logout(@CookieValue(value=COOKIE,required=false) String token,HttpServletResponse response) {
        persistentLoginService.revoke(token); cookie(response,null);
        return Result.success();
    }

    @PostMapping("/session/refresh")
    public ResponseEntity<Result<LoginResult>> refresh(@CookieValue(value=COOKIE,required=false) String token,
            @RequestHeader(value="X-Session-Request",required=false) String requested,HttpServletResponse response) {
        if (!"1".equals(requested)) return ResponseEntity.status(403).body(Result.error(403,"请求无效"));
        try { return ResponseEntity.ok(Result.success(persistentLoginService.refresh(token))); }
        catch (BizException e) { cookie(response,null); return ResponseEntity.status(401).body(Result.error(401,e.getMessage())); }
    }

    /** 注册（默认 user 角色） */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO registerDTO) {
        sysUserService.register(registerDTO);
        return Result.success();
    }
}
