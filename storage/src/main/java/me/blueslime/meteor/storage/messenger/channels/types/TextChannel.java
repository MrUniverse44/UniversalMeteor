package me.blueslime.meteor.storage.messenger.channels.types;

import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.messenger.channels.BaseChannel;
import me.blueslime.meteor.storage.messenger.channels.cache.ChannelCache;
import me.blueslime.meteor.storage.messenger.Messenger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class TextChannel extends BaseChannel {

    public TextChannel(Messenger messenger) {
        super(messenger);
    }

    public TextChannel(Messenger messenger, ChannelCache cache) {
        super(messenger, cache);
    }

    public TextChannel(Messenger messenger, ObjectMapper mapper, ChannelCache cache, PlatformLogger logger) {
        super(messenger, mapper, cache, logger);
    }

    public void send(String destiny, String... messages) {
        Map<String,Object> payload = new HashMap<>();
        payload.put("type","text");
        payload.put("destiny", destiny);
        if (messages != null && messages.length > 0) {
            payload.put("messages", Arrays.asList(messages));
        } else {
            payload.put("messages", new ArrayList<>());
        }

        String json = objectMapper.toJson(payload);
        publishRaw(json);
    }
}
