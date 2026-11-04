package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.entity.SysMenu;
import com.elderly.mapper.SysMenuMapper;
import com.elderly.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统菜单服务实现
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuMapper sysMenuMapper;

    @Override
    public List<SysMenu> listTree() {
        List<SysMenu> all = sysMenuMapper.selectList(new QueryWrapper<SysMenu>().orderByAsc("sort"));
        // 按父ID分组（null 视为顶级 0）
        Map<Long, List<SysMenu>> childrenMap = all.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        List<SysMenu> tree = childrenMap.getOrDefault(0L, new ArrayList<>());
        buildTree(tree, childrenMap);
        return tree;
    }

    /** 递归挂载子菜单 */
    private void buildTree(List<SysMenu> nodes, Map<Long, List<SysMenu>> childrenMap) {
        for (SysMenu node : nodes) {
            node.setChildren(childrenMap.getOrDefault(node.getId(), new ArrayList<>()));
            buildTree(node.getChildren(), childrenMap);
        }
    }
}
