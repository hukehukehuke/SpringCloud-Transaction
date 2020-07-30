package com.immo.example.ticket.domain.web;

import com.immo.example.ticket.domain.dao.TicketRepository;
import com.immo.example.ticket.domain.domain.Ticket;
import com.immo.example.ticket.domain.service.TicketsService;
import com.imooc.example.dto.OrderDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Created by mavlarn on 2018/1/20.
 */
@RestController
@RequestMapping("/api/Ticket")
public class TicketResource{

    @Autowired
    private TicketRepository ticketRepository;
    @Autowired
    private TicketsService ticketsService;
    @Autowired
    private JmsTemplate jmsTemplate;

    @PostMapping(value = "create")
    public void create(OrderDTO orderDTO){
        jmsTemplate.convertAndSend("order:new"+orderDTO);
    }

    @GetMapping("")
    public List<Ticket> getAll(){
        return this.ticketRepository.getAll();
    }


    @PostMapping("lock")
    public Ticket lock(OrderDTO orderDTO){
        return ticketsService.ticketLock(orderDTO);
    }
}
