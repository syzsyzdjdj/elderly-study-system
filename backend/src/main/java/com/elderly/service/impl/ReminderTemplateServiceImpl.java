package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.elderly.common.BizException;
import com.elderly.entity.Reminder;
import com.elderly.entity.ReminderTemplate;
import com.elderly.mapper.ReminderTemplateMapper;
import com.elderly.service.ReminderService;
import com.elderly.service.ReminderTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReminderTemplateServiceImpl implements ReminderTemplateService {

    private final ReminderTemplateMapper templateMapper;
    private final ReminderService reminderService;

    @Override
    public List<ReminderTemplate> listEnabled() {
        return templateMapper.selectList(
                new QueryWrapper<ReminderTemplate>()
                        .eq("enabled", 1)
                        .orderByAsc("sort")
                        .orderByAsc("id"));
    }

    @Override
    public List<ReminderTemplate> listAll() {
        return templateMapper.selectList(
                new QueryWrapper<ReminderTemplate>()
                        .orderByAsc("sort")
                        .orderByAsc("id"));
    }

    @Override
    @Transactional
    public ReminderTemplate save(ReminderTemplate template) {
        if (template.getTemplateName() == null || template.getTemplateName().trim().isEmpty()) {
            throw new BizException("请填写模板名称");
        }
        if (template.getTitle() == null || template.getTitle().trim().isEmpty()) {
            throw new BizException("请填写提醒标题");
        }
        if (template.getContent() == null || template.getContent().trim().isEmpty()) {
            throw new BizException("请填写语音播报文本");
        }
        if (template.getEnabled() == null) template.setEnabled(1);
        if (template.getSort() == null) template.setSort(0);
        if (template.getRemindType() == null) template.setRemindType("DAILY");
        if (template.getRemindTime() == null) template.setRemindTime(java.time.LocalTime.of(9, 0));
        if (template.getId() == null) {
            templateMapper.insert(template);
        } else {
            templateMapper.updateById(template);
        }
        return template;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        templateMapper.deleteById(id);
    }

    @Override
    @Transactional
    public Reminder useTemplate(Long templateId, Long userId) {
        ReminderTemplate t = templateMapper.selectById(templateId);
        if (t == null) throw new BizException("提醒模板不存在");
        if (t.getEnabled() == null || t.getEnabled() != 1) throw new BizException("该模板已停用");

        Reminder r = new Reminder();
        r.setUserId(userId);
        r.setTitle(t.getTitle());
        r.setContent(t.getContent());
        r.setRemindType(t.getRemindType());
        r.setRemindTime(t.getRemindTime());
        r.setDayOfWeek(t.getDayOfWeek());
        r.setDayOfMonth(t.getDayOfMonth());
        r.setEnabled(1);
        return reminderService.create(r);
    }
}
