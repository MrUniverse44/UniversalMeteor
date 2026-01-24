package me.blueslime.meteor.modules.api.api.module.data;

public record ModuleData(boolean found, String id, String name, String version, String[] authors, String description, String[] dependencies, int priority, String platform, String[] bukkitDependencies, String folderName) {
}
