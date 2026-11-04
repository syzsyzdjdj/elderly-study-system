package com.elderly.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** 密码（BCrypt 加密；只允许写入，禁止序列化输出） */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 昵称 */
    private String nickname;

    /** 手机号 */
    private String phone;

    /** 头像 */
    private String avatar;

    /** 角色ID */
    private Long roleId;

    /** 角色名称(非表字段，列表填充用) */
    @TableField(exist = false)
    private String roleName;

    /** 角色标识(非表字段，列表填充用) */
    @TableField(exist = false)
    private String roleKey;

    /** 积分 */
    private Integer points;

    /** 常住省 */
    private String province;

    /** 常住市 */
    private String city;

    /** 常住区县 */
    private String district;

    /** 界面字号百分比(100/115/130) */
    private Integer fontScale;

    /** 高对比度模式(0关 1开) */
    private Integer highContrast;

    /** 字体加粗(0常规 1加粗) */
    private Integer fontBold;

    /** 行距百分比(140紧凑/160标准/190宽松) */
    private Integer lineHeight;

    /** 朗读音色key（语音包，default/elderly/edge:sichuan 等） */
    private String voice;

    /** 状态(0禁用 1正常) */
    private Integer status;

    /** 数据来源标记，由模拟生成器维护，不接受外部 JSON 写入。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Integer isMock;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
