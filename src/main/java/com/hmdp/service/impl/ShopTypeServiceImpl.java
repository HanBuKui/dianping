package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getTypeList() {
        String key = RedisConstants.CACHE_SHOPTYPE_KEY;

        String cacheShopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(cacheShopTypeJson)){
            return Result.ok(JSONUtil.toList(cacheShopTypeJson,ShopType.class));
        }
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if(shopTypes == null){
            return Result.fail("店铺类型数据出错！");
        }
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopTypes));
        return Result.ok(shopTypes);
    }
}
