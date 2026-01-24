package me.blueslime.meteor.modules.api.loader.method;

import java.util.Arrays;
import java.util.Objects;

public record MethodSignature(String name, Class<?>[] params) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodSignature that = (MethodSignature) o;
        return Objects.equals(name, that.name) && Arrays.equals(params, that.params);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name);
        result = 31 * result + Arrays.hashCode(params);
        return result;
    }
}
