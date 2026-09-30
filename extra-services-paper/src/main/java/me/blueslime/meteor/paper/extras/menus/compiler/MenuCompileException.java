package me.blueslime.meteor.paper.extras.menus.compiler;

public class MenuCompileException extends RuntimeException {

    private final String menuId;

    public MenuCompileException(
            String menuId,
            String message
    ) {
        super(
                "[" + menuId + "] "
                        + message
        );

        this.menuId =
                menuId;
    }

    public MenuCompileException(
            String menuId,
            String message,
            Throwable cause
    ) {
        super(
                "[" + menuId + "] "
                        + message,
                cause
        );

        this.menuId =
                menuId;
    }

    public String menuId() {
        return menuId;
    }
}