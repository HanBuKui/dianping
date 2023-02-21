package com.hmdp.utils;

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

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        long threadId = Thread.currentThread().getId();   //存到redis的value可以随便设置，但最好还是有意义一点的数
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, String.valueOf(threadId), timeoutSec, TimeUnit.SECONDS);
        //注意自动拆箱时success为空指针的可能性
        return Boolean.TRUE.equals(success);   //也是hutool的 BooleanUtil.isTrue() 的实现方法
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
