package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @ClassName: SimpleRedisLock
 * @Description: 实现Redis分布式锁 版本1
 * @author: zjh
 * @date: 2023/2/21  22:06
 * @Version: 1.0
 */


public class SimpleRedisLock implements ILock{

    private StringRedisTemplate stringRedisTemplate;
    private String name;  //业务的名称（也就是锁的名称）

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";

    /**
     * UUID是指在一台机器上生成的数字，它保证对在同一时空中的所有机器都是唯一的
     * UUID由以下几部分的组合：
     * （1）当前日期和时间，UUID的第一个部分与时间有关，如果你在生成一个UUID之后，过几秒又生成一个UUID，则第一个部分不同，其余相同。
     * （2）时钟序列。
     * （3）全局唯一的IEEE机器识别号，如果有网卡，从网卡MAC地址获得，没有网卡以其他方式获得。
     * 通过组成可以看出，首先每台机器的mac地址是不一样的，那么如果出现重复，可能是同一时间下生成的id可能相同，不会存在不同时间内生成重复的数据
    */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";   //线程标识前缀(区分集群下不同主机(不同JVM) 而ThreadID在jvm内部是自增生成的)

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识  (UUID:区分不同JVM + 线程id：区分同一个JVM中的不同线程)
        String threadId = ID_PREFIX + Thread.currentThread().getId();  //解决redis分布式锁误删问题
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //注意自动拆箱时success为空指针的可能性
        return Boolean.TRUE.equals(success);   //也是hutool的 BooleanUtil.isTrue() 的实现方法
    }

    @Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁中标识并判断标识是否一致
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if(threadId.equals(id)){
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
