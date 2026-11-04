package com.elderly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import java.util.List;
import lombok.Data;

/**
 * AI 问答请求
 */
@Data
public class AiChatDTO {

    @NotBlank(message = "问题不能为空")
    @Size(max = 1000, message = "问题不能超过1000字")
    private String question;

    @Size(max = 6, message = "最多携带6条历史消息")
    private List<@NotNull @Valid ChatTurn> history = List.of();
}
