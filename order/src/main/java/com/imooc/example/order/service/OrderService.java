package com.imooc.example.order.service;

import com.imooc.example.dto.OrderDTO;
import com.imooc.example.order.dao.OrderRepository;
import com.imooc.example.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Service
public class OrderService {

    private final static Logger LOG = LoggerFactory.getLogger(OrderService.class);
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private JmsTemplate jmsTemplate;

    @Transactional(rollbackFor = Exception.class)
    @JmsListener(destination = "order:locked", containerFactory = "msgFactory")
    public void handleOrder(OrderDTO orderDTO) {
        if (orderRepository.findOneByUuid(orderDTO.getUuid()) != null) {
            LOG.info("ddddd");
        } else {
            Order order = creteOrder();
            orderDTO.setId(order.getId());
            orderRepository.save(order);
        }
        orderDTO.setStatu("NEW");
        jmsTemplate.convertAndSend("order : pay" + orderDTO);

    }

    @Transactional(rollbackFor = Exception.class)
    @JmsListener(destination = "order:finish", containerFactory = "msgFactory")
    public void handleOrderFinish(OrderDTO orderDTO) {
        LOG.info("ddd" + orderDTO);
        Order one = orderRepository.findOne(orderDTO.getId());
        one.setStatus("FINISH");
        orderRepository.save(one);
    }

    /**
     * 1、一开始锁票失败
     * 2、扣费失败、解锁票，然后触发失败队列
     * 3、报错了定时任务检查
     *
     * @param orderDTO
     */
    @Transactional(rollbackFor = Exception.class)
    @JmsListener(destination = "order:fail", containerFactory = "msgFactory")
    public void handleOrderFail(OrderDTO orderDTO) {
        LOG.info("ddd" + orderDTO);
        Order order = null;
        if (orderDTO.getId() == null) {
            order.setStatus("FAIL");
            order.setReason("锁票失败");
        } else {
            order = orderRepository.findOne(orderDTO.getId());
            if (orderDTO.getStatu().equalsIgnoreCase("支付金额不足")) {
                order.setReason("支付金额不足");
            }
        }
        order.setStatus("FAIL");
        orderRepository.save(order);
    }


    public Order creteOrder() {
        Order order = new Order();
        order.setStatus("status");
        return order;
    }

    @Scheduled(fixedDelay = 10000)
    public void checkTimeoutOrders() {
        ZonedDateTime zonedDateTime = ZonedDateTime.now().minusMinutes(1L);
        List<Order> orderList = orderRepository.findOneByStatusAndCreateDateBefore("NEW", zonedDateTime);
        orderList.forEach( order -> {
            OrderDTO orderDTO = new OrderDTO();
            orderDTO.setStatu(order.getStatus());
            jmsTemplate.convertAndSend("order:fail"+orderDTO);
        });
    }
}
