package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @ClassName: CacheClient
 * @Description: 封装的缓存工具类
 * @author: zjh
 * @date: 2023/2/17  19:53
 * @Version: 1.0
 */

@Slf4j
@Component
public class CacheClient {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit unit){
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(unit.toSeconds(time)),value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
    *@Description: 查询+解决缓存穿透 =》 泛型+函数式编程
    *@Param: [keyPrefix 前缀, id, type 泛型的类型, time 有效期, unit, dbFallback 函数式编程，根据id查询数据库的方法]
    *@return: R
    */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit unit, Function<ID,R> dbFallback){
        String key = keyPrefix + id;
        // 1. 从redis中查询商铺缓存  （以json形式存储）
        String cacheJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(cacheJson)){
            // 3. 存在，直接返回
            return JSONUtil.toBean(cacheJson, type);
        }
        // 判断命中是否是空值
        if(cacheJson != null){
            //说明是缓存空值"" 返回错误 穿透结果
            return null;
        }
        // 4. 不存在，根据id查询数据库
        R r = dbFallback.apply(id);
        // 5. 不存在，返回错误
        if(r == null){
            // 将空值写入redis （避免缓存击穿问题）
            stringRedisTemplate.opsForValue().set(key,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 6. 存在，写入redis
        set(key, r, time, unit);
        // 7. 返回数据
        return r;
    }

    /**
    *@Description: 查询+解决缓存击穿 => 逻辑过期
    *@Param: [keyPrefix, id, type, time, unit, dbFallback]
    *@return: R
    */
    public <R,ID> R queryWithLogicExpire(String keyPrefix, ID id, Class<R> type, Long time, TimeUnit unit, Function<ID,R> dbFallback) {
        String key = keyPrefix + id;
        // 1. 从redis中查询
        String cacheJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isBlank(cacheJson)) {
            //未命中 返回空
            return null;
        }
        // 3. 存在，反序列化为对象
        RedisData redisData = JSONUtil.toBean(cacheJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. 判断是否过期
        if(!expireTime.isAfter(LocalDateTime.now())){
            // 5.1 未过期 返回
            return r;
        }
        // 5.2 过期，需要缓存重建
        // 6. 缓存重建
        // 6.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁成功
        // 6.3 成功 开启线程执行重建过程
        if(isLock){
            //可以在次再次检测redis缓存是否过期，doublecheck
            CACHE_REBUILD_EXXECUTOR.submit(()->{
                // 查询数据库
                R curR = dbFallback.apply(id);
                //写入redis
                setWithLogicExpire(key,curR,time,unit);
                //释放锁
                unLock(lockKey);
            });
        }
        // 6.4 返回过期商品信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);   //转成基本类型返回，如果直接返回，拆箱的时候可能会造成空指针
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
