package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
    *@Description: 优惠券抢购
    *@Param: [voucherId]
    *@return: com.hmdp.dto.Result
    */
    Result seckKillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
