package com.elderly.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.BizException;
import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.UserMessage;
import com.elderly.service.impl.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户消息通知（老人端）
 */
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    private Long userId() {
        Long id = SecurityUtil.getCurrentUserId();
        if (id == null) {
            throw new BizException("请先登录");
        }
        return id;
    }

    /** 我的消息分页 */
    @GetMapping("/list")
    public Result<Page<UserMessage>> list(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        return Result.success(messageService.myMessages(userId(), page, size));
    }

    /** 未读数等汇总 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.success(messageService.summary(userId()));
    }

    /** 标记单条已读 */
    @PostMapping("/read/{id}")
    public Result<Void> read(@PathVariable Long id) {
        messageService.markRead(userId(), id);
        return Result.success();
    }

    /** 全部已读 */
    @PostMapping("/read-all")
    public Result<Void> readAll() {
        messageService.markAllRead(userId());
        return Result.success();
    }
}
