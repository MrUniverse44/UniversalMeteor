package me.blueslime.meteor.platforms.api.utils;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class FileUtil {

    public static void verifyExist(File file, String resource) {
        checkFileExistence(file, build(resource));
    }

    public static void verifyExist(File file, InputStream resource) {
        checkFileExistence(file, resource);
    }

    public static InputStream build(String location) {
        if (location == null) {
            return null;
        }
        if (!location.startsWith("/")) {
            return FileUtil.class.getResourceAsStream("/" + location);
        }
        return FileUtil.class.getResourceAsStream(location);
    }

    public static void checkFileExistence(File file, InputStream resource) {
        if (!file.getParentFile().exists()) {
            boolean createFile = file.getParentFile().mkdirs();
            if (!createFile) {
                return;
            }
        }

        if (!file.exists()) {
            try (InputStream in = resource) {
                cloneResource(file, in);
            } catch (Exception exception) {
                Implements.fetch(PlatformLogger.class).error(exception, "Can't create resource copy of a .yml");
            }
        }
    }

    public static void cloneResource(File file, InputStream in) throws IOException {
        if (in != null) {
            Files.copy(in, file.toPath());
        } else {
            file.createNewFile();
        }
    }
}
