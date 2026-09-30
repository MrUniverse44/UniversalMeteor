package me.blueslime.meteor.paper.extras.runtime.compiler;

@FunctionalInterface
public interface CompilationReporter {

    void report(
            String resourceType,
            String resourceId,
            String path,
            RuntimeException exception
    );

    static CompilationReporter noop() {
        return (
                resourceType,
                resourceId,
                path,
                exception
        ) -> {};
    }
}
