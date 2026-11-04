package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.common.BizException;
import com.elderly.entity.SysRole;
import com.elderly.entity.SysRoleMenu;
import com.elderly.mapper.SysRoleMapper;
import com.elderly.mapper.SysRoleMenuMapper;
import com.elderly.mapper.SysMenuMapper;
import com.elderly.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统角色服务实现
 */
@Service
@RequiredArgsConstructor
public class SysRoleServiceImpl implements SysRoleService {

    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final ManagementNavigationService navigation;

    @Override
    public List<SysRole> list() {
        List<SysRole> roles = sysRoleMapper.selectList(new QueryWrapper<SysRole>().orderByAsc("id"));
        Map<Long, List<Long>> menus = sysRoleMenuMapper.selectList(null).stream().collect(Collectors.groupingBy(
                SysRoleMenu::getRoleId, Collectors.mapping(SysRoleMenu::getMenuId, Collectors.toList())));
        roles.forEach(role -> role.setMenuIds(menus.getOrDefault(role.getId(), List.of())));
        return roles;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignMenu(Long roleId, List<Long> menuIds) {
        if (roleId == null) {
            throw new BizException("角色ID不能为空");
        }
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        if (!"course_manager".equals(role.getRoleKey())) {
            throw new BizException("仅课程管理员支持菜单分配；管理员拥有全部菜单，学员使用学习端");
        }
        List<Long> selected = menuIds == null ? List.of() : new ArrayList<>(new HashSet<>(menuIds));
        if (selected.stream().anyMatch(java.util.Objects::isNull)) throw new BizException("菜单ID不能为空");
        if (!selected.isEmpty()) {
            var menus = sysMenuMapper.selectBatchIds(selected);
            var allowed = navigation.assignablePaths(role.getRoleKey());
            if (menus.size() != selected.size() || menus.stream().anyMatch(menu -> !allowed.contains(menu.getPath()))) {
                throw new BizException("选择的菜单不适用于该角色");
            }
        }
        // 先删旧关联
        sysRoleMenuMapper.delete(new QueryWrapper<SysRoleMenu>().eq("role_id", roleId));
        // 再插新关联
        if (!selected.isEmpty()) {
            for (Long menuId : selected) {
                SysRoleMenu roleMenu = new SysRoleMenu();
                roleMenu.setRoleId(roleId);
                roleMenu.setMenuId(menuId);
                sysRoleMenuMapper.insert(roleMenu);
            }
        }
    }
}
