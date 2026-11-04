package com.elderly.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.Result;
import com.elderly.entity.FaqQa;
import com.elderly.service.impl.FaqQaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审核常见问题库管理（ADMIN）：老人端提问优先命中已审核问答
 */
@RestController
@RequestMapping("/api/faq")
@RequiredArgsConstructor
public class FaqController {

    private final FaqQaService faqQaService;

    /** 管理端：分页查询（分类/状态/关键词过滤） */
    @GetMapping("/admin/page")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<FaqQa>> adminPage(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String keyword) {
        return Result.success(faqQaService.adminPage(page, size, category, status, keyword));
    }

    /** 新增/修改审核问答（status=1 即审核通过并对老人生效） */
    @PostMapping("/admin/save")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> save(@RequestBody FaqQa faq) {
        faqQaService.save(faq);
        return Result.success();
    }

    /** 删除审核问答 */
    @DeleteMapping("/admin/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        faqQaService.delete(id);
        return Result.success();
    }
}
