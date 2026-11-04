package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.entity.UserMessage;
import com.elderly.mapper.UserMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户消息通知服务
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private final UserMessageMapper messageMapper;

    /** 我的消息分页 */
    public Page<UserMessage> myMessages(Long userId, long page, long size) {
        return messageMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<UserMessage>().eq("user_id", userId).orderByDesc("id"));
    }

    /** 未读数 + 消息总数 */
    public Map<String, Object> summary(Long userId) {
        Long unread = messageMapper.selectCount(new QueryWrapper<UserMessage>()
                .eq("user_id", userId).eq("is_read", 0));
        Map<String, Object> map = new HashMap<>();
        map.put("unread", unread == null ? 0 : unread);
        return map;
    }

    /** 标记单条已读（校验属主） */
    @Transactional
    public void markRead(Long userId, Long id) {
        UserMessage message = messageMapper.selectById(id);
        if (message != null && message.getUserId().equals(userId)) {
            message.setIsRead(1);
            messageMapper.updateById(message);
        }
    }

    /** 全部已读 */
    @Transactional
    public void markAllRead(Long userId) {
        UserMessage update = new UserMessage();
        update.setIsRead(1);
        messageMapper.update(update, new QueryWrapper<UserMessage>()
                .eq("user_id", userId).eq("is_read", 0));
    }
}
