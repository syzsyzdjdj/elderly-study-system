package com.elderly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderly.entity.PrizeExchange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface PrizeExchangeMapper extends BaseMapper<PrizeExchange> {
    @Update("UPDATE prize_exchange SET status=#{status},handler_id=#{handlerId},handle_time=#{handledAt} "
            + "WHERE id=#{id} AND status='PENDING'")
    int transitionPending(@Param("id") Long id, @Param("status") String status,
                          @Param("handlerId") Long handlerId, @Param("handledAt") LocalDateTime handledAt);
}
