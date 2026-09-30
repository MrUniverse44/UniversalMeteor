package me.blueslime.meteor.paper.extras.menus.session;

public record MenuOpenResult(
        MenuOpenStatus status,
        MenuSession session
) {

    public boolean opened() {
        return status ==
                MenuOpenStatus.OPENED;
    }

    public static MenuOpenResult opened(
            MenuSession session
    ) {
        return new MenuOpenResult(
                MenuOpenStatus.OPENED,
                session
        );
    }

    public static MenuOpenResult of(
            MenuOpenStatus status
    ) {
        return new MenuOpenResult(
                status,
                null
        );
    }
}