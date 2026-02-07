package me.blueslime.meteor.platforms.api.entity;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.utilities.text.TextReplacer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Abstract implementation of the Sender interface.
 * <p>
 * This class handles the logic for message processing (coloring, replacing) and
 * configuration reading, delegating the actual sending of the raw string to the subclass.
 *
 * @param <T> The type of the underlying platform sender (e.g., CommandSender, CommandSource).
 * @param <C> Component to send (e.g., TextComponent, Component from MiniMessage, BaseComponent, String)
 */
public abstract class PlatformSender<T, C> implements Sender {

    /**
     * The platform-specific sender object.
     */
    protected final T handle;

    /**
     * Constructs a new PlatformSender.
     *
     * @param handle The platform-specific sender instance.
     */
    protected PlatformSender(T handle) {
        this.handle = handle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getHandle() {
        return handle;
    }

    // --- MessageNotifier Implementation ---

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(String... messages) {
        send(TextReplacer.EMPTY, messages);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("RedundantLengthCheck")
    @Override
    public void send(TextReplacer replacer, String... messages) {
        if (messages == null || messages.length == 0) {
            return;
        }
        for (String message : messages) {
            if (message == null) continue;

            // Apply the replacer if present
            String result = replacer == null ? message : replacer.apply(message);

            // TODO: Implement custom global Placeholder system here
            // if (GlobalPlaceholders.hasProvider()) {
            //     result = GlobalPlaceholders.parse(this, result);
            // }

            // Colorize and send to the specific platform implementation
            performSend(colorize(result));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(Collection<String> messages) {
        send(messages, TextReplacer.EMPTY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(Collection<String> messages, TextReplacer replacer) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // Convert Collection to Array to reuse the varargs logic
        send(replacer, messages.toArray(new String[0]));
    }

    // --- ConfigurationHandle Integration ---

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(ConfigurationHandle configuration, String path) {
        send(configuration, path, TextReplacer.EMPTY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void send(ConfigurationHandle configuration, String path, TextReplacer replacer) {
        if (configuration == null || path == null) {
            return;
        }

        if (!configuration.contains(path)) {
            return;
        }

        Object obj = configuration.get(path);

        if (obj instanceof List) {
            // Since the List extends Collection, we can safely cast or pass it directly
            // We use getStringList to ensure safety from the configuration handle
            send(configuration.getStringList(path), replacer);
        } else {
            // It's a single object (String, Integer, etc.), convert to String
            send(replacer, obj != null ? obj.toString() : "");
        }
    }

    // --- Abstract Methods & Utilities ---

    /**
     * Performs the actual sending of the message to the platform-specific handle.
     * <p>
     * This method receives the message <b>already colorized</b> and with placeholders replaced.
     *
     * @param message The final message string to send.
     */
    protected abstract void performSend(C message);

    /**
     * Colorizes the given text using the ColorHandler.
     *
     * @param text The text to colorize.
     * @return The colorized text.
     */
    protected abstract C colorize(String text);

    /**
     * Colorizes a list of strings using TextUtilities.
     *
     * @param list The list of strings to colorize.
     * @return The colorized list.
     */
    protected List<C> colorizeList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        List<C> result = new ArrayList<>();
        for (String s : list) {
            result.add(colorize(s));
        }
        return result;
    }
}