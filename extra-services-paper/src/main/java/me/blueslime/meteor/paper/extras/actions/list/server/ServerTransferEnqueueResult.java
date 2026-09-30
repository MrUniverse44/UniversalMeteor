package me.blueslime.meteor.paper.extras.actions.list.server;

public record ServerTransferEnqueueResult(
    Status status,
    String serverName,
    int position,
    int amount
) {

    public enum Status {
        JOINED,
        REPLACED,
        CANCELLED,
        REJECTED
    }

    public boolean successful() {
        return status != Status.REJECTED;
    }
}
