package com.elderly.service;

import com.elderly.entity.SysMenu;

import java.util.List;

/**
 * 系统菜单服务
 */
public interface SysMenuService {

    /** 菜单树(admin) */
    List<SysMenu> listTree();
}
