package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 数字陪伴话术
 */
@Data
@TableName("ai_companion_script")
public class AiCompanionScript {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** GREETING/FESTIVAL/ENCOURAGE/RECOMMEND */
    private String scene;

    private String keyword;

    private String title;

    private String content;

    private Integer enabled;

    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
