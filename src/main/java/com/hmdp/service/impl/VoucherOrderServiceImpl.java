package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private RedissonClient redissonClient;

    private BlockingDeque<VoucherOrder> orderTasks = (BlockingDeque<VoucherOrder>) new ArrayBlockingQueue<VoucherOrder>(1024 * 1024);  //阻塞队列
    private static ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(()->{

            while(true){
                try{
                    // 1.从消息队列中拿消息  XREADGROUP GROUP g1 c1  COUNT 1 BLOCK 2000 STREAMS streams.order >   //g1组的c1消费者
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    if(list == null || list.isEmpty()){
                        // 没有消息，继续下一次循环
                        continue;
                    }
                    // 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();  // 键值对的值
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    // 获取消息成功，下单
                    handleVouncherOrder(voucherOrder);
                    //ack确认
                    stringRedisTemplate.opsForStream().acknowledge("streams.order","g1",record.getId());
                }catch (Exception e){
                    log.error("处理订单异常",e);
                    // 从pendinglist 里取消息，重新执行
                    handlePendingList();
                }
            }

        });
    }

    // 阻塞队列方案的线程执行方法
//    private class VoucherOrderHandle implements Runnable{
//        @Override
//        public void run() {
//            while(true){
//                try {
//                    // 1. 获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    // 2. 创建订单
//                    handleVouncherOrder(voucherOrder);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    log.error("处理订单异常",e);
//                }
//
//            }
//        }
//    }

    private void handlePendingList(){
        while(true){
            try{
                // 1.从pending-list中拿消息  XREADGROUP GROUP g1 c1  COUNT 1 BLOCK 2000 STREAMS streams.order 0   //g1组的c1消费者
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );
                if(list == null || list.isEmpty()){
                    // 没有读到，说明pending-list里没有异常消息 结束循环
                    break;
                }
                // 解析消息
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> value = record.getValue();  // 键值对的值
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                // 获取消息成功，下单
                handleVouncherOrder(voucherOrder);
                //ack确认
                stringRedisTemplate.opsForStream().acknowledge("streams.order","g1",record.getId());
            }catch (Exception e){
                log.error("处理pending-list订单异常",e);
            }
        }
    }

    private void handleVouncherOrder(VoucherOrder voucherOrder) throws InterruptedException {
        // 1. 获取用户（因为是从线程池中新开的线程，不是主线程，从UserHolder取不到用户信息，所以只能从voucherOrder中取）
        Long userId = voucherOrder.getUserId();
        // 2. 创建锁对象 (其实不加锁也可以，因为redis做了并发的避免，但此处加只是做个兜底（虽然这种可能性几乎没有）)
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if(!isLock){
            log.error("不允许重复下单");
            return;
        }
        //TODO 写入到数据库的一些操作(即 createVoucherOrder 但要注意的是如果在此处获取代理对象会失效，因为获取代理对象的底层用的ThreadLocal，而我们此时是在线程池里的新线程执行的：解决方法：1.将代理对象传参进来 2.将代理对象变成类的成员变量)
    }

    //秒杀lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("./lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //基于redis完成秒杀资格判断（秒杀优化）+Stream消息队列
    @Override
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


        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nexId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.EMPTY_LIST,
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        int resultValue = result.intValue();
        // 2.判断结果是否为0  0为有购买资格
        if(resultValue != 0){
            return Result.fail(resultValue==1?"库存不足":"不能重复下单");
        }
        // 3. 保存到数据库的信息通过消息队列放到线程池中执行

        // 4. 返回订单ID
        return Result.ok(orderId);
    }



//    @Override
//    public Result seckKillVoucher(Long voucherId) {
//        // 1. 查寻优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if(voucher == null){
//            return Result.fail("优惠券信息错误!");
//        }
//        // 2. 判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始!");
//        }
//        // 3. 判断秒杀是否结束
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束!");
//        }
//        // 4. 判断库存是否充足
//        if (voucher.getStock()<1) {
//            return Result.fail("库存不足!");
//        }
//
//        Long userID = UserHolder.getUser().getId();
//        //**************************************** 单机模式下的锁机制 ********************************************************************
//
////        synchronized(userID.toString().intern()) {
////            /*
////            Q4: 为什么不能直接return createVoucherOrder(voucherId)？会出现什么问题
////            A: 因为这个createVoucherOrder使用了@Transactional通过AOP实现事务的控制，
////               而直接 return createVoucherOrder(voucherId) 本质其实是 return this.createVoucherOrder(voucherId)
////               是直接调用的方法，而不是Spring管理的对象
////               Spring AOP是通过动态代理对象实现的，而直接那样相当于直接调用原本方法，！！没有实现事务的操作！！
////
////               所以要通过 AopContext.currentProxy(); 方法获取当前对象的代理对象，然后调用代理方法，才能实现事务控制
////
////               除此之外，还要有额外两步骤：
////                 1.  maven中导入  aspectjweaver 包
////                 <!--        代理的模式-->
////                <dependency>
////                    <groupId>org.aspectj</groupId>
////                    <artifactId>aspectjweaver</artifactId>
////                </dependency>
////
////                2. 在启动类中加入注解 @EnableAspectJAutoProxy(exposeProxy = true)   //代理对象可以暴露（默认为false）
////             */
////            // 获取代理对象(事务)
////            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        //************************************************************************************************************
//
//        //**************************************** 集群模式下使用分布式锁 ********************************************************************
//
//        //创建锁对象
//
//        //自己实现的分布式锁
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userID, stringRedisTemplate);
////        boolean isLock = lock.tryLock(1200);
//
//        //已有框架redisson提供的锁实现
//        RLock lock = redissonClient.getLock("lock:order:" + userID);   //可重入锁
//        boolean isLock = lock.tryLock();
//        //判断锁是否获取成功
//        if(!isLock){
//            // 获取锁失败,返回错误或重试
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//        //************************************************************************************************************
//    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){   //synchronized（悲观锁）在方法上锁的对象是this  ->  所有用户加的是同一把锁  ->  应该以当前用户id加锁，这样不同用户在一人一单查询时各自查各自的
        // 5. 一人一单   (   一人一单功能   )
        Long userID = UserHolder.getUser().getId();

        /*
         Q1: 为什么锁要toString()
         A: 每一次请求userID都是一个全新的对象，对象变了，锁就变了,而我们是希望值一样的用一把锁，所以用了toString()

         Q2: toString()就能保证以值来加锁么？  不行❌
         A: toString 底层是new了一个String，所以只调用toString()其实每次返回的还是新的对象  =>  所以还要调用intern()方法
            intern()方法：返回字符串对象的规范表示 就是初始化了一个为空的String Pool，equals为true 就返回池中对象，否则生成新的String对象，加入池中并返回

            It follows that for any two strings s and t, s.intern() == t.intern() is true if and only if s.equals(t) is true.

         锁的范围变小（同一用户用一把锁），系统性能提升

         Q3: 为什么不能在这个方法里用synchronized
         A： 因为锁在括号结束后就释放了，而事务要在整个函数执行完才提交（此时事务的级别是 可重复读），所以在 放锁 提交事务 之间如果该用户再次进行一人一单查询，会因为事务未提交而显示未有订单 => 并发问题
            所以要将synchronized 放在上面
         */
//        synchronized(userID.toString().intern()){
            // 5.1 查询订单
            int count = query().eq("user_id", userID).eq("voucher_id", voucherId).count();
            // 5.2 判断是否存在
            if(count > 0) {
                //用户已经买过
                return Result.fail("用户已经购买过一次！");
            }

            // 6. 扣减库存  (   超卖问题   )
            boolean success = seckillVoucherService.update()   //其实也用了sql语句update的行锁机制，数据库层面加了锁
                    .setSql("stock = stock-1")
                    .eq("voucher_id", voucherId).gt("stock",0)   // where id = ? and stock > 0  乐观锁(不加锁，在数据更新时判断是否有其他线程在修改)(CAS实现)
                    .update();
            if(!success){
                return Result.fail("库存不足!");
            }

            // 7. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long orderID = redisIdWorker.nexId("order");
            voucherOrder.setId(orderID);
            voucherOrder.setUserId(userID);
            voucherOrder.setVoucherId(voucherId);
            // 8. 返回订单id
            save(voucherOrder);
            return Result.ok(orderID);
//        }
    }
}
