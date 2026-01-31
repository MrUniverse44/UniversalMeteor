package me.blueslime.meteor.platforms.api.utils.files;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("all")
public class YamlConfiguration {

    private static final Pattern KEY_PATTERN = Pattern.compile("^(\\s*)(?:(['\"])(.*?)\\2|([^:'\"\\s]+))\\s*:.*$");

    private static final ThreadLocal<Yaml> YAML_INSTANCE = ThreadLocal.withInitial(() -> {
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(2);
        dumperOptions.setAllowUnicode(true);
        dumperOptions.setSplitLines(false);

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setMaxAliasesForCollections(500);

        Representer representer = new Representer(dumperOptions);
        representer.getPropertyUtils().setSkipMissingProperties(true);

        return new Yaml(new Constructor(loaderOptions), representer, dumperOptions);
    });

    public static PluginConfiguration load(File file, PluginConfiguration defaults) throws IOException {
        if (!file.exists()) {
            return new PluginConfiguration(defaults);
        }
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return getPluginConfiguration(defaults, content);
    }

    @NotNull
    private static PluginConfiguration getPluginConfiguration(PluginConfiguration defaults, String content) {
        Map<String, Object> map = YAML_INSTANCE.get().load(content);
        if (map == null) map = new LinkedHashMap<>();

        PluginConfiguration config = new PluginConfiguration(map, defaults);

        extractComments(content, config);

        return config;
    }

    public static PluginConfiguration load(File file) throws IOException {
        return load(file, null);
    }

    public static PluginConfiguration load(InputStream stream, PluginConfiguration defaults) {
        try {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return getPluginConfiguration(defaults, content);
        } catch (IOException e) {
            e.printStackTrace();
            return new PluginConfiguration(defaults);
        }
    }

    public static void save(PluginConfiguration config, File file) throws IOException {
        String data = YAML_INSTANCE.get().dump(config.toMap());

        String dataWithComments = insertComments(data, config);

        if (file.getParentFile() != null) file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), dataWithComments, StandardCharsets.UTF_8);
    }

    private static void extractComments(String content, PluginConfiguration config) {
        String[] lines = content.split("\n");
        List<String> commentBuffer = new ArrayList<>();

        List<String> keyHierarchy = new ArrayList<>();
        List<Integer> indentHierarchy = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("#")) {
                commentBuffer.add(trimmed.substring(1).trim());
                continue;
            }

            if (trimmed.isEmpty()) {
                continue;
            }

            Matcher matcher = KEY_PATTERN.matcher(line);
            if (matcher.find()) {
                int currentIndent = matcher.group(1).length();

                String keyName = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);

                updateHierarchy(keyHierarchy, indentHierarchy, currentIndent);

                keyHierarchy.add(keyName);
                indentHierarchy.add(currentIndent);

                if (!commentBuffer.isEmpty()) {
                    String fullPath = String.join(".", keyHierarchy);
                    config.setComments(fullPath, new ArrayList<>(commentBuffer));
                    commentBuffer.clear();
                }
            } else {
                commentBuffer.clear();
            }
        }
    }

    private static void updateHierarchy(List<String> keys, List<Integer> indents, int currentIndent) {
        while (!indents.isEmpty() && currentIndent <= indents.get(indents.size() - 1)) {
            keys.remove(keys.size() - 1);
            indents.remove(indents.size() - 1);
        }
    }

    private static String insertComments(String yamlContent, PluginConfiguration config) {
        StringBuilder result = new StringBuilder();
        String[] lines = yamlContent.split("\n");

        List<String> keyHierarchy = new ArrayList<>();
        List<Integer> indentHierarchy = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().startsWith("#")) {
                result.append(line).append("\n");
                continue;
            }

            Matcher matcher = KEY_PATTERN.matcher(line);
            if (matcher.find()) {
                int currentIndent = matcher.group(1).length();
                String keyName = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);

                updateHierarchy(keyHierarchy, indentHierarchy, currentIndent);
                keyHierarchy.add(keyName);
                indentHierarchy.add(currentIndent);

                String fullPath = String.join(".", keyHierarchy);
                List<String> comments = config.getComments(fullPath);

                if (!comments.isEmpty()) {
                    String spaces = " ".repeat(currentIndent);
                    for (String comment : comments) {
                        result.append(spaces).append("# ").append(comment).append("\n");
                    }
                }
            }
            result.append(line).append("\n");
        }

        return result.toString();
    }
}