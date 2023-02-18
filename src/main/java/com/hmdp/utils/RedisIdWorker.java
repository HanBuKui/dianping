package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @ClassName: RedisIdWorker
 * @Description: 利用redis生成全局唯一ID
 * @author: zjh
 * @date: 2023/2/18  20:01
 * @Version: 1.0
 */

@Component
public class RedisIdWorker {

    /**
    *@Description: 开始的时间戳
    */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
    *@Description: 序列号位数
    */
    private static final int COUNT_BITS = 32;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
    *@Description:
     * 64 位的long
     * 1位符号位   31位时间戳（单位为s，可以用69年）   32位序列号
    *@Param: [keyPrefix 业务的前缀]
    *@return: long
    */
    public long nexId(String keyPrefix){
        // 1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;

        // 2. 生成序列号(利用redis自增长)
        // 2.1 获取当前日期，精确到天(一天一个key)
        //   避免超过32位上限（一天一个自增） + 方便统计
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3. 拼接并返回
        return timestamp << COUNT_BITS | count;  //+也可以，但位运算比加减快多了
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);  //转化为秒
        System.out.println(second);
    }
}
