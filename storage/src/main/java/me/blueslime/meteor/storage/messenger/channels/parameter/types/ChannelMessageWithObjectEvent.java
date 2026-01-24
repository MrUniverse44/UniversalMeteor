package me.blueslime.meteor.storage.messenger.channels.parameter.types;

import me.blueslime.meteor.storage.messenger.channels.parameter.ChannelMessageEvent;

public class ChannelMessageWithObjectEvent extends ChannelMessageEvent {

    private final Object object;
    private final String[] messages;

    
    public ChannelMessageWithObjectEvent(String id, String destiny, Object object, String[] messages) {
        super(id, destiny);
        this.object = object;
        this.messages = messages;
    }

    public Object getObject() {
        return object;
    }

    public String[] getMessages() {
        return messages;
    }

    @Override
    public boolean isEmpty() {
        return object == null && (messages == null || messages.length == 0);
    }
}
