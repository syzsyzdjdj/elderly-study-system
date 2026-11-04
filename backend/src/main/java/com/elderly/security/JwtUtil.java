package com.elderly.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（jjwt 0.12.x）
 * token 中携带 userId(subject)、username、roleKey
 */
@Component
public class JwtUtil {

    /** 签名密钥 */
    private final SecretKey key;

    /** 有效期（毫秒） */
    private final long expire;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire}") long expire) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("请通过 JWT_SECRET 或开发机私有配置设置 JWT 密钥");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expire = expire;
    }

    /**
     * 生成 token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param roleKey  角色标识（admin/course_manager/user）
     */
    public String generateToken(Long userId, String username, String roleKey) {
        return generateTokenUntil(userId, username, roleKey, System.currentTimeMillis() + expire);
    }

    public String generateTokenUntil(Long userId, String username, String roleKey, long sessionExpiresAt) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", roleKey)
                .issuedAt(new Date())
                .expiration(new Date(Math.min(System.currentTimeMillis() + expire, sessionExpiresAt)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，返回 Claims（过期/签名错误将抛出异常）
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
