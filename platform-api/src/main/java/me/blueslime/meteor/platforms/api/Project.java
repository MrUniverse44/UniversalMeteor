package me.blueslime.meteor.platforms.api;

public record Project(String name) {
    /**
     * Set up the name of your project
     * @param name of the plugin/mod/project
     * @return project instance, also this instance will be registered in fetch()
     */
    public static Project named(String name) {
        return new Project(name);
    }
}
