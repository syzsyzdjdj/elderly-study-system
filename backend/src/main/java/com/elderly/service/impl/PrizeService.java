package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.elderly.common.BizException;
import com.elderly.entity.PointRecord;
import com.elderly.entity.Prize;
import com.elderly.entity.PrizeExchange;
import com.elderly.entity.SysUser;
import com.elderly.entity.UserMessage;
import com.elderly.mapper.PointRecordMapper;
import com.elderly.mapper.PrizeExchangeMapper;
import com.elderly.mapper.PrizeMapper;
import com.elderly.mapper.SysUserMapper;
import com.elderly.mapper.UserMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 福利奖品与积分兑换服务
 */
@Service
@RequiredArgsConstructor
public class PrizeService {

    private final PrizeMapper prizeMapper;
    private final PrizeExchangeMapper exchangeMapper;
    private final SysUserMapper sysUserMapper;
    private final PointRecordMapper pointRecordMapper;
    private final UserMessageMapper userMessageMapper;

    /** 移动端：上架奖品 */
    public List<Prize> onSaleList() {
        return prizeMapper.selectList(new QueryWrapper<Prize>()
                .eq("status", 1).orderByAsc("points_cost"));
    }

    /**
     * 积分兑换：原子扣分 + 库存校验扣减 + 明细 + 消息通知
     */
    @Transactional
    public PrizeExchange exchange(Long userId, Long prizeId) {
        Prize prize = prizeMapper.selectById(prizeId);
        if (prize == null || !Integer.valueOf(1).equals(prize.getStatus())) {
            throw new BizException("奖品不存在或已下架");
        }
        if (prize.getStock() == null || prize.getStock() <= 0) {
            throw new BizException("该奖品已被兑换完啦");
        }
        if (prize.getPointsCost() == null || prize.getPointsCost() < 0) {
            throw new BizException("奖品积分配置有误，请联系管理员");
        }
        int cost = prize.getPointsCost();
        // 原子扣分：余额不足返回 0
        int rows = sysUserMapper.deductPoints(userId, cost);
        if (rows == 0) {
            throw new BizException("积分不足，再去学习赚一点吧");
        }
        // 乐观扣库存（仅在仍有库存时成功）
        int stockRows = prizeMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Prize>()
                        .setSql("stock = stock - 1")
                        .eq("id", prizeId).gt("stock", 0));
        if (stockRows == 0) {
            // 抛出异常后整个事务回滚，包括此前的扣分。
            throw new BizException("手慢了，该奖品已被兑换完");
        }

        PrizeExchange exchange = new PrizeExchange();
        exchange.setUserId(userId);
        exchange.setPrizeId(prizeId);
        exchange.setPrizeName(prize.getName());
        exchange.setPointsCost(cost);
        exchange.setStatus("PENDING");
        exchange.setCreateTime(LocalDateTime.now());
        exchangeMapper.insert(exchange);

        // 积分明细（负向）
        SysUser user = sysUserMapper.selectById(userId);
        PointRecord record = new PointRecord();
        record.setUserId(userId);
        record.setChangeAmount(-cost);
        record.setBalance(user == null || user.getPoints() == null ? 0 : user.getPoints());
        record.setChangeType("EXCHANGE");
        record.setRemark("兑换奖品：" + prize.getName());
        record.setRefId(exchange.getId());
        record.setCreateTime(LocalDateTime.now());
        pointRecordMapper.insert(record);

        // 消息通知
        UserMessage message = new UserMessage();
        message.setUserId(userId);
        message.setTitle("奖品兑换成功");
        message.setContent("您已使用" + cost + "积分兑换【" + prize.getName()
                + "】，请留意社区通知，凭账号到社区服务点领取。");
        message.setMsgType("EXCHANGE");
        message.setBizId(exchange.getId());
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.now());
        userMessageMapper.insert(message);

        return exchange;
    }

    /** 我的兑换记录 */
    public List<PrizeExchange> myExchanges(Long userId) {
        return exchangeMapper.selectList(new QueryWrapper<PrizeExchange>()
                .eq("user_id", userId).orderByDesc("id"));
    }

    /** 管理端：兑换明细分页（带用户昵称） */
    public Page<PrizeExchange> adminPage(long page, long size, String status) {
        QueryWrapper<PrizeExchange> wrapper = new QueryWrapper<PrizeExchange>().orderByDesc("id");
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        Page<PrizeExchange> result = exchangeMapper.selectPage(new Page<>(page, size), wrapper);
        // 批量填充昵称
        List<Long> userIds = result.getRecords().stream().map(PrizeExchange::getUserId).distinct().toList();
        if (!userIds.isEmpty()) {
            sysUserMapper.selectBatchIds(userIds).forEach(u ->
                    result.getRecords().stream()
                            .filter(e -> e.getUserId().equals(u.getId()))
                            .forEach(e -> e.setUserName(u.getNickname())));
        }
        return result;
    }

    /**
     * 管理端发放/取消兑换。取消时退回积分与库存。
     */
    @Transactional
    public void handle(Long exchangeId, String action, Long handlerId) {
        if (exchangeId == null || handlerId == null) throw new BizException("处理参数不完整");
        if (!"DONE".equals(action) && !"CANCEL".equals(action)) {
            throw new BizException("不支持的操作");
        }
        PrizeExchange exchange = exchangeMapper.selectById(exchangeId);
        if (exchange == null) {
            throw new BizException("兑换记录不存在");
        }
        if (exchange.getPointsCost() == null || exchange.getPointsCost() < 0) {
            throw new BizException("兑换积分记录有误，请联系管理员");
        }
        // 条件更新抢占处理权；退款与状态迁移同事务提交，失败时一起回滚。
        if (exchangeMapper.transitionPending(exchangeId, action, handlerId, LocalDateTime.now()) != 1) {
            throw new BizException("该记录已处理，请勿重复操作");
        }
        if ("CANCEL".equals(action)) {
            // 退积分、还库存
            if (sysUserMapper.addPoints(exchange.getUserId(), exchange.getPointsCost()) != 1) {
                throw new BizException("兑换用户不存在，无法退款");
            }
            prizeMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Prize>()
                            .setSql("stock = stock + 1")
                            .eq("id", exchange.getPrizeId()));

            PointRecord record = new PointRecord();
            record.setUserId(exchange.getUserId());
            record.setChangeAmount(exchange.getPointsCost());
            SysUser user = sysUserMapper.selectById(exchange.getUserId());
            record.setBalance(user == null || user.getPoints() == null ? 0 : user.getPoints());
            record.setChangeType("ADMIN");
            record.setRemark("兑换取消退回：" + exchange.getPrizeName());
            record.setRefId(exchange.getId());
            record.setCreateTime(LocalDateTime.now());
            pointRecordMapper.insert(record);
        }
    }
}
