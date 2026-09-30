package me.blueslime.meteor.paper.extras.actions.list.server;

public enum ServerTransferState {

    WAITING,

    /**
     * At least one Connect request was sent.
     * <br>
     * IMPORTANT:
     * <br>
     * This does NOT mean that the player was
     * successfully transferred.
     * <br>
     * The request remains in this state and inside
     * the queue while the player is still connected
     * to this backend.
     */
    TRANSFERRING
}
