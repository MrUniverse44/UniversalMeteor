package me.blueslime.meteor.storage.messenger.channels.types;

import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.storage.interfaces.StorageObject;
import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.messenger.channels.BaseChannel;
import me.blueslime.meteor.storage.messenger.channels.cache.ChannelCache;
import me.blueslime.meteor.storage.messenger.Messenger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class ObjectChannel<V extends StorageObject> extends BaseChannel {

    public ObjectChannel(Messenger messenger) {
        super(messenger);
    }

    public ObjectChannel(Messenger messenger, ChannelCache cache) {
        super(messenger, cache);
    }

    public ObjectChannel(Messenger messenger, ObjectMapper mapper, ChannelCache cache, PlatformLogger logger) {
        super(messenger, mapper, cache, logger);
    }

    public void send(V value, String destiny, String... messages) {
        Map<String, Object> map = objectMapper.toDocument(value);
        Map<String, Object> payload = new HashMap<>();
        payload.put("destiny", destiny == null ? "" : destiny);
        payload.put("type", "object");
        payload.put("class", value.getClass().getName());
        payload.put("data", map);

        if (messages != null && messages.length > 0) {
            payload.put("messages", Arrays.asList(messages));
        } else {
            payload.put("messages", new ArrayList<>());
        }

        String json = objectMapper.toJson(payload);
        publishRaw(json);
    }
}

