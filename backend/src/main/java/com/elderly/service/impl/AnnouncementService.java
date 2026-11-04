package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.BizException;
import com.elderly.entity.Announcement;
import com.elderly.entity.SysRole;
import com.elderly.entity.SysUser;
import com.elderly.entity.UserMessage;
import com.elderly.mapper.AnnouncementMapper;
import com.elderly.mapper.SysRoleMapper;
import com.elderly.mapper.SysUserMapper;
import com.elderly.mapper.UserMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台公告服务：移动端只读已发布公告，管理端 CRUD；
 * 公告发布时给全部老年学员扇出消息通知。
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final UserMessageMapper userMessageMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    /** 移动端：已发布公告（按发布时间倒序） */
    public List<Announcement> publishedList(String type) {
        QueryWrapper<Announcement> wrapper = new QueryWrapper<Announcement>()
                .eq("status", 1)
                .le("publish_time", LocalDateTime.now())
                .orderByDesc("publish_time");
        if (type != null && !type.isEmpty()) {
            wrapper.eq("ann_type", type);
        }
        return announcementMapper.selectList(wrapper);
    }

    /** 管理端：全部分页 */
    public Page<Announcement> adminPage(long page, long size, String type) {
        QueryWrapper<Announcement> wrapper = new QueryWrapper<Announcement>().orderByDesc("id");
        if (type != null && !type.isEmpty()) {
            wrapper.eq("ann_type", type);
        }
        return announcementMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /** 新增/修改公告；状态为发布时设置发布时间并扇出消息 */
    @Transactional
    public void save(Announcement ann, Long operatorId) {
        boolean wasPublished = ann.getId() != null;
        Announcement old = wasPublished ? announcementMapper.selectById(ann.getId()) : null;
        if (wasPublished && old == null) {
            throw new BizException("公告不存在");
        }
        boolean publishNow = false;
        if (ann.getStatus() != null && ann.getStatus() == 1) {
            publishNow = old == null || old.getStatus() == null || old.getStatus() == 0;
            if (old == null || old.getPublishTime() == null) {
                ann.setPublishTime(LocalDateTime.now());
            }
        }
        ann.setCreateBy(operatorId);
        if (ann.getId() == null) {
            announcementMapper.insert(ann);
        } else {
            announcementMapper.updateById(ann);
        }
        // 首次发布 → 给所有老年学员推送消息
        if (publishNow) {
            fanOutMessages(ann);
        }
    }

    /** 删除公告 */
    public void delete(Long id) {
        announcementMapper.deleteById(id);
    }

    /**
     * 给全部老年学员扇出消息（幂等：同一公告同一用户不重复推）。
     * 后台学员规模有限（演示+虚拟用户），直接批量插入即可。
     */
    private void fanOutMessages(Announcement ann) {
        SysRole userRole = sysRoleMapper.selectOne(new QueryWrapper<SysRole>().eq("role_key", "user"));
        if (userRole == null) {
            return;
        }
        List<SysUser> users = sysUserMapper.selectList(
                new QueryWrapper<SysUser>().eq("role_id", userRole.getId()));
        for (SysUser user : users) {
            Long exists = userMessageMapper.selectCount(new QueryWrapper<UserMessage>()
                    .eq("user_id", user.getId())
                    .eq("msg_type", ann.getAnnType() == null ? "SYSTEM" : ann.getAnnType())
                    .eq("biz_id", ann.getId()));
            if (exists != null && exists > 0) {
                continue;
            }
            UserMessage message = new UserMessage();
            message.setUserId(user.getId());
            message.setTitle(ann.getTitle());
            message.setContent(ann.getContent() != null && ann.getContent().length() > 500
                    ? ann.getContent().substring(0, 500) : ann.getContent());
            message.setMsgType("GOV".equals(ann.getAnnType()) ? "GOV" : "SYSTEM");
            message.setBizId(ann.getId());
            message.setIsRead(0);
            message.setCreateTime(LocalDateTime.now());
            userMessageMapper.insert(message);
        }
    }
}
