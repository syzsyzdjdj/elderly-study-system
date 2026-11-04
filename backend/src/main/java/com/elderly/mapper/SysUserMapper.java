package com.elderly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.elderly.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

/**
 * 系统用户 Mapper
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 锁持有到事务提交，串行化同一用户的积分业务。 */
    @Select("SELECT id FROM sys_user WHERE id = #{userId} FOR UPDATE")
    Long lockPoints(@Param("userId") Long userId);

    /** 原子加分（避免并发下读-改-写丢失更新） */
    @Update("UPDATE sys_user SET points = points + #{points} WHERE id = #{userId}")
    int addPoints(@Param("userId") Long userId, @Param("points") int points);

    /** 原子扣分（余额不足时返回 0，用于积分兑换防超支） */
    @Update("UPDATE sys_user SET points = points - #{points} WHERE id = #{userId} AND points >= #{points}")
    int deductPoints(@Param("userId") Long userId, @Param("points") int points);
}
