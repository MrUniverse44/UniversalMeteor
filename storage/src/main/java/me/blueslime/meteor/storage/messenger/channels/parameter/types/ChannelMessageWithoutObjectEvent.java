package me.blueslime.meteor.storage.messenger.channels.parameter.types;

import me.blueslime.meteor.storage.messenger.channels.parameter.ChannelMessageEvent;

public class ChannelMessageWithoutObjectEvent extends ChannelMessageEvent {

    private final String[] messages;

    public ChannelMessageWithoutObjectEvent(String id, String destiny, String[] messages) {
        super(id, destiny);
        this.messages = messages;
    }

    public String[] getMessages() {
        return messages;
    }

    @Override
    public boolean isEmpty() {
        return messages == null || messages.length == 0;
    }
}
