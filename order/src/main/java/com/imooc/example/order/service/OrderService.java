package com.imooc.example.order.service;

import com.imooc.example.dto.OrderDTO;
import com.imooc.example.order.dao.OrderRepository;
import com.imooc.example.order.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Order creteOrder() {
        Order order = new Order();
        order.setStatus("status");
        return order;
    }
}
