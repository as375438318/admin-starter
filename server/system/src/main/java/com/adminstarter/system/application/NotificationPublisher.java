package com.adminstarter.system.application;

/**
 * 通知发送服务接口：供定时任务、账号安全等模块直接调用，不必经过 HTTP。
 * <p>publish 参与调用方事务（无事务则自建）；publishInNewTransaction 使用独立事务，
 * 业务回滚时通知仍然发出。两个方法都必须经 Spring 代理调用，同类内部自调用不触发事务。</p>
 */
public interface NotificationPublisher {

    /**
     * 在调用方事务内发布（Propagation.REQUIRED）：业务回滚则通知一并回滚。
     */
    Long publish(NotificationCommand command);

    /**
     * 在独立事务中发布（Propagation.REQUIRES_NEW）：业务回滚不影响通知落库。
     */
    Long publishInNewTransaction(NotificationCommand command);
}
