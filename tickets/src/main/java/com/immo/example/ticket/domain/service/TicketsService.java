package com.immo.example.ticket.domain.service;

import com.immo.example.ticket.domain.dao.TicketRepository;
import com.immo.example.ticket.domain.domain.Ticket;
import com.immo.example.ticket.domain.web.TicketResource;
import com.imooc.example.dto.OrderDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketsService {

    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private JmsTemplate jmsTemplate;

    private final static Logger LOG = LoggerFactory.getLogger(TicketsService.class);

    @JmsListener(destination = "order:new", containerFactory = "msgFactory")
    public void handleTicketInLock(OrderDTO orderDTO) {
        LOG.info("dddddd" + orderDTO);
        int lockCount = ticketRepository.lockTicket(orderDTO.getCustomerId(),orderDTO.getTicketNum());
        if(lockCount == 1){
            orderDTO.setStatu("Ticket Lock");
            jmsTemplate.convertAndSend("order:locked"+orderDTO);
        }else{

        }
    }

    @JmsListener(destination = "order:ticket_move", containerFactory = "msgFactory")
    public void handleTicketMove(OrderDTO orderDTO) {
        int lockCount = ticketRepository.lockTicket(orderDTO.getCustomerId(),orderDTO.getTicketNum());

        if(lockCount == 1){
            orderDTO.setStatu("Ticket Lock");
            jmsTemplate.convertAndSend("order:locked"+orderDTO);
        }else{

        }
    }
    @Transactional(rollbackFor = Exception.class)
    public Ticket ticketLock(OrderDTO orderDTO) {
        Ticket ticket = ticketRepository.findOneByTicketNumAndLockUser(orderDTO.getTicketNum(),orderDTO.getTicketNum());
        ticket.setLockUser(orderDTO.getCustomerId());
        ticket = ticketRepository.save(ticket);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return ticket;
    }


    @Transactional(rollbackFor = Exception.class)
    public int ticketLock2(OrderDTO orderDTO) {
        int lockCount = ticketRepository.lockTicket(orderDTO.getTicketNum(), orderDTO.getTicketNum());
        LOG.info("dd"+lockCount);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return lockCount;
    }
}
