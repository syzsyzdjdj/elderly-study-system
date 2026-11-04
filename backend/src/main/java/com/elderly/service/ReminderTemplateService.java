package com.elderly.service;

import com.elderly.entity.Reminder;
import com.elderly.entity.ReminderTemplate;

import java.util.List;

/**
 * 定时提醒模板服务（管理员配置模板，老人端一键选用）
 */
public interface ReminderTemplateService {

    /** 老人端：查询所有启用的模板 */
    List<ReminderTemplate> listEnabled();

    /** 管理端：查询全部模板（含停用） */
    List<ReminderTemplate> listAll();

    /** 管理端：新增/修改模板（id 为空新增） */
    ReminderTemplate save(ReminderTemplate template);

    /** 管理端：删除模板 */
    void delete(Long id);

    /** 老人端：基于模板创建一条属于自己的提醒 */
    Reminder useTemplate(Long templateId, Long userId);
}
