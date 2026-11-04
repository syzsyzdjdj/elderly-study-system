package com.elderly.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.Result;
import com.elderly.common.SecurityUtil;
import com.elderly.entity.Prize;
import com.elderly.entity.PrizeExchange;
import com.elderly.dto.SavePrizeDTO;
import com.elderly.dto.HandleExchangeDTO;
import jakarta.validation.Valid;
import com.elderly.mapper.PrizeMapper;
import com.elderly.service.impl.PrizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 福利奖品与兑换（老人端兑换 + 管理端维护/发放）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PrizeController {

    private final PrizeService prizeService;
    private final PrizeMapper prizeMapper;

    private Long userId() {
        Long id = SecurityUtil.getCurrentUserId();
        if (id == null) {
            throw new com.elderly.common.BizException("请先登录");
        }
        return id;
    }

    // ---------------- 老人端 ----------------

    /** 可兑换奖品列表 */
    @GetMapping("/prize/list")
    public Result<List<Prize>> list() {
        return Result.success(prizeService.onSaleList());
    }

    /** 兑换奖品 */
    @PostMapping("/prize/exchange/{id}")
    public Result<PrizeExchange> exchange(@PathVariable Long id) {
        return Result.success(prizeService.exchange(userId(), id));
    }

    /** 我的兑换记录 */
    @GetMapping("/prize/my")
    public Result<List<PrizeExchange>> myExchanges() {
        return Result.success(prizeService.myExchanges(userId()));
    }

    // ---------------- 管理端：奖品 CRUD ----------------

    @GetMapping("/prize/admin/list")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<List<Prize>> adminList() {
        return Result.success(prizeMapper.selectList(new QueryWrapper<Prize>().orderByAsc("points_cost")));
    }

    @PostMapping("/prize/admin/save")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> save(@Valid @RequestBody SavePrizeDTO dto) {
        Prize prize = dto.toEntity();
        if (prize.getId() == null) {
            prizeMapper.insert(prize);
        } else {
            prizeMapper.updateById(prize);
        }
        return Result.success();
    }

    @DeleteMapping("/prize/admin/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable Long id) {
        prizeMapper.deleteById(id);
        return Result.success();
    }

    /** 兑换明细分页 */
    @GetMapping("/exchange/page")
    @PreAuthorize("hasAnyRole('ADMIN','COURSE_MANAGER')")
    public Result<Page<PrizeExchange>> exchangePage(@RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String status) {
        return Result.success(prizeService.adminPage(page, size, status));
    }

    /** 发放 / 取消兑换 */
    @PostMapping("/exchange/handle")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> handle(@Valid @RequestBody HandleExchangeDTO body) {
        prizeService.handle(body.id(), body.action(), SecurityUtil.getCurrentUserId());
        return Result.success();
    }
}
