package com.elderly.service.impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.elderly.mapper.*;
import com.elderly.util.ChatTtsUtil;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourseRegionVisibilityTest {
    SqlSession session;
    CourseServiceImpl service;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:course-region-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE course_info(id BIGINT PRIMARY KEY,category_id BIGINT,title VARCHAR(100),cover VARCHAR(255),content CLOB,video_url VARCHAR(255),intro VARCHAR(255),duration INT,points_reward INT,status INT,province VARCHAR(100),city VARCHAR(100),district VARCHAR(100),create_time TIMESTAMP,update_time TIMESTAMP)");
        // Three representations of nationwide, two province-wide, two different cities, one other province.
        Object[][] regions = {{null,null},{"",""},{"  "," "},{"广东省",null},{"广东省",""},{"广东省","广州市"},{"广东省","深圳市"},{"浙江省","杭州市"}};
        for (int i=0;i<regions.length;i++) jdbc.update("INSERT INTO course_info(id,title,status,province,city) VALUES(?,?,1,?,?)",i+1,"课程"+(i+1),regions[i][0],regions[i][1]);
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test",new JdbcTransactionFactory(),ds));
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(pagination);
        config.addMapper(CourseInfoMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        service = new CourseServiceImpl(mock(CourseCategoryMapper.class),session.getMapper(CourseInfoMapper.class),
                mock(StudyRecordMapper.class),mock(ChatTtsUtil.class),mock(FavoriteService.class));
    }
    @AfterEach void close() { service.shutdown(); session.close(); }

    @Test void nationalBlankAndProvinceWideCoursesRemainVisibleWithCityFilter() {
        var result = service.pageCourses(1,100,null,null,1,"广东省","广州市");
        assertEquals(List.of(6L,5L,4L,3L,2L,1L),result.getRecords().stream().map(c->c.getId()).toList());
        assertEquals(6,result.getTotal());
        var page = service.pageCourses(1,2,null,null,1,"广东省","广州市");
        assertEquals(6,page.getTotal());
        assertEquals(List.of(6L,5L),page.getRecords().stream().map(c->c.getId()).toList());
    }
    @Test void provinceFilterKeepsItsCitiesAndNoRegionFilterStillShowsEveryCourse() {
        var result = service.pageCourses(1,100,null,null,null," 广东省 ",null);
        assertEquals(7,result.getTotal());
        assertEquals(8,service.pageCourses(1,100,null,null,null,null,null).getTotal());
    }
}
