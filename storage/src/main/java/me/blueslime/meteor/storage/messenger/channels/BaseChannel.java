package me.blueslime.meteor.storage.messenger.channels;

import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.messenger.channels.cache.ChannelCache;
import me.blueslime.meteor.storage.messenger.Messenger;

import java.util.logging.Logger;

public abstract class BaseChannel extends Channel {
    protected final Messenger messenger;
    protected final ObjectMapper objectMapper;
    protected final ChannelCache cache;
    protected final Logger logger;

    public BaseChannel(Messenger messenger) {
        this.messenger = messenger;
        this.objectMapper = messenger.mapper();
        this.cache = new ChannelCache();
        this.logger = messenger.getLogger();
    }

    public BaseChannel(Messenger messenger, ChannelCache cache) {
        this.messenger = messenger;
        this.objectMapper = messenger.mapper();
        this.cache = cache;
        this.logger = messenger.getLogger();
    }

    public BaseChannel(Messenger messenger, ObjectMapper mapper, ChannelCache cache, Logger logger) {
        this.messenger = messenger;
        this.objectMapper = mapper;
        this.cache = cache;
        this.logger = logger;
    }

    protected void publishRaw(String payload) {
        messenger.publish(getId(), payload);
    }

    public ChannelCache cache() {
        return cache;
    }
}
