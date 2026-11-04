package com.elderly;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 老年数字素养学习系统 启动类
 */
@SpringBootApplication
@MapperScan("com.elderly.mapper")
@EnableScheduling
public class ElderlyStudyApplication {
    public static void main(String[] args) {
        SpringApplication.run(ElderlyStudyApplication.class, args);
    }
}
