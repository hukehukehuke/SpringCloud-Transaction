package com.imooc.example.order.domain;

import org.hibernate.annotations.GenericGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

@Entity(name = "pay_info")
public class PayInfo {
    @Id
    @GeneratedValue
    private Long id;

    private String status;
    @Column(name = "order_id")
    private Long orderId;

    private Integer ammout;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Integer getNum() {
        return ammout;
    }

    public void setNum(Integer num) {
        this.ammout = num;
    }
}
