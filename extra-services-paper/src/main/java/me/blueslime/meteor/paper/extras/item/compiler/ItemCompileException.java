package me.blueslime.meteor.paper.extras.item.compiler;

public class ItemCompileException extends RuntimeException {

    private final String path;

    public ItemCompileException(
            String path,
            String message
    ) {
        super(
                path == null || path.isBlank()
                        ? message
                        : "[" + path + "] " + message
        );

        this.path = path;
    }

    public ItemCompileException(
            String path,
            String message,
            Throwable cause
    ) {
        super(
            path == null || path.isBlank()
                ? message
                : "[" + path + "] " + message,
            cause
        );

        this.path = path;
    }

    public String path() {
        return path;
    }
}