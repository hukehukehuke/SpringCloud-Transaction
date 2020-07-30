package com.immo.example.ticket.domain.dao;

import com.immo.example.ticket.domain.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Created by mavlarn on 2018/1/20.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Ticket findOneByTitle(String title);

    List<Ticket> getAll();

    Ticket findOneByTicketNumAndLockUser(Long ticketNum, Long ticketNum1);

    @Override
    @Modifying(clearAutomatically = true)
    Ticket save(Ticket ticket);


    /**
     * 通过lockUser SQL语句的方式锁票
     * @param customerId
     * @param ticketNums
     * @return
     */
    @Modifying
    @Query(value = "update ticket set lockUser = ?1 where lockUser is null and ticketsNum = ?2")
    int lockTicket(Long customerId,Long ticketNums);


    @Modifying
    @Query(value = "update ticket set ower =?1,lockUser = null where lockUser = ?1 and ticketsNum = ?2")
    int moveTicket(Long customerId,Long ticketNums);


    @Modifying
    @Query(value = "update ticket set lockUser = null where lockUser = ?1 and ticketsNum = ?2")
    int ticketUnlock(Long customerId,Long ticketNums);

    @Modifying
    @Query(value = "update ticket set ower = null where ower = ?1 and ticketsNum = ?2")
    int unMoveTicket(Long customerId,Long ticketNums);
}
