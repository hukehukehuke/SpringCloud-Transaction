package com.immo.example.ticket.domain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;

@SpringBootApplication
@EnableEurekaClient   //user服务客户端
@EnableHystrix   //启动Hystrix监听
@EnableFeignClients
public class TicketsApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketsApplication.class, args);
	}
}
