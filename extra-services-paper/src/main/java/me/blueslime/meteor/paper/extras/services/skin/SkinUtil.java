package me.blueslime.meteor.paper.extras.services.skin;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

import me.blueslime.meteor.utilities.consumer.PluginConsumer;
import me.blueslime.meteor.utilities.text.TextReplacer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinIdentifier;
import net.skinsrestorer.api.storage.PlayerStorage;
import org.bukkit.entity.Player;

public class SkinUtil {

    private static final AsyncCache<UUID, BufferedImage> HEAD_CACHE = Caffeine.newBuilder()
        .expireAfterAccess(15, TimeUnit.MINUTES)
        .maximumSize(500)
        .buildAsync();

    // Arreglo constante para optimizar la conversión de colores
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static final boolean CONTAINS_SKIN_RESTORER = PluginConsumer.ofUnchecked(
        () -> {
            Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            return true;
        },
        e -> {},
        () -> false
    );

    /**
     * Devuelve el CompletableFuture con la cabeza del jugador.
     * Si múltiples hilos piden el mismo UUID a la vez, Caffeine agrupa las peticiones
     * y ejecuta fetchHeadSync solo una vez.
     */
    public static CompletableFuture<BufferedImage> getPlayerHeadAsync(Player player) {
        return HEAD_CACHE.get(player.getUniqueId(), (uuid, executor) ->
                CompletableFuture.supplyAsync(() -> fetchHeadSync(player), executor)
        );
    }

    /**
     * Lógica síncrona de obtención (Ejecutada en un hilo secundario por CompletableFuture)
     */
    private static BufferedImage fetchHeadSync(Player player) {
        try {
            // OPTIMIZACIÓN PAPER API: Intentar obtener la URL de textura directamente del perfil.
            // Esto usa la CDN de Mojang (textures.minecraft.net), es rapidísimo y NO tiene rate-limits.
            URL skinUrl = player.getPlayerProfile().getTextures().getSkin();
            if (skinUrl != null) {
                BufferedImage fullSkin = ImageIO.read(skinUrl);
                if (fullSkin != null) {
                    return fullSkin.getSubimage(8, 8, 8, 8); // Extraer capa base de la cabeza
                }
            }
        } catch (Exception ignored) {
        }

        UUID uuid = player.getUniqueId();
        String target;
        if (isSkinsRestorerAvailable()) {
            SkinIdentifier skinData = getPlayerSkin(uuid);
            target = skinData != null ? skinData.getIdentifier() : uuid.toString();
        } else {
            target = uuid.toString();
        }

        List<String> fallbacks = Arrays.asList(
            "https://minotar.net/avatar/" + target + "/8.png",
            "https://crafatar.com/renders/head/" + uuid + "?scale=8"
        );

        for (String urlStr : fallbacks) {
            try {
                @SuppressWarnings("deprecation") BufferedImage head = ImageIO.read(new URL(urlStr));
                if (head != null) {
                    return head;
                }
            } catch (Exception ignored) {}
        }

        throw new RuntimeException("No se pudo obtener la cabeza del jugador: " + player.getName());
    }

    /**
     * Convierte la imagen a líneas de texto de Minecraft.
     */
    public static TextReplacer convertToLines(BufferedImage image) {
        TextReplacer replacer = TextReplacer.builder();

        for (int y = 0; y < 8; y++) {
            StringBuilder line = new StringBuilder(72);
            for (int x = 0; x < 8; x++) {
                int rgb = image.getRGB(x, y);
                line.append(toFastHexBlock(rgb));
            }
            replacer = replacer.replace("<player-head-" + y + ">", line.toString());
        }
        return replacer;
    }

    /**
     * OPTIMIZACIÓN MICRO-RENDIMIENTO: Convertidor hexadecimal Bitwise.
     * Evita alojar objetos (new Color()) y usar Regex (String.format()),
     * lo cual era letal al ejecutarse 64 veces por cada cabeza generada.
     */
    private static String toFastHexBlock(int rgb) {
        char[] hex = new char[9];
        hex[0] = '&';
        hex[1] = '#';
        hex[2] = HEX_CHARS[(rgb >> 20) & 0xF]; // Red (primeros 4 bits)
        hex[3] = HEX_CHARS[(rgb >> 16) & 0xF]; // Red (últimos 4 bits)
        hex[4] = HEX_CHARS[(rgb >> 12) & 0xF]; // Green
        hex[5] = HEX_CHARS[(rgb >> 8) & 0xF];
        hex[6] = HEX_CHARS[(rgb >> 4) & 0xF];  // Blue
        hex[7] = HEX_CHARS[rgb & 0xF];
        hex[8] = '█';
        return new String(hex);
    }

    public static SkinIdentifier getPlayerSkin(UUID uuid) {
        PlayerStorage playerStorage = SkinsRestorerProvider.get().getPlayerStorage();
        Optional<SkinIdentifier> property = playerStorage.getSkinIdOfPlayer(uuid);
        return property.orElse(null);
    }

    public static boolean isSkinsRestorerAvailable() {
        return CONTAINS_SKIN_RESTORER;
    }
}

