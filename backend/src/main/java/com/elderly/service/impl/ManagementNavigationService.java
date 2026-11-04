package com.elderly.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

/** 菜单分配控制导航；API 权限仍由后端方法鉴权独立执行。 */
@Service
public class ManagementNavigationService {
    public record Menu(String title, String path, List<String> roles) {}
    private final JdbcTemplate jdbc;
    private final List<Menu> definitions;

    public ManagementNavigationService(JdbcTemplate jdbc, ObjectMapper mapper) throws IOException {
        this.jdbc = jdbc;
        try (var input = getClass().getResourceAsStream("/shared/admin-navigation.json")) {
            if (input == null) throw new IOException("Missing admin-navigation.json");
            definitions = List.copyOf(mapper.readValue(input, new TypeReference<List<Menu>>() {}));
        }
    }

    public List<Menu> forUser(Long userId) {
        if (userId == null) return List.of();
        var roles = jdbc.queryForList("SELECT r.id,r.role_key FROM sys_user u JOIN sys_role r ON r.id=u.role_id "
                + "WHERE u.id=? AND u.status=1", userId);
        if (roles.isEmpty()) return List.of();
        String role = (String) roles.get(0).get("role_key");
        if ("admin".equals(role)) return definitions;
        if (!"course_manager".equals(role)) return List.of();
        Set<String> assigned = new HashSet<>(jdbc.queryForList("SELECT m.path FROM sys_role_menu rm "
                + "JOIN sys_menu m ON m.id=rm.menu_id WHERE rm.role_id=?", String.class, roles.get(0).get("id")));
        return definitions.stream().filter(menu -> menu.roles().contains(role)
                && (assigned.contains(menu.path()) || menu.path().equals("/admin/profile"))).toList();
    }

    public Set<String> assignablePaths(String role) {
        if (!"course_manager".equals(role)) return Set.of();
        Set<String> paths = new HashSet<>();
        definitions.stream().filter(menu -> menu.roles().contains(role) && !menu.path().equals("/admin/profile"))
                .forEach(menu -> paths.add(menu.path()));
        return paths;
    }
}
