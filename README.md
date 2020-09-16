# SpringCloud-Transaction
Spring Boot +Spring Cloud +MQ+JTA订单和 用户分布式事务实现
1.什么是可靠消息最终一致性事务

​ 可靠消息最终一致性方案是指当事务发起方执行完成本地事务后并发出一条消息，事务参与方(消息消费者)一定能够接收消息并处理事务成功，此方案强调的是只要消息发给事务参与方最终事务要达到一致。

​ 此方案是利用消息中间件完成，如下图：

​ 事务发起方（消息生产方）将消息发给消息中间件，事务参与方从消息中间件接收消息，事务发起方和消息中间件之间，事务参与方（消息消费方）和消息中间件之间都是通过网络通信，由于网络通信的不确定性会导致分布式事务问题。



因此可靠消息最终一致性方案要解决以下几个问题：

1.1.本地事务与消息发送的原子性问题

​ 本地事务与消息发送的原子性问题即：事务发起方在本地事务执行成功后消息必须发出去，否则就丢弃消息。即实现本地事务和消息发送的原子性，要么都成功，要么都失败。本地事务与消息发送的原子性问题是实现可靠消息最终一致性方案的关键问题。

先来尝试下这种操作，先发送消息，再操作数据库：

begin transaction；
	//1.发送MQ
	//2.数据库操作
commit transation;

这种情况下无法保证数据库操作与发送消息的一致性，因为可能发送消息成功，数据库操作失败。

你立马想到第二种方案，先进行数据库操作，再发送消息：

begin transaction；
	//1.数据库操作
	//2.发送MQ
commit transation;

​ 这种情况下貌似没有问题，如果发送MQ消息失败，就会抛出异常，导致数据库事务回滚。但如果是超时异常，数据库回滚，但MQ其实已经正常发送了，同样会导致不一致。

1.2、事务参与方接收消息的可靠性

事务参与方必须能够从消息队列接收到消息，如果接收消息失败可以重复接收消息。

1.3、消息重复消费的问题

​ 由于网络2的存在，若某一个消费节点超时但是消费成功，此时消息中间件会重复投递此消息，就导致了消息的重复消费。

​ 要解决消息重复消费的问题就要实现事务参与方的方法幂等性。
2.解决方案

​ 上节讨论了可靠消息最终一致性事务方案需要解决的问题，本节讨论具体的解决方案。
2.1.本地消息表方案

​ 本地消息表这个方案最初是eBay提出的，此方案的核心是通过本地事务保证数据业务操作和消息的一致性，然后通过定时任务将消息发送至消息中间件，待确认消息发送给消费方成功再将消息删除。

下面以注册送积分为例来说明：

下例共有两个微服务交互，用户服务和积分服务，用户服务负责添加用户，积分服务负责增加积分。



交互流程如下：

1) 用户注册

​ 用户服务在本地事务新增用户和增加 ”积分消息日志“。（用户表和消息表通过本地事务保证一致）

下边是伪代码

begin transaction；
	//1.新增用户
	//2.存储积分消息日志
commit transation;

这种情况下，本地数据库操作与存储积分消息日志处于同一个事务中，本地数据库操作与记录消息日志操作具备原子性。

2) 定时任务扫描日志

​ 如何保证将消息发送给消息队列呢？

​ 经过第一步消息已经写到消息日志表中，可以启动独立的线程，定时对消息日志表中的消息进行扫描并发送至消息中间件，在消息中间件反馈发送成功后删除该消息日志，否则等待定时任务下一周期重试。

3) 消费消息

​ 如何保证消费者一定能消费到消息呢？

​ 这里可以使用MQ的ack（即消息确认）机制，消费者监听MQ，如果消费者接收到消息并且业务处理完成后向MQ发送ack（即消息确认），此时说明消费者正常消费消息完成，MQ将不再向消费者推送消息，否则消费者会不断重试向消费者来发送消息。

​ 积分服务接收到”增加积分“消息，开始增加积分，积分增加成功后向消息中间件回应ack，否则消息中间件将重复投递此消息。

​ 由于消息会重复投递，积分服务的”增加积分“功能需要实现幂等性。
2.2.RocketMQ事务消息方案

​ RocketMQ 是一个来自阿里巴巴的分布式消息中间件，于 2012 年开源，并在 2017 年正式成为 Apache 顶级项目。据了解，包括阿里云上的消息产品以及收购的子公司在内，阿里集团的消息产品全线都运行在 RocketMQ 之上，并且最近几年的双十一大促中，RocketMQ 都有抢眼表现。Apache RocketMQ 4.3之后的版本正式支持事务消息，为分布式事务实现提供了便利性支持。

​ RocketMQ 事务消息设计则主要是为了解决 Producer 端的消息发送与本地事务执行的原子性问题，RocketMQ 的设计中 broker 与 producer 端的双向通信能力，使得 broker 天生可以作为一个事务协调者存在；而 RocketMQ 本身提供的存储机制为事务消息提供了持久化能力；RocketMQ 的高可用机制以及可靠消息设计则为事务消息在系统发生异常时依然能够保证达成事务的最终一致性。

​ 在RocketMQ 4.3后实现了完整的事务消息，实际上其实是对本地消息表的一个封装，将本地消息表移动到了MQ内部，解决 Producer 端的消息发送与本地事务执行的原子性问题。



执行流程如下：

为方便理解我们还以注册送积分的例子来描述 整个流程。

Producer 即MQ发送方，本例中是用户服务，负责新增用户。MQ订阅方即消息消费方，本例中是积分服务，负责新增积分。

1、Producer 发送事务消息

​ Producer （MQ发送方）发送事务消息至MQ Server，MQ Server将消息状态标记为Prepared（预备状态），注意此时这条消息消费者（MQ订阅方）是无法消费到的。

​ 本例中，Producer 发送 ”增加积分消息“ 到MQ Server。

2、MQ Server回应消息发送成功

​ MQ Server接收到Producer 发送给的消息则回应发送成功表示MQ已接收到消息。

3、Producer 执行本地事务

​ Producer 端执行业务代码逻辑，通过本地数据库事务控制。

​ 本例中，Producer 执行添加用户操作。

4、消息投递

​ 若Producer 本地事务执行成功则自动向MQServer发送commit消息，MQ Server接收到commit消息后将”增加积分消息“ 状态标记为可消费，此时MQ订阅方（积分服务）即正常消费消息；

​ 若Producer 本地事务执行失败则自动向MQServer发送rollback消息，MQ Server接收到rollback消息后 将删除”增加积分消息“ 。

​ MQ订阅方（积分服务）消费消息，消费成功则向MQ回应ack，否则将重复接收消息。这里ack默认自动回应，即程序执行正常则自动回应ack。

5、事务回查

​ 如果执行Producer端本地事务过程中，执行端挂掉，或者超时，MQ Server将会不停的询问同组的其他 Producer来获取事务执行状态，这个过程叫事务回查。MQ Server会根据事务回查结果来决定是否投递消息。

以上主干流程已由RocketMQ实现，对用户侧来说，用户需要分别实现本地事务执行以及本地事务回查方法，因此只需关注本地事务的执行状态即可。

RoacketMQ提供RocketMQLocalTransactionListener接口：

public interface RocketMQLocalTransactionListener {
   /**
   - 发送prepare消息成功此方法被回调，该方法用于执行本地事务
   - @param msg 回传的消息，利用transactionId即可获取到该消息的唯一Id
   - @param arg 调用send方法时传递的参数，当send时候若有额外的参数可以传递到send方法中，这里能获取到
   - @return 返回事务状态，COMMIT：提交  ROLLBACK：回滚  UNKNOW：回调
     */
       RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg);
   /**
   - @param msg 通过获取transactionId来判断这条消息的本地事务执行状态
   - @return 返回事务状态，COMMIT：提交  ROLLBACK：回滚  UNKNOW：回调
     */
       RocketMQLocalTransactionState checkLocalTransaction(Message msg);
   }

    发送事务消息：

以下是RocketMQ提供用于发送事务消息的API：

TransactionMQProducer producer = new TransactionMQProducer("ProducerGroup");
producer.setNamesrvAddr("127.0.0.1:9876");
producer.start();
//设置TransactionListener实现
producer.setTransactionListener(transactionListener）；
//发送事务消息
SendResult sendResult = producer.sendMessageInTransaction(msg, null);

3.RocketMQ实现可靠消息最终一致性事务
3.1.业务说明

​ 本实例通过RocketMQ中间件实现可靠消息最终一致性分布式事务，模拟两个账户的转账交易过程。

​ 两个账户在分别在不同的银行(张三在bank1、李四在bank2)，bank1、bank2是两个微服务。交易过程是，张三给李四转账指定金额。

​ 上述交易步骤，张三扣减金额与给bank2发转账消息，两个操作必须是一个整体性的事务。



3.2.程序组成部分

本示例程序组成部分如下：

数据库：MySQL-5.7.25

​ 包括bank1和bank2两个数据库。

JDK：64位 jdk1.8.0_201

rocketmq 服务端：RocketMQ-4.5.0

rocketmq 客户端：RocketMQ-Spring-Boot-starter.2.0.2-RELEASE

微服务框架：spring-boot-2.1.3、spring-cloud-Greenwich.RELEASE

微服务及数据库的关系 ：

​ dtx/dtx-txmsg-demo/dtx-txmsg-demo-bank1 银行1，操作张三账户， 连接数据库bank1

​ dtx/dtx-txmsg-demo/dtx-txmsg-demo-bank2 银行2，操作李四账户，连接数据库bank2

本示例程序技术架构如下：



交互流程如下：

1、Bank1向MQ Server发送转账消息

2、Bank1执行本地事务，扣减金额

3、Bank2接收消息，执行本地事务，添加金额
3.3.创建数据库

导入数据库脚本：资料\sql\bank1.sql、资料\sql\bank2.sql，已经导过不用重复导入。

创建bank1库，并导入以下表结构和数据(包含张三账户)

CREATE DATABASE `bank1` CHARACTER SET 'utf8' COLLATE 'utf8_general_ci';
DROP TABLE IF EXISTS `account_info`;
CREATE TABLE `account_info`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_name` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '户主姓名',
  `account_no` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '银行卡号',
  `account_password` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '帐户密码',
  `account_balance` double NULL DEFAULT NULL COMMENT '帐户余额',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8 COLLATE = utf8_bin ROW_FORMAT = Dynamic;
INSERT INTO `account_info` VALUES (2, '张三的账户', '1', '', 10000);

创建bank2库，并导入以下表结构和数据(包含李四账户)

CREATE DATABASE `bank2` CHARACTER SET 'utf8' COLLATE 'utf8_general_ci';
CREATE TABLE `account_info`  (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_name` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '户主姓名',
  `account_no` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '银行卡号',
  `account_password` varchar(100) CHARACTER SET utf8 COLLATE utf8_bin NULL DEFAULT NULL COMMENT '帐户密码',
  `account_balance` double NULL DEFAULT NULL COMMENT '帐户余额',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8 COLLATE = utf8_bin ROW_FORMAT = Dynamic;
INSERT INTO `account_info` VALUES (3, '李四的账户', '2', NULL, 0);

在bank1、bank2数据库中新增de_duplication，交易记录表(去重表)，用于交易幂等控制。

DROP TABLE IF EXISTS `de_duplication`;
CREATE TABLE `de_duplication`  (
  `tx_no`  varchar(64) COLLATE utf8_bin NOT NULL,
  `create_time` datetime(0) NULL DEFAULT NULL,
  PRIMARY KEY (`tx_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_bin ROW_FORMAT = Dynamic;

3.4.启动RocketMQ

（1）下载RocketMQ服务器

下载地址：http://mirrors.tuna.tsinghua.edu.cn/apache/rocketmq/4.5.0/rocketmq-all-4.5.0-bin-release.zip

（2）解压并启动

启动nameserver:

set ROCKETMQ_HOME=[rocketmq服务端解压路径]

start [rocketmq服务端解压路径]/bin/mqnamesrv.cmd

启动broker:

set ROCKETMQ_HOME=[rocketmq服务端解压路径]

start [rocketmq服务端解压路径]/bin/mqbroker.cmd -n 127.0.0.1:9876 autoCreateTopicEnable=true

3.5 导入dtx-txmsg-demo

dtx-txmsg-demo是本方案的测试工程，根据业务需求需要创建两个dtx-txmsg-demo工程。

（1）导入dtx-txmsg-demo

​ 导入：资料\基础代码\dtx-txmsg-demo到父工程dtx下。

​ 两个测试工程如下：

​ dtx/dtx-txmsg-demo/dtx-txmsg-demo-bank1 ，操作张三账户，连接数据库bank1

​ dtx/dtx-txmsg-demo/dtx-txmsg-demo-bank2 ，操作李四账户，连接数据库bank2

（2）父工程maven依赖说明

在dtx父工程中指定了SpringBoot和SpringCloud版本

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-dependencies</artifactId>
    <version>2.1.3.RELEASE</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>Greenwich.RELEASE</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

在dtx-txmsg-demo父工程中指定了rocketmq-spring-boot-starter的版本。

<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>2.0.2</version>
 </dependency>

（3）配置rocketMQ

在application-local.propertis中配置rocketMQ nameServer地址及生产组：

rocketmq.producer.group = producer_bank2
rocketmq.name-server = 127.0.0.1:9876

其它详细配置见导入的基础工程。
3.6 dtx-txmsg-demo-bank1

dtx-txmsg-demo-bank1实现如下功能：

1、张三扣减金额，提交本地事务。

2、向MQ发送转账消息。

2）Dao

@Mapper
@Component
public interface AccountInfoDao {
    @Update("update account_info set account_balance=account_balance+#{amount} where account_no=#{accountNo}")
    int updateAccountBalance(@Param("accountNo") String accountNo, @Param("amount") Double amount);

    @Select("select count(1) from de_duplication where tx_no = #{txNo}")
    int isExistTx(String txNo);

    @Insert("insert into de_duplication values(#{txNo},now());")
    int addTx(String txNo);

}

3）AccountInfoService

@Service
@Slf4j
public class AccountInfoServiceImpl implements AccountInfoService {

	@Resource
	private RocketMQTemplate rocketMQTemplate;

	@Autowired
	private AccountInfoDao accountInfoDao;

	/**
	 * 更新帐号余额-发送消息
	 * producer向MQ Server发送消息
	 *
	 * @param accountChangeEvent
	 */
	@Override
	public void sendUpdateAccountBalance(AccountChangeEvent accountChangeEvent) {
		//构建消息体
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("accountChange",accountChangeEvent);
		Message<String> message = MessageBuilder.withPayload(jsonObject.toJSONString()).build();
		TransactionSendResult sendResult = rocketMQTemplate.sendMessageInTransaction("producer_group_txmsg_bank1", "topic_txmsg", message, null);

		log.info("send transcation message body={},result={}",message.getPayload(),sendResult.getSendStatus());
	}

	/**
	 * 更新帐号余额-本地事务
	 * producer发送消息完成后接收到MQ Server的回应即开始执行本地事务
	 *
	 * @param accountChangeEvent
	 */
	@Transactional
	@Override
	public void doUpdateAccountBalance(AccountChangeEvent accountChangeEvent) {
		log.info("开始更新本地事务，事务号：{}",accountChangeEvent.getTxNo());
		accountInfoDao.updateAccountBalance(accountChangeEvent.getAccountNo(),accountChangeEvent.getAmount() * -1);
		//为幂等作准备
		accountInfoDao.addTx(accountChangeEvent.getTxNo());
		if(accountChangeEvent.getAmount() == 2){
			throw new RuntimeException("bank1更新本地事务时抛出异常");
		}
		log.info("结束更新本地事务，事务号：{}",accountChangeEvent.getTxNo());
	}
}

4）RocketMQLocalTransactionListener

编写RocketMQLocalTransactionListener接口实现类，实现执行本地事务和事务回查两个方法。

@Component
@Slf4j
@RocketMQTransactionListener(txProducerGroup = "producer_group_txmsg_bank1")
public class ProducerTxmsgListener implements RocketMQLocalTransactionListener {

    @Autowired
    AccountInfoService accountInfoService;

    @Autowired
    AccountInfoDao accountInfoDao;

    //消息发送成功回调此方法，此方法执行本地事务
    @Override
    @Transactional
    public RocketMQLocalTransactionState executeLocalTransaction(Message message, Object arg) {
        //解析消息内容
        try {
            String jsonString = new String((byte[]) message.getPayload());
            JSONObject jsonObject = JSONObject.parseObject(jsonString);
            AccountChangeEvent accountChangeEvent = JSONObject.parseObject(jsonObject.getString("accountChange"), AccountChangeEvent.class);
            //扣除金额
            accountInfoService.doUpdateAccountBalance(accountChangeEvent);
            return RocketMQLocalTransactionState.COMMIT;
        } catch (Exception e) {
            log.error("executeLocalTransaction 事务执行失败",e);
            e.printStackTrace();
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }

    //此方法检查事务执行状态
    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message message) {
        RocketMQLocalTransactionState state;
        final JSONObject jsonObject = JSON.parseObject(new String((byte[]) message.getPayload()));
        AccountChangeEvent accountChangeEvent = JSONObject.parseObject(jsonObject.getString("accountChange"),AccountChangeEvent.class);

        //事务id
        String txNo = accountChangeEvent.getTxNo();
        int isexistTx = accountInfoDao.isExistTx(txNo);
        log.info("回查事务，事务号: {} 结果: {}", accountChangeEvent.getTxNo(),isexistTx);
        if(isexistTx>0){
            state=  RocketMQLocalTransactionState.COMMIT;
        }else{
            state=  RocketMQLocalTransactionState.UNKNOWN;
        }

        return state;

    }
}

5）Controller

@RestController
@Slf4j
public class AccountInfoController {
    @Autowired
    private AccountInfoService accountInfoService;

    @GetMapping(value = "/transfer")
    public String transfer(@RequestParam("accountNo")String accountNo,@RequestParam("amount") Double amount){
        String tx_no = UUID.randomUUID().toString();
        AccountChangeEvent accountChangeEvent = new AccountChangeEvent(accountNo,amount,tx_no);

        accountInfoService.sendUpdateAccountBalance(accountChangeEvent);
        return "转账成功";
    }
}

3.7 dtx-txmsg-demo-bank2

dtx-txmsg-demo-bank2需要实现如下功能：

1、监听MQ，接收消息。

2、接收到消息增加账户金额。

1） Service

注意为避免消息重复发送，这里需要实现幂等。

@Service
@Slf4j
public class AccountInfoServiceImpl implements AccountInfoService {

    @Autowired
    AccountInfoDao accountInfoDao;


    /**
     * 消费消息，更新本地事务，添加金额
     * @param accountChangeEvent
     */
    @Override
    @Transactional
    public void addAccountInfoBalance(AccountChangeEvent accountChangeEvent) {
      log.info("bank2更新本地账号，账号：{},金额：{}",accountChangeEvent.getAccountNo(),accountChangeEvent.getAmount());

      //幂等校验
        int existTx = accountInfoDao.isExistTx(accountChangeEvent.getTxNo());
        if(existTx<=0){
            //执行更新
            accountInfoDao.updateAccountBalance(accountChangeEvent.getAccountNo(),accountChangeEvent.getAmount());
            //添加事务记录
            accountInfoDao.addTx(accountChangeEvent.getTxNo());
            log.info("更新本地事务执行成功，本次事务号: {}", accountChangeEvent.getTxNo());
        }else{
            log.info("更新本地事务执行失败，本次事务号: {}", accountChangeEvent.getTxNo());
        }

    }
}

2）MQ监听类

@Component
@RocketMQMessageListener(topic = "topic_txmsg",consumerGroup = "consumer_txmsg_group_bank2")
@Slf4j
public class TxmsgConsumer implements RocketMQListener<String> {
    @Autowired
    AccountInfoService accountInfoService;

    @Override
    public void onMessage(String s) {
        log.info("开始消费消息:{}",s);
        //解析消息为对象
        final JSONObject jsonObject = JSON.parseObject(s);
        AccountChangeEvent accountChangeEvent = JSONObject.parseObject(jsonObject.getString("accountChange"),AccountChangeEvent.class);

        //调用service增加账号金额
        accountChangeEvent.setAccountNo("2");
        accountInfoService.addAccountInfoBalance(accountChangeEvent);
    }
}

3.8 测试场景

    bank1本地事务失败，则bank1不发送转账消息。bank2接收转账消息失败，会进行重试发送消息。bank2多次消费同一个消息，实现幂等。
