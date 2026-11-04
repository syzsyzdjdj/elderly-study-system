package com.elderly.service.impl;

import com.elderly.common.BizException;
import com.elderly.dto.LoginResult;
import com.elderly.entity.SysUser;
import com.elderly.mapper.SysRoleMapper;
import com.elderly.mapper.SysUserMapper;
import com.elderly.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Opaque, revocable device credential. Passwords and raw session tokens are never stored here. */
@Service
@RequiredArgsConstructor
public class PersistentLoginService {
    public static final long LIFETIME_SECONDS = 30L * 24 * 60 * 60;
    private final JdbcTemplate jdbc;
    private final SysUserMapper users;
    private final SysRoleMapper roles;
    private final JwtUtil jwt;

    @Transactional
    public String issue(Long userId) {
        SysUser user = activeUser(userId);
        // Management accounts retain the original short session policy.
        if (!"user".equals(role(user))) return null;
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long now = System.currentTimeMillis();
        jdbc.update("DELETE FROM login_session WHERE expires_at_ms <= ?", now);
        jdbc.update("INSERT INTO login_session(token_hash,user_id,password_stamp,expires_at_ms,last_used_at_ms) VALUES(?,?,?,?,?)",
                hash(token),userId,hash(user.getPassword()),now + LIFETIME_SECONDS * 1000,now);
        return token;
    }

    @Transactional
    public LoginResult refresh(String token) {
        if (!validToken(token)) throw expired();
        var rows = jdbc.queryForList("SELECT user_id,password_stamp,expires_at_ms FROM login_session WHERE token_hash=?",hash(token));
        if (rows.size()!=1 || ((Number)rows.get(0).get("expires_at_ms")).longValue() <= System.currentTimeMillis()) throw expired();
        var row = rows.get(0);
        SysUser user = activeUser(((Number)row.get("user_id")).longValue());
        if (!hash(user.getPassword()).equals(row.get("password_stamp")) || !"user".equals(role(user))) throw expired();
        jdbc.update("UPDATE login_session SET last_used_at_ms=? WHERE token_hash=?",System.currentTimeMillis(),hash(token));
        LoginResult result = new LoginResult();
        result.setToken(jwt.generateTokenUntil(user.getId(),user.getUsername(),"user",((Number)row.get("expires_at_ms")).longValue()));
        result.setRoleKey("user"); user.setPassword(null); result.setUserInfo(user);
        return result;
    }

    public void revoke(String token) {
        if (validToken(token)) jdbc.update("DELETE FROM login_session WHERE token_hash=?",hash(token));
    }
    private SysUser activeUser(Long id) {
        SysUser user = users.selectById(id);
        if (user==null || user.getStatus()==null || user.getStatus()!=1) throw expired();
        return user;
    }
    private String role(SysUser user) {
        var role = user.getRoleId()==null ? null : roles.selectById(user.getRoleId());
        return role==null ? "user" : role.getRoleKey();
    }
    private static boolean validToken(String token) { return token!=null && token.matches("[A-Za-z0-9_-]{43}"); }
    private static BizException expired() { return new BizException("保持登录已失效，请重新登录"); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("登录凭证处理失败",e); }
    }
}
