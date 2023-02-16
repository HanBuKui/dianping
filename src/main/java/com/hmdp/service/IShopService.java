package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
    *@Description: 查询商铺信息（支持缓存）
    *@Param: [id]
    *@return: com.hmdp.dto.Result
    */
    Result queryById(Long id);

    /**
    *@Description: 更新商铺信息（1.更新数据库 2.删缓存）
    *@Param: [shop]
    *@return: com.hmdp.dto.Result
    */
    Result updateShop(Shop shop);
}
