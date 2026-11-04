package com.elderly.controller;

import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.dto.AiChatDTO;
import com.elderly.dto.ChatReply;
import com.elderly.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 智能问答（需登录）
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    /** 问答：审核FAQ优先，大模型/本地知识库兜底，返回 {answer, source} */
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@Valid @RequestBody AiChatDTO dto) {
        ChatReply reply = aiChatService.chat(SecurityUtil.getCurrentUserId(), dto.getQuestion(), dto.getHistory());
        Map<String, String> data = new LinkedHashMap<>();
        data.put("answer", reply.answer());
        data.put("source", reply.source());
        data.put("notice", reply.notice());
        return Result.success(data);
    }
}
