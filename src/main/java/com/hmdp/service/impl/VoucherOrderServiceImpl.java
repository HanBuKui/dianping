package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckKillVoucher(Long voucherId) {
        // 1. 查寻优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null){
            return Result.fail("优惠券信息错误!");
        }
        // 2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        // 3. 判断秒杀是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束!");
        }
        // 4. 判断库存是否充足
        if (voucher.getStock()<1) {
            return Result.fail("库存不足!");
        }
        // 5. 扣减库存
        boolean success = seckillVoucherService.update()   //其实也用了sql语句update的行锁机制，数据库层面加了锁
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherId).gt("stock",0)   // where id = ? and stock > 0  乐观锁(CAS实现)
                .update();
        if(!success){
            //重试
            return Result.fail("库存不足!");
        }
        // 6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderID = redisIdWorker.nexId("order");
        voucherOrder.setId(orderID);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        // 7. 返回订单id
        save(voucherOrder);
        return Result.ok(orderID);
    }


}
