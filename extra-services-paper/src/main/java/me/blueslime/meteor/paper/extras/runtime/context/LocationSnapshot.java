package me.blueslime.meteor.paper.extras.runtime.context;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record LocationSnapshot(
    UUID worldId,
    double x,
    double y,
    double z,
    float yaw,
    float pitch
) {

    public static LocationSnapshot from(Location location) {
        World world = location.getWorld();

        return new LocationSnapshot(
            world == null ? null : world.getUID(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    public Location toLocation(World world) {
        return new Location(
            world,
            x,
            y,
            z,
            yaw,
            pitch
        );
    }

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }

    private int floor(double value) {
        int integer =
                (int) value;

        return value < integer
                ? integer - 1
                : integer;
    }
}