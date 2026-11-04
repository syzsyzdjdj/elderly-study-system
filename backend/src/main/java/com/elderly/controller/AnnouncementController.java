package com.elderly.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.Announcement;
import com.elderly.service.impl.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 平台公告：老人端查看已发布公告，管理端维护
 */
@RestController
@RequestMapping("/api/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** 老人端：已发布公告列表（可按类型 SYSTEM/GOV 过滤） */
    @GetMapping("/list")
    public Result<List<Announcement>> list(@RequestParam(required = false) String type) {
        return Result.success(announcementService.publishedList(type));
    }

    /** 管理端：分页查询全部公告 */
    @GetMapping("/admin/page")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Page<Announcement>> adminPage(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String type) {
        return Result.success(announcementService.adminPage(page, size, type));
    }

    /** 新增/修改公告（发布时自动推送消息给老年学员） */
    @PostMapping("/admin/save")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Void> save(@RequestBody Announcement announcement) {
        announcementService.save(announcement, SecurityUtil.getCurrentUserId());
        return Result.success();
    }

    /** 删除公告 */
    @DeleteMapping("/admin/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return Result.success();
    }
}
