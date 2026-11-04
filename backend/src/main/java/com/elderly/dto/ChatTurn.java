package com.elderly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Client context is conversation data; clients cannot supply system instructions. */
public record ChatTurn(
        @NotBlank @Pattern(regexp = "user|assistant", message = "历史消息角色无效") String role,
        @NotBlank @Size(max = 1000, message = "历史消息不能超过1000字") String content) {}
