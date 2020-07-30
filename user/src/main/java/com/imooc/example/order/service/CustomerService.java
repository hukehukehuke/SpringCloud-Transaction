package com.imooc.example.order.service;

import com.imooc.example.dto.OrderDTO;
import com.imooc.example.order.dao.CustomerRepository;
import com.imooc.example.order.dao.PayInfoRepository;
import com.imooc.example.order.domain.Customer;
import com.imooc.example.order.domain.PayInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
    private final  static Logger LOG = LoggerFactory.getLogger(CustomerService.class);
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private JmsTemplate jmsTemplate;
    @Autowired
    private PayInfoRepository payInfoRepository;

    @Transactional(rollbackFor = Exception.class)
    @JmsListener(destination = "order:pay",containerFactory = "msgFactory")
    public void handlePay(OrderDTO orderDTO){
        LOG.info("ddd"+orderDTO);
        Customer customer = new Customer();
        PayInfo payInfo = payInfoRepository.findOneByOrderId(orderDTO.getId());
        if(payInfo != null){
            LOG.info("ddd"+orderDTO);
            orderDTO.setStatu("没有足够的钱");
            jmsTemplate.convertAndSend("order:unlock"+orderDTO);
            return;
        }else{
            if(customer.getDeposit() < orderDTO.getAmount()){  //钱不够
                return;
            }
            payInfo = new PayInfo();
            payInfo.setId(22L);
            payInfo.setOrderId(orderDTO.getId());
            payInfoRepository.save(payInfo);
            //customer.setDeposit(customer.getDeposit() - orderDTO.getAmount());
            customerRepository.charge(customer.getId(),orderDTO.getAmount());
        }


        orderDTO.setStatu("PAID");
        jmsTemplate.convertAndSend("order:tikets"+ orderDTO);
    }

}
