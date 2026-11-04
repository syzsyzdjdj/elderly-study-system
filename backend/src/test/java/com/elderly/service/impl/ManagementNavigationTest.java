package com.elderly.service.impl;

import com.elderly.common.BizException;
import com.elderly.entity.SysRole;
import com.elderly.entity.SysMenu;
import com.elderly.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagementNavigationTest {
    DriverManagerDataSource ds;
    JdbcTemplate jdbc;
    ManagementNavigationService service;

    @BeforeEach void setup() throws Exception {
        ds = new DriverManagerDataSource("jdbc:h2:mem:menus-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE sys_role(id BIGINT PRIMARY KEY,role_key VARCHAR(50))");
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,role_id BIGINT,status INT)");
        jdbc.execute("CREATE TABLE sys_menu(id BIGINT AUTO_INCREMENT PRIMARY KEY,parent_id BIGINT,menu_name VARCHAR(100),path VARCHAR(100),component VARCHAR(100),perms VARCHAR(100),sort INT)");
        jdbc.execute("CREATE TABLE sys_role_menu(id BIGINT AUTO_INCREMENT PRIMARY KEY,role_id BIGINT,menu_id BIGINT)");
        jdbc.update("INSERT INTO sys_role VALUES(1,'admin'),(2,'course_manager'),(3,'user')");
        jdbc.update("INSERT INTO sys_user VALUES(1,1,1),(2,2,1),(3,3,1)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V27__management_navigation.sql")).execute(ds);
        service = new ManagementNavigationService(jdbc, new ObjectMapper());
    }

    @Test void migrationGrantsContentMenusAndNavigationReflectsRevocation() {
        assertEquals(List.of("/admin/courses", "/admin/categories", "/admin/profile"),
                service.forUser(2L).stream().map(ManagementNavigationService.Menu::path).toList());
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id=2");
        assertEquals(List.of("/admin/profile"), service.forUser(2L).stream().map(ManagementNavigationService.Menu::path).toList());
        assertTrue(service.forUser(1L).stream().anyMatch(menu -> menu.path().equals("/admin/roles")));
        assertTrue(service.forUser(3L).isEmpty());
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=2");
        assertTrue(service.forUser(2L).isEmpty());
    }

    @Test void migrationPreservesExistingAssignments() {
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id=2");
        jdbc.update("INSERT INTO sys_role_menu(role_id,menu_id) SELECT 2,id FROM sys_menu WHERE path='/admin/categories'");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V27__management_navigation.sql")).execute(ds);
        assertEquals(List.of("/admin/categories", "/admin/profile"),
                service.forUser(2L).stream().map(ManagementNavigationService.Menu::path).toList());
    }

    @Test void incompatibleAssignmentsAreRejectedBeforeDeletingExistingOnes() {
        var roles = mock(SysRoleMapper.class); var links = mock(SysRoleMenuMapper.class); var menus = mock(SysMenuMapper.class);
        var role = new SysRole(); role.setRoleKey("course_manager");
        when(roles.selectById(2L)).thenReturn(role);
        var adminMenu = new SysMenu(); adminMenu.setPath("/admin/users");
        when(menus.selectBatchIds(List.of(8L))).thenReturn(List.of(adminMenu));
        var roleService = new SysRoleServiceImpl(roles, links, menus, service);
        assertThrows(BizException.class, () -> roleService.assignMenu(2L, List.of(8L)));
        verifyNoInteractions(links);
        role.setRoleKey("admin");
        assertThrows(BizException.class, () -> roleService.assignMenu(2L, List.of()));
        verifyNoInteractions(links);
    }
}
