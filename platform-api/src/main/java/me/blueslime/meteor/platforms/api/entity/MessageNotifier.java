package me.blueslime.meteor.platforms.api.entity;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.utilities.text.TextReplacer;

import java.util.Collection;

public interface MessageNotifier {

    /**
     * Sends a single message or multiple messages to the sender.
     *
     * @param messages The messages to send.
     */
    void send(String... messages);

    /**
     * Sends messages to the sender with placeholder replacements.
     *
     * @param replacer The text replacer to apply to the messages.
     * @param messages The messages to send.
     */
    void send(TextReplacer replacer, String... messages);

    /**
     * Sends a collection of messages to the sender.
     * <p>
     * This supports Lists, Sets, and other Collection types.
     *
     * @param messages The collection of messages to send.
     */
    void send(Collection<String> messages);

    /**
     * Sends a collection of messages to the sender with placeholder replacements.
     *
     * @param messages The collection of messages to send.
     * @param replacer The text replacer to apply to the messages.
     */
    void send(Collection<String> messages, TextReplacer replacer);

    /**
     * Sends a message retrieved from a configuration file.
     * <p>
     * It automatically detects if the path corresponds to a String or a List/Collection.
     *
     * @param configuration The configuration handle to read from.
     * @param path          The path in the configuration file.
     */
    void send(ConfigurationHandle configuration, String path);

    /**
     * Sends a message retrieved from a configuration file with placeholder replacements.
     * <p>
     * It automatically detects if the path corresponds to a String or a List/Collection.
     *
     * @param configuration The configuration handle to read from.
     * @param path          The path in the configuration file.
     * @param replacer      The text replacer to apply to the retrieved message(s).
     */
    void send(ConfigurationHandle configuration, String path, TextReplacer replacer);
}
