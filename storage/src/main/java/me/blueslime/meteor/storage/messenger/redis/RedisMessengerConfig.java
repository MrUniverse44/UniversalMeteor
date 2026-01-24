package me.blueslime.meteor.storage.messenger.redis;

import me.blueslime.meteor.storage.messenger.types.RedisMessenger;

import java.time.Duration;

public class RedisMessengerConfig {
    private boolean asyncPublish = true;           
    private boolean asyncProcess = true;           
    private RedisMessenger.SubscribeMode defaultMode = RedisMessenger.SubscribeMode.PUBSUB;
    private String streamPrefix = "stream:";       
    private String consumerGroupPrefix = "cg:";    
    private int streamReadCount = 10;              
    private long streamBlockMillis = 5_000L;       
    private int publisherThreads = 2;
    private int subscriberThreads = 4;
    private int processingThreads = 8;
    private Duration reconnectBackoff = Duration.ofSeconds(2);

    private RedisMessengerConfig() {

    }

    public static RedisMessengerConfig builder() {
        return new RedisMessengerConfig();
    }

    
    public boolean ackAfterProcessing = true;

    public boolean isAckAfterProcessing() {
        return ackAfterProcessing;
    }

    public RedisMessengerConfig setAckAfterProcessing(boolean ackAfterProcessing) {
        this.ackAfterProcessing = ackAfterProcessing;
        return this;
    }

    public boolean isAsyncProcess() {
        return asyncProcess;
    }

    public RedisMessengerConfig setAsyncProcess(boolean asyncProcess) {
        this.asyncProcess = asyncProcess;
        return this;
    }

    public boolean isAsyncPublish() {
        return asyncPublish;
    }

    public RedisMessengerConfig setAsyncPublish(boolean asyncPublish) {
        this.asyncPublish = asyncPublish;
        return this;
    }

    public Duration getReconnectBackoff() {
        return reconnectBackoff;
    }

    public RedisMessengerConfig setReconnectBackoff(Duration reconnectBackoff) {
        this.reconnectBackoff = reconnectBackoff;
        return this;
    }

    public int getProcessingThreads() {
        return processingThreads;
    }

    public RedisMessengerConfig setProcessingThreads(int processingThreads) {
        this.processingThreads = processingThreads;
        return this;
    }

    public int getSubscriberThreads() {
        return subscriberThreads;
    }

    public RedisMessengerConfig setSubscriberThreads(int subscriberThreads) {
        this.subscriberThreads = subscriberThreads;
        return this;
    }

    public int getStreamReadCount() {
        return streamReadCount;
    }

    public RedisMessengerConfig setStreamReadCount(int streamReadCount) {
        this.streamReadCount = streamReadCount;
        return this;
    }

    public long getStreamBlockMillis() {
        return streamBlockMillis;
    }

    public RedisMessengerConfig setStreamBlockMillis(long streamBlockMillis) {
        this.streamBlockMillis = streamBlockMillis;
        return this;
    }

    public int getPublisherThreads() {
        return publisherThreads;
    }

    public RedisMessengerConfig setPublisherThreads(int publisherThreads) {
        this.publisherThreads = publisherThreads;
        return this;
    }

    public String getConsumerGroupPrefix() {
        return consumerGroupPrefix;
    }

    public RedisMessengerConfig setConsumerGroupPrefix(String consumerGroupPrefix) {
        this.consumerGroupPrefix = consumerGroupPrefix;
        return this;
    }

    public String getStreamPrefix() {
        return streamPrefix;
    }

    public RedisMessengerConfig setStreamPrefix(String streamPrefix) {
        this.streamPrefix = streamPrefix;
        return this;
    }

    public RedisMessenger.SubscribeMode getSubscriberMode() {
        return defaultMode;
    }

    public void setSubscriberMode(RedisMessenger.SubscribeMode defaultMode) {
        this.defaultMode = defaultMode;
    }
}
