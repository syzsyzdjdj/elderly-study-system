package com.elderly.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.elderly.common.BizException;
import com.elderly.dto.SavePrizeDTO;
import com.elderly.entity.*;
import com.elderly.mapper.*;
import jakarta.validation.Validation;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

class PrizeServiceTest {
    JdbcTemplate jdbc;
    TransactionTemplate tx;
    SqlSessionTemplate session;
    PrizeExchangeMapper exchanges;
    SysUserMapper users;
    PrizeService service;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:prize-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE prize_exchange(id BIGINT PRIMARY KEY,user_id BIGINT,prize_id BIGINT,"
                + "prize_name VARCHAR(100),points_cost INT,status VARCHAR(20),create_time TIMESTAMP,handle_time TIMESTAMP,handler_id BIGINT)");
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,points INT)");
        jdbc.execute("CREATE TABLE prize(id BIGINT PRIMARY KEY,stock INT)");
        jdbc.execute("CREATE TABLE point_record(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,change_amount INT,"
                + "balance INT,change_type VARCHAR(20),remark VARCHAR(255),ref_id BIGINT,create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE point_rule(id BIGINT PRIMARY KEY,rule_code VARCHAR(20),rule_name VARCHAR(100),points INT,daily_limit INT,enabled INT,remark VARCHAR(255))");
        jdbc.update("INSERT INTO point_rule VALUES(1,'QA','问答',5,1,1,'')");
        jdbc.update("INSERT INTO sys_user VALUES(1,90)");
        jdbc.update("INSERT INTO prize VALUES(1,0)");
        jdbc.update("INSERT INTO prize_exchange(id,user_id,prize_id,prize_name,points_cost,status) VALUES(1,1,1,'奖品',10,'PENDING')");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), ds));
        for (Class<?> mapper : new Class<?>[]{PrizeExchangeMapper.class, SysUserMapper.class, PrizeMapper.class, PointRecordMapper.class, PointRuleMapper.class}) {
            config.addMapper(mapper);
        }
        session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        exchanges = session.getMapper(PrizeExchangeMapper.class);
        users = mock(SysUserMapper.class, delegatesTo(session.getMapper(SysUserMapper.class)));
        doAnswer(call -> {
            SysUser user = new SysUser(); user.setId(1L);
            user.setPoints(jdbc.queryForObject("SELECT points FROM sys_user WHERE id=1", Integer.class));
            return user;
        }).when(users).selectById(anyLong());
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        service = make(exchanges, session.getMapper(PointRecordMapper.class));
    }

    PrizeService make(PrizeExchangeMapper mapper, PointRecordMapper records) {
        return new PrizeService(session.getMapper(PrizeMapper.class), mapper, users, records, mock(UserMessageMapper.class));
    }

    void race(String first, String second) throws Exception {
        var barrier = new CyclicBarrier(2);
        var raced = mock(PrizeExchangeMapper.class, delegatesTo(exchanges));
        doAnswer(call -> {
            PrizeExchange result = exchanges.selectById(1L);
            barrier.await(5, TimeUnit.SECONDS); // Both transactions observe PENDING before either updates.
            return result;
        }).when(raced).selectById(anyLong());
        PrizeService concurrent = make(raced, session.getMapper(PointRecordMapper.class));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> a = () -> attempt(concurrent, first);
            Callable<Boolean> b = () -> attempt(concurrent, second);
            Future<Boolean> one = pool.submit(a), two = pool.submit(b);
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        String status = jdbc.queryForObject("SELECT status FROM prize_exchange WHERE id=1", String.class);
        boolean cancelled = status.equals("CANCEL");
        assertEquals(cancelled ? 100 : 90, jdbc.queryForObject("SELECT points FROM sys_user WHERE id=1", Integer.class));
        assertEquals(cancelled ? 1 : 0, jdbc.queryForObject("SELECT stock FROM prize WHERE id=1", Integer.class));
        assertEquals(cancelled ? 1 : 0, jdbc.queryForObject("SELECT COUNT(*) FROM point_record", Integer.class));
    }

    boolean attempt(PrizeService target, String action) {
        try {
            tx.executeWithoutResult(ignored -> target.handle(1L, action, 2L));
            return true;
        } catch (BizException alreadyHandled) { return false; }
    }

    @Test void concurrentCancellationsRefundOnce() throws Exception { race("CANCEL", "CANCEL"); }
    @Test void completionAndCancellationCannotBothSucceed() throws Exception { race("DONE", "CANCEL"); }
    @Test void concurrentCompletionsHaveOneWinner() throws Exception { race("DONE", "DONE"); }

    @Test void separatePointServicesRespectDailyLimitUsingDatabaseLock() throws Exception {
        var first = new PointService(session.getMapper(PointRecordMapper.class), session.getMapper(PointRuleMapper.class), users);
        var second = new PointService(session.getMapper(PointRecordMapper.class), session.getMapper(PointRuleMapper.class), users);
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> one = pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return tx.execute(ignored -> first.awardByRule(1L, "QA", 1L));
            });
            Future<Integer> two = pool.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return tx.execute(ignored -> second.awardByRule(1L, "QA", 2L));
            });
            assertEquals(5, one.get(10, TimeUnit.SECONDS) + two.get(10, TimeUnit.SECONDS));
            assertEquals(95, jdbc.queryForObject("SELECT points FROM sys_user", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM point_record", Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void refundFailureRollsBackStateBalanceAndStock() {
        var records = mock(PointRecordMapper.class);
        when(records.insert(any(PointRecord.class))).thenThrow(new IllegalStateException("write failed"));
        var failing = make(exchanges, records);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(ignored -> failing.handle(1L, "CANCEL", 2L)));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM prize_exchange", String.class));
        assertEquals(90, jdbc.queryForObject("SELECT points FROM sys_user", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT stock FROM prize", Integer.class));
        assertTrue(attempt(service, "CANCEL"));
    }

    @Test void invalidPricesCannotReachDeductionAndDtoRejectsInvalidAdminInput() {
        var prizes = mock(PrizeMapper.class);
        var userMapper = mock(SysUserMapper.class);
        var prize = new Prize(); prize.setStatus(1); prize.setStock(1); prize.setPointsCost(-10);
        when(prizes.selectById(1L)).thenReturn(prize);
        var target = new PrizeService(prizes, exchanges, userMapper, mock(PointRecordMapper.class), mock(UserMessageMapper.class));
        assertThrows(BizException.class, () -> target.exchange(1L, 1L));
        verifyNoInteractions(userMapper);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new SavePrizeDTO(null, "奖品", null, -1, 0, null, 1)).isEmpty());
            assertFalse(validator.validate(new SavePrizeDTO(null, "奖品", null, 1, -1, null, 1)).isEmpty());
            assertFalse(validator.validate(new SavePrizeDTO(null, "奖品", null, 1, 1, null, 2)).isEmpty());
            assertTrue(validator.validate(new SavePrizeDTO(null, "奖品", null, 0, 1, null, 1)).isEmpty());
        }
    }
}
