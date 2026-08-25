package com.kyc.ports;

public interface QueuePort {

    void publish(String queueName, String payload);
}
