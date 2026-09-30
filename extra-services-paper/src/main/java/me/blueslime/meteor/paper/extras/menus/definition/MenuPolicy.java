package me.blueslime.meteor.paper.extras.menus.definition;

public record MenuPolicy(
        boolean cancelTopInventoryClicks,
        boolean cancelPlayerInventoryClicks,
        boolean cancelDrag
) {

    public static MenuPolicy protectedMenu() {
        return new MenuPolicy(
                true,
                true,
                true
        );
    }

    public static MenuPolicy permissive() {
        return new MenuPolicy(
                false,
                false,
                false
        );
    }
}
