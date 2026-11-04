package com.elderly.service.impl;

import com.elderly.entity.SysRole;
import com.elderly.entity.SysUser;
import com.elderly.mapper.SysRoleMapper;
import com.elderly.mapper.SysUserMapper;
import com.elderly.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersistentLoginServiceTest {
    JdbcTemplate jdbc;
    PersistentLoginService service;
    JwtUtil jwt;
    String password = "bcrypt-password-stamp";
    String role = "user";
    int status = 1;
    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:session-"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)"); jdbc.update("INSERT INTO sys_user(id) VALUES(1)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V26__persistent_login.sql")).execute(ds);
        var users=mock(SysUserMapper.class); var roles=mock(SysRoleMapper.class);
        when(users.selectById(1L)).thenAnswer(inv -> {
            var user=new SysUser(); user.setId(1L);user.setUsername("learner");user.setPassword(password);user.setStatus(status);user.setRoleId(3L);return user;
        });
        when(roles.selectById(3L)).thenAnswer(inv -> { var value=new SysRole();value.setRoleKey(role);return value; });
        jwt=new JwtUtil("test-secret-for-persistent-session-1234567890",86400000);
        service=new PersistentLoginService(jdbc,users,roles,jwt);
    }
    @Test void issueRefreshAndLogoutStoreOnlyHashAndBoundTokenExpiry() {
        String token=service.issue(1L);
        assertEquals(43,token.length());
        String stored=jdbc.queryForObject("SELECT token_hash FROM login_session",String.class);
        assertNotEquals(token,stored); assertEquals(64,stored.length());
        long deadline=System.currentTimeMillis()+60000;
        jdbc.update("UPDATE login_session SET expires_at_ms=?",deadline);
        var result=service.refresh(token);
        assertEquals(1L,result.getUserInfo().getId()); assertNull(result.getUserInfo().getPassword());
        assertTrue(jwt.parseToken(result.getToken()).getExpiration().getTime()<=deadline);
        assertNotNull(service.refresh(token)); // another tab may use the same HttpOnly cookie
        service.revoke(token);
        assertThrows(RuntimeException.class,()->service.refresh(token));
    }
    @Test void expiryPasswordChangeAndDisabledAccountPreventRenewal() {
        String expired=service.issue(1L);jdbc.update("UPDATE login_session SET expires_at_ms=0");
        assertThrows(RuntimeException.class,()->service.refresh(expired));
        String changed=service.issue(1L);password="changed-bcrypt-password";
        assertThrows(RuntimeException.class,()->service.refresh(changed));
        String disabled=service.issue(1L);status=0;
        assertThrows(RuntimeException.class,()->service.refresh(disabled));
    }
    @Test void managementAccountsCannotReceiveLongSessionsAndRoleChangePreventsRenewal() {
        role="admin";assertNull(service.issue(1L));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM login_session",Integer.class));
        role="user";String token=service.issue(1L);role="course_manager";
        assertThrows(RuntimeException.class,()->service.refresh(token));
    }
}
