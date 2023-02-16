package com.hmdp.service.impl;

import ch.qos.logback.core.pattern.color.RedCompositeConverter;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        //互斥锁解决缓存击穿
        return Result.ok(shop);
    }

    /**
    *@Description: 互斥锁解决缓存击穿
    *@Param: [id]
    *@return: com.hmdp.entity.Shop
    */
    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        boolean isWait = true;
        String lockKey = RedisConstants.LOCK_SHOP_KEY+id;
        Shop shop = null;
        try {
            while (isWait){
                // 1. 从redis中查询商铺缓存(命中)
                String cacheShopJson = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(cacheShopJson)){
                    shop = JSONUtil.toBean(cacheShopJson, Shop.class);
                    return shop;
                }

                //实现缓存重建
                //1. 获取互斥锁
                boolean isLock = tryLock(lockKey);
                //2.判断是否获取成功
                if(!isLock){
                    //2.1 失败，则休眠并重试
                    Thread.sleep(50);
                }else {
                    isWait = false;
                }
            }
            //2.2 成功
            //2.2.1 再次检查缓存，做doublecheck，防止是刚好前一个更新完缓存后放了锁，然后被你拿到了
            String cacheShopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(cacheShopJson)){
                shop = JSONUtil.toBean(cacheShopJson, Shop.class);
                unLock(lockKey);  //放锁
                return shop;
            }
            //2.2.2 根据id查询数据库
            shop = getById(id);
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //释放互斥锁
            unLock(lockKey);
        }

        //返回
        return shop;
    }

    private static final ExecutorService CACHE_REBUILD_EXXECUTOR = Executors.newFixedThreadPool(10);  //线程池

    /**
    *@Description: 逻辑过期解决缓存穿透问题
    *@Param: [id]
    *@return: com.hmdp.entity.Shop
    */
    public Shop queryWithLogicExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 从redis中查询
        String cacheShopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isBlank(cacheShopJson)) {
            //未命中 返回空
            return null;
        }
        // 3. 存在，反序列化为对象
        RedisData redisData = JSONUtil.toBean(cacheShopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. 判断是否过期
       if(!expireTime.isAfter(LocalDateTime.now())){
           // 5.1 未过期 返回
           return shop;
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
               // 重建缓存
               this.saveShop2Redis(id,20L);
               //释放锁
               unLock(lockKey);
           });
        }
        // 6.4 返回过期商品信息
        return shop;
    }


    /**
    *@Description: 查询商铺信息 缓存穿透问题
    *@Param: [id]
    *@return: com.hmdp.entity.Shop
    */
    public Shop queryWithPassThrough(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 从redis中查询商铺缓存  （以json形式存储）
        String cacheShopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否存在
        if (StrUtil.isNotBlank(cacheShopJson)){
            // 3. 存在，直接返回
            Shop shop = JSONUtil.toBean(cacheShopJson, Shop.class);
            return shop;
        }
        // 判断命中是否是空值
        if(cacheShopJson != null){
            //说明是缓存空值"" 返回错误 穿透结果
            return null;
        }

        // 4. 不存在，根据id查询数据库
        Shop shop = getById(id);
        // 5. 不存在，返回错误
        if(shop == null){
            // 将空值写入redis （避免缓存击穿问题）
            stringRedisTemplate.opsForValue().set(key,"", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 6. 存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7. 返回数据
        return shop;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);   //转成基本类型返回，如果直接返回，拆箱的时候可能会造成空指针
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
    *@Description: 活动热点数据缓存预热
    *@Param: [id, expirSeconds]
    *@return: void
    */
    public void saveShop2Redis(Long id, Long expirSeconds){
        String key = RedisConstants.CACHE_SHOP_KEY+id;
        Shop shop = getById(id);
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(expirSeconds), shop);
        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key,jsonStr);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空！");
        }
        //1. 更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY+id);
        return Result.ok();
    }
}
