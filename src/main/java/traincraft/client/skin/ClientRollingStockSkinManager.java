package traincraft.client.skin;

// TC_BATCH6_R2_LEGACY_SKIN_GUI

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import traincraft.client.screen.RollingStockSkinScreen;
import traincraft.skin.SkinChangerEvents;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-side renderer bridge and selector data for the classic Skin Changer. */
public final class ClientRollingStockSkinManager {
    public record SkinOption(String key, String displayName, ResourceLocation texture) {}

    private static final Map<UUID, Set<ResourceLocation>> BASE_TEXTURES =
            new ConcurrentHashMap<>();
    private static final Map<String, Boolean> SAME_BYTES =
            new ConcurrentHashMap<>();

    public static void applySyncedSkin(int entityId, String skinKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity != null) {
            entity.getPersistentData().putString(
                    SkinChangerEvents.SKIN_KEY_TAG,
                    skinKey == null ? "" : skinKey);
            entity.getPersistentData().remove(SkinChangerEvents.SKIN_INDEX_TAG);
        }
    }

    public static ResourceLocation texture(Entity entity, ResourceLocation baseTexture) {
        if (entity == null || baseTexture == null) {
            return baseTexture;
        }

        BASE_TEXTURES
                .computeIfAbsent(entity.getUUID(), ignored -> ConcurrentHashMap.newKeySet())
                .add(baseTexture);

        String key = entity.getPersistentData().getString(SkinChangerEvents.SKIN_KEY_TAG);
        if (key.isEmpty()) {
            return baseTexture;
        }

        ResourceLocation resolved = LegacySkinCatalog.resolve(baseTexture, key);
        return resourceExists(resolved) ? resolved : baseTexture;
    }

    public static String selectedKey(Entity entity) {
        return entity == null ? ""
                : entity.getPersistentData().getString(SkinChangerEvents.SKIN_KEY_TAG);
    }

    public static List<SkinOption> options(Entity entity) {
        if (entity == null) {
            return List.of();
        }

        Set<ResourceLocation> bases = BASE_TEXTURES.get(entity.getUUID());

        // TC_BATCH6_R5A_RENDERER_DISCOVERY
        // Some multipart legacy renderers bind their body/bogie textures directly
        // and therefore may not pass through texture(...) before the player opens
        // the Train Colors Catalog. Ask the actual registered entity renderer for
        // its texture once, then re-read BASE_TEXTURES. If that renderer itself is
        // Skin Changer-aware, texture(...) records the real base as a side effect.
        // Otherwise the returned renderer texture becomes the safe primary base.
        if (bases == null || bases.isEmpty()) {
            ResourceLocation discovered = null;
            try {
                var renderer = Minecraft.getInstance()
                        .getEntityRenderDispatcher()
                        .getRenderer(entity);
                if (renderer != null) {
                    discovered = renderer.getTextureLocation(entity);
                }
            } catch (RuntimeException ignored) {
                // Keep the legacy-safe empty result below if renderer discovery
                // cannot be performed for this entity.
            }

            bases = BASE_TEXTURES.get(entity.getUUID());
            if ((bases == null || bases.isEmpty()) && discovered != null) {
                BASE_TEXTURES
                        .computeIfAbsent(entity.getUUID(), ignored -> ConcurrentHashMap.newKeySet())
                        .add(discovered);
                bases = BASE_TEXTURES.get(entity.getUUID());
            }
        }

        if (bases == null || bases.isEmpty()) {
            return List.of();
        }

        List<SkinOption> best = List.of();
        for (ResourceLocation base : bases.stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
            List<SkinOption> current = optionsForBase(base);
            if (current.size() > best.size()) {
                best = current;
            }
        }
        return best;
    }

    private static List<SkinOption> optionsForBase(ResourceLocation base) {
        ArrayList<SkinOption> result = new ArrayList<>();
        result.add(new SkinOption("", "Original", base));

        for (LegacySkinCatalog.Entry entry : LegacySkinCatalog.entries(base)) {
            if (!resourceExists(entry.texture())) {
                continue;
            }
            if (sameBytes(base, entry.texture())) {
                continue;
            }
            result.add(new SkinOption(entry.key(), entry.displayName(), entry.texture()));
        }
        return List.copyOf(result);
    }

    public static void openMenu(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity == null) {
            return;
        }

        List<SkinOption> options = options(entity);
        if (options.isEmpty()) {
            minecraft.player.displayClientMessage(
                    Component.literal("Skin data is still loading. Look at the train and try again."),
                    true);
            return;
        }
        if (options.size() <= 1) {
            minecraft.player.displayClientMessage(
                    Component.literal("No alternate skins are available for this train."),
                    true);
            return;
        }

        minecraft.setScreen(new RollingStockSkinScreen(entityId));
    }

    private static boolean resourceExists(ResourceLocation texture) {
        if (texture == null) {
            return false;
        }
        return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
    }

    private static boolean sameBytes(ResourceLocation a, ResourceLocation b) {
        String key = a + "|" + b;
        return SAME_BYTES.computeIfAbsent(key, ignored -> compareResources(a, b));
    }

    private static boolean compareResources(ResourceLocation a, ResourceLocation b) {
        try {
            Optional<Resource> ra = Minecraft.getInstance().getResourceManager().getResource(a);
            Optional<Resource> rb = Minecraft.getInstance().getResourceManager().getResource(b);
            if (ra.isEmpty() || rb.isEmpty()) {
                return false;
            }
            try (InputStream ia = ra.get().open(); InputStream ib = rb.get().open()) {
                return Arrays.equals(ia.readAllBytes(), ib.readAllBytes());
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private ClientRollingStockSkinManager() {}
}
