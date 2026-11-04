package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核常见问题（权威问答）：人工审核后启用，老人提问优先命中
 */
@Data
@TableName("faq_qa")
public class FaqQa {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标准问法（完整等于时优先命中） */
    private String question;

    /** 匹配关键词，英文逗号分隔 */
    private String keywords;

    /** 权威解答 */
    private String answer;

    /** 业务分类 */
    private String category;

    /** 1=已审核启用 0=停用 */
    private Integer status;

    /** 命中次数 */
    private Long hitCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
