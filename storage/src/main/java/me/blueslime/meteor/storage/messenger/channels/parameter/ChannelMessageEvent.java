package me.blueslime.meteor.storage.messenger.channels.parameter;

public abstract class ChannelMessageEvent {

    private final String destiny;
    private final String id;

    public ChannelMessageEvent(String id, String destiny) {
        this.id = id;
        this.destiny = destiny;
    }

    public String getChannelId() {
        return id;
    }

    public String getDestiny() {
        return destiny;
    }

    public abstract boolean isEmpty();

}
