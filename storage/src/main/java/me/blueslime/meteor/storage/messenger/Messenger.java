package me.blueslime.meteor.storage.messenger;

import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.messenger.channels.parameter.ChannelMessageEvent;
import java.util.function.Consumer;

public interface Messenger extends PlatformService {
    /**
     * Publish a message payload for a given channel id.
     * Payload SHOULD be a String (JSON) produced by ObjectMapper.
     */
    void publish(String channelId, String payload);

    /**
     * Subscribe to incoming messages for a given channel id.
     * The messenger implementation must call the consumer when a message arrives.
     * Returns an opaque subscription id (optional).
     */
    String subscribe(String channelId, Consumer<ChannelMessageEvent> consumer);

    /**
     * Unsubscribe a previously created subscription (if supported).
     */
    void unsubscribe(String subscriptionId);

    /**
     * Shutdown / cleanup resources.
     */
    void shutdown();

    default ObjectMapper mapper() {
        return new ObjectMapper();
    };
}
