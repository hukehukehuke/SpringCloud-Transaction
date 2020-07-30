package com.imooc.example.order.dao;

import com.imooc.example.order.domain.PayInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayInfoRepository extends JpaRepository<PayInfo,Long> {

    PayInfo findOneByOrderId(Long orderId);
}
