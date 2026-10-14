package traincraft.client.event;

// TC_BATCH8_R4A4D_RESPONSIVE_BUILDER_OVERLAY

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import traincraft.Traincraft;
import traincraft.client.screen.PoweredLocomotiveScreen;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.item.TrackBlueprintItem;

@Mod.EventBusSubscriber(
        modid = Traincraft.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TracksBuilderBlueprintOverlay {
    // TC_DEV34_R1C1A_WIDE_BUILDER_OVERLAY_DOCK
    private static final int BUILDER_WIDTH = 391;

    @SubscribeEvent
    public static void onRender(
            ScreenEvent.Render.Post event) {
        if (!(event.getScreen()
                instanceof PoweredLocomotiveScreen screen)) {
            return;
        }

        LegacyPoweredLocomotive locomotive =
                screen.getMenu().getLocomotive();

        if (locomotive == null
                || !locomotive.isBuilder()) {
            return;
        }

        GuiGraphics graphics =
                event.getGuiGraphics();
        Font font =
                Minecraft.getInstance().font;

        ItemStack blueprint =
                locomotive.getPowerInventory()
                        .getStackInSlot(52);

        boolean blueprintMode =
                blueprint.getItem()
                                instanceof TrackBlueprintItem
                        && TrackBlueprintItem
                                .isLocked(blueprint)
                        && TrackBlueprintItem
                                .segmentCount(blueprint) > 0;

        int progress =
                Math.max(
                        0,
                        locomotive.getBuilderBlueprintProgress());

        int total =
                blueprintMode
                        ? TrackBlueprintItem
                                .segmentCount(blueprint)
                        : 0;

        renderSidePanel(
                graphics,
                font,
                screen,
                locomotive,
                blueprint,
                blueprintMode,
                progress,
                total);

        if (blueprintMode
                && progress < total) {
            graphics.pose().pushPose();
            tcDev36R1k29vApplyMainGuiTransform(
                    graphics,
                    screen);
            renderRackRequirements(
                    graphics,
                    font,
                    screen,
                    locomotive,
                    blueprint,
                    progress,
                    total);
            graphics.pose().popPose();
        }
        // TC_DEV36_R1J_STRUCTURE_REQUIREMENTS
        if (blueprintMode
                && traincraft.blueprint.BuilderStructurePlan.count(
                        blueprint) > 0) {
            graphics.pose().pushPose();
            tcDev36R1k29vApplyMainGuiTransform(
                    graphics,
                    screen);
            renderStructureRequirements(
                    graphics,
                    font,
                    screen,
                    locomotive,
                    blueprint);
            graphics.pose().popPose();
        }
        // TC_DEV36_R1K4_REQUIREMENT_TOOLTIPS
        if (blueprintMode) {
            renderRequirementTooltip(
                    graphics,
                    font,
                    screen,
                    locomotive,
                    blueprint,
                    progress,
                    total,
                    event.getMouseX(),
                    event.getMouseY());
        }
    }

    // TC_DEV36_R1K29V_MAIN_GUI_OVERLAY_TRANSFORM
    // ScreenEvent.Render.Post begins with an identity pose, while the main
    // PoweredLocomotiveScreen has already been drawn with the accepted r1k29i
    // 523x272 scale + 66px cluster-center shift. Requirement ghosts/borders/
    // counts must replay that exact transform or they drift off the real slots.
    private static float tcDev36R1k29vMainGuiScale(
            PoweredLocomotiveScreen screen) {
        int margin =
                4;

        float availableWidth =
                Math.max(
                        1.0F,
                        screen.width
                                - margin * 2.0F);

        float availableHeight =
                Math.max(
                        1.0F,
                        screen.height
                                - margin * 2.0F);

        float scale =
                Math.min(
                        1.0F,
                        Math.min(
                                availableWidth / 523.0F,
                                availableHeight / 272.0F));

        return Math.max(
                1.0F / 523.0F,
                scale);
    }

    private static void tcDev36R1k29vApplyMainGuiTransform(
            GuiGraphics graphics,
            PoweredLocomotiveScreen screen) {
        float scale =
                tcDev36R1k29vMainGuiScale(
                        screen);

        float shift =
                66.0F
                        * scale;

        graphics.pose()
                .translate(
                        screen.width / 2.0F
                                + shift,
                        screen.height / 2.0F,
                        0.0F);

        graphics.pose()
                .scale(
                        scale,
                        scale,
                        1.0F);

        graphics.pose()
                .translate(
                        -screen.width / 2.0F,
                        -screen.height / 2.0F,
                        0.0F);
    }

    private static int tcDev36R1k29vLogicalMouseX(
            PoweredLocomotiveScreen screen,
            int physicalX) {
        float scale =
                tcDev36R1k29vMainGuiScale(
                        screen);

        double center =
                screen.width / 2.0D;

        double shift =
                66.0D
                        * scale;

        return (int)Math.floor(
                center
                        + (physicalX
                                - center
                                - shift)
                                / scale);
    }

    private static int tcDev36R1k29vLogicalMouseY(
            PoweredLocomotiveScreen screen,
            int physicalY) {
        float scale =
                tcDev36R1k29vMainGuiScale(
                        screen);

        double center =
                screen.height / 2.0D;

        return (int)Math.floor(
                center
                        + (physicalY
                                - center)
                                / scale);
    }
    private static void renderSidePanel(
            GuiGraphics graphics,
            Font font,
            PoweredLocomotiveScreen screen,
            LegacyPoweredLocomotive locomotive,
            ItemStack blueprint,
            boolean blueprintMode,
            int progress,
            int total) {
        // TC_BATCH8_R4A4D_RESPONSIVE_BUILDER_OVERLAY
        // TC_DEV36_R1E_BUILDER_STATUS_LEFT
        // TC_DEV36_R1F_STATUS_MINIMIZED_RESPONSIVE
        // TC_DEV36_R1K29I_COMBINED_CLUSTER_OVERLAY
        //
        // Treat the left status panel + main 391px Builder screen as one
        // centered responsive cluster. This uses the SAME 523x272 fit as
        // PoweredLocomotiveScreen, so the overlay can never outgrow/overlap
        // the scaled main GUI in a small logical window.
        int panelWidth = 126;
        int panelHeight = 174;
        int gap = 6;
        int margin = 4;

        int clusterWidth =
                panelWidth
                        + gap
                        + BUILDER_WIDTH;

        int clusterHeight =
                272;

        float availableWidth =
                Math.max(
                        1.0F,
                        screen.width
                                - margin * 2.0F);

        float availableHeight =
                Math.max(
                        1.0F,
                        screen.height
                                - margin * 2.0F);

        float panelScale =
                Math.min(
                        1.0F,
                        Math.min(
                                availableWidth
                                        / (float)clusterWidth,
                                availableHeight
                                        / (float)clusterHeight));

        panelScale =
                Math.max(
                        1.0F / (float)clusterWidth,
                        panelScale);

        float clusterShiftX =
                (panelWidth + gap)
                        * 0.5F
                        * panelScale;

        float centerX =
                screen.width / 2.0F;

        float scaledGuiLeft =
                centerX
                        + clusterShiftX
                        + (screen.getGuiLeft()
                                - centerX)
                                * panelScale;

        int scaledWidth =
                Math.max(
                        1,
                        Math.round(
                                panelWidth
                                        * panelScale));

        int scaledHeight =
                Math.max(
                        1,
                        Math.round(
                                panelHeight
                                        * panelScale));

        int x =
                Math.round(
                        scaledGuiLeft
                                - (panelWidth + gap)
                                        * panelScale);

        x =
                Math.max(
                        margin,
                        Math.min(
                                x,
                                screen.width
                                        - margin
                                        - scaledWidth));

        float centerY =
                screen.height / 2.0F;

        float scaledGuiTop =
                centerY
                        + (screen.getGuiTop()
                                - centerY)
                                * panelScale;

        int y =
                Math.round(
                        scaledGuiTop);

        y =
                Math.max(
                        margin,
                        Math.min(
                                y,
                                screen.height
                                        - margin
                                        - scaledHeight));

        graphics.pose().pushPose();
        graphics.pose().translate(
                x,
                y,
                500.0F);
        graphics.pose().scale(
                panelScale,
                panelScale,
                1.0F);
        graphics.pose().translate(
                -x,
                -y,
                0.0F);

        graphics.fill(
                x,
                y,
                x + panelWidth,
                y + panelHeight,
                0xE0181B1F);

        graphics.fill(
                x + 1,
                y + 1,
                x + panelWidth - 1,
                y + 22,
                0xE02B333B);

        graphics.fill(
                x,
                y + 22,
                x + panelWidth,
                y + 24,
                0xFF3CBCD6);

        graphics.fill(x, y, x + panelWidth, y + 1, 0xFF68747E);
        graphics.fill(x, y, x + 1, y + panelHeight, 0xFF68747E);
        graphics.fill(
                x,
                y + panelHeight - 1,
                x + panelWidth,
                y + panelHeight,
                0xFF08090A);
        graphics.fill(
                x + panelWidth - 1,
                y,
                x + panelWidth,
                y + panelHeight,
                0xFF08090A);

        graphics.drawString(
                font,
                "BUILDER / BLUEPRINT",
                x + 7,
                y + 7,
                0xFFFFFFFF,
                false);

        String next = "-";
        String direction = "-";

        if (blueprintMode
                && progress < total) {
            CompoundTag segment =
                    TrackBlueprintItem.segment(
                            blueprint,
                            progress);

            ItemStack nextStack =
                    TrackBlueprintItem
                            .segmentTrackStack(
                                    segment);

            next =
                    nextStack.isEmpty()
                            ? TrackBlueprintItem
                                    .segmentCanonicalItemId(
                                            segment)
                            : nextStack
                                    .getHoverName()
                                    .getString();

            String itemId =
                    normalizeCanonical(
                            TrackBlueprintItem
                                    .segmentCanonicalItemId(
                                            segment));

            if (TrackBlueprintItem
                    .isUnifiedPairId(itemId)) {
                direction =
                        TrackBlueprintItem
                                .segmentIsLeft(segment)
                                ? "LEFT"
                                : "RIGHT";
            } else {
                direction = "N/A";
            }
        }

        String buildStart =
                blueprintMode
                        ? (TrackBlueprintItem
                                        .hasPreviewAnchor(
                                                blueprint)
                                ? "SET"
                                : "AUTO")
                        : "-";

        String status =
                locomotive
                        .getBuilderBlueprintNeed();

        if (status == null
                || status.isEmpty()) {
            status =
                    blueprintMode
                            ? (progress >= total
                                    ? "COMPLETE"
                                    : "READY")
                            : "CLASSIC MODE";
        }

        LinkedHashMap<String, Need> needs =
                blueprintMode
                        ? remainingNeeds(
                                blueprint,
                                progress,
                                total)
                        : new LinkedHashMap<>();

        int missingTypes = 0;
        for (Map.Entry<String, Need> entry :
                needs.entrySet()) {
            if (availableCount(
                            locomotive,
                            entry.getKey())
                    < entry.getValue().count()) {
                missingTypes++;
            }
        }

        int tx = x + 8;
        int ty = y + 30;
        int step = 12;

        graphics.drawString(
                font,
                "Mode: "
                        + (blueprintMode
                                ? "BLUEPRINT"
                                : "CLASSIC"),
                tx,
                ty,
                blueprintMode
                        ? 0xFF65E6FF
                        : 0xFFD0D0D0,
                false);

        graphics.drawString(
                font,
                "Plan: "
                        + (blueprintMode
                                ? Math.min(progress, total)
                                        + "/"
                                        + total
                                : "-"),
                tx,
                ty + step,
                0xFF67B9FF,
                false);

        graphics.drawString(
                font,
                "Track: " + trim(next, 14),
                tx,
                ty + step * 2,
                0xFF65E6FF,
                false);

        graphics.drawString(
                font,
                "Dir: " + direction,
                tx,
                ty + step * 3,
                0xFFFFFFFF,
                false);

        graphics.drawString(
                font,
                "Start: " + buildStart,
                tx,
                ty + step * 4,
                0xFF8DFF8D,
                false);

        boolean badStatus =
                status.startsWith("NEED")
                        || status.contains("FAILED")
                        || status.contains("BLOCKED");

        graphics.drawString(
                font,
                "Status: " + trim(status, 12),
                tx,
                ty + step * 5,
                badStatus
                        ? 0xFFFF8A8A
                        : 0xFFA8D7FF,
                false);

        graphics.drawString(
                font,
                "Materials: " + needs.size(),
                tx,
                ty + step * 6,
                0xFFFFCC72,
                false);

        graphics.drawString(
                font,
                "Missing: " + missingTypes,
                tx,
                ty + step * 7,
                missingTypes > 0
                        ? 0xFFFF7777
                        : 0xFF61E779,
                false);

        graphics.drawString(
                font,
                "Overlap: ACTIVE",
                tx,
                ty + step * 8,
                0xFFFF9A8F,
                false);

        int dividerY =
                ty + step * 9 + 1;

        graphics.fill(
                tx,
                dividerY,
                x + panelWidth - 8,
                dividerY + 1,
                0xAA737373);

        graphics.drawString(
                font,
                "GREEN = full",
                tx,
                dividerY + 7,
                0xFF8DFF9D,
                false);

        graphics.drawString(
                font,
                "RED = short",
                tx,
                dividerY + 18,
                0xFFFF9A9A,
                false);

        graphics.pose().popPose();
    }

    // r4a3h keeps requirement ghosts/counts on the existing 20..51 rack.
    private static void renderRackRequirements(
            GuiGraphics graphics,
            Font font,
            PoweredLocomotiveScreen screen,
            LegacyPoweredLocomotive locomotive,
            ItemStack blueprint,
            int progress,
            int total) {
        LinkedHashMap<String, Need> needs =
                remainingNeeds(
                        blueprint,
                        progress,
                        total);

        if (needs.isEmpty()) {
            return;
        }

        Set<Integer> assigned =
                new HashSet<>();

        int left =
                screen.getGuiLeft();
        int top =
                screen.getGuiTop();

        for (Map.Entry<String, Need> entry :
                needs.entrySet()) {
            String id =
                    entry.getKey();
            Need need =
                    entry.getValue();

            int rackSlot =
                    matchingRackSlot(
                            locomotive,
                            id,
                            assigned);

            if (rackSlot < 0) {
                rackSlot =
                        emptyRackSlot(
                                locomotive,
                                assigned);
            }

            if (rackSlot < 0) {
                break;
            }

            assigned.add(rackSlot);

            int relative =
                    tcDev36R1k12BlueprintMaterialDisplayIndex(
                            rackSlot);
            int row =
                    relative / 8;
            int col =
                    relative % 8;

            int slotX =
                    left
                            + 106
                            + col * 18;
            int slotY =
                    top
                            + 50
                            + row * 18;

            ItemStack actual =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(
                                    rackSlot);

            int have =
                    availableCount(
                            locomotive,
                            id);

            boolean ready =
                    have >= need.count();

            drawBorder(
                    graphics,
                    slotX,
                    slotY,
                    ready
                            ? 0xFF2EFF52
                            : 0xFFFF5555);

            if (actual.isEmpty()
                    && !need.stack().isEmpty()) {
                ItemStack ghost =
                        need.stack().copy();
                ghost.setCount(1);

                graphics.renderItem(
                        ghost,
                        slotX,
                        slotY);

                // Requirement-only ghost: clearly lower opacity than a real item.
                graphics.fill(
                        slotX,
                        slotY,
                        slotX + 16,
                        slotY + 16,
                        0x90FFFFFF);

                graphics.fill(
                        slotX,
                        slotY,
                        slotX + 16,
                        slotY + 16,
                        0x70000000);
            }

            drawCount(
                    graphics,
                    font,
                    have,
                    need.count(),
                    slotX,
                    slotY,
                    ready
                            ? 0xFF8DFF9D
                            : 0xFFFFC0C0);
        }
    }

    private static LinkedHashMap<String, Need> remainingNeeds(
            ItemStack blueprint,
            int progress,
            int total) {
        LinkedHashMap<String, Need> result =
                new LinkedHashMap<>();

        for (int index =
                        Math.max(
                                0,
                                progress);
             index < total;
             index++) {
            CompoundTag segment =
                    TrackBlueprintItem.segment(
                            blueprint,
                            index);

            String id =
                    normalizeCanonical(
                            TrackBlueprintItem
                                    .segmentCanonicalItemId(
                                            segment));

            if (id.isEmpty()) {
                continue;
            }

            ItemStack sample =
                    TrackBlueprintItem
                            .segmentTrackStack(
                                    segment);

            if (sample.isEmpty()) {
                sample =
                        stackForCanonical(
                                id);
            }

            Need previous =
                    result.get(id);

            if (previous == null) {
                result.put(
                        id,
                        new Need(
                                sample,
                                1));
            } else {
                result.put(
                        id,
                        new Need(
                                previous.stack(),
                                previous.count()
                                        + 1));
            }
        }

        return result;
    }

    // TC_DEV36_R1J_STRUCTURE_REQUIREMENTS
    private static void renderStructureRequirements(
            GuiGraphics graphics,
            Font font,
            PoweredLocomotiveScreen screen,
            LegacyPoweredLocomotive locomotive,
            ItemStack blueprint) {
        LinkedHashMap<String, Need> needs =
                remainingStructureNeeds(
                        blueprint);

        if (needs.isEmpty()) {
            return;
        }

        Set<Integer> assigned =
                new HashSet<>();

        int left =
                screen.getGuiLeft();
        int top =
                screen.getGuiTop();

        for (Map.Entry<String, Need> entry :
                needs.entrySet()) {
            String id =
                    entry.getKey();
            Need need =
                    entry.getValue();

            int displaySlot =
                    matchingSupplySlot(
                            locomotive,
                            id,
                            assigned);

            if (displaySlot < 0) {
                displaySlot =
                        emptySupplySlot(
                                locomotive,
                                assigned);
            }

            if (displaySlot < 0) {
                break;
            }

            assigned.add(
                    displaySlot);

            int relative =
                    tcDev36R1k12SupplyDisplayIndex(
                            displaySlot);
            int row =
                    relative / 5;
            int col =
                    relative % 5;

            int slotX =
                    left
                            + 12
                            + col * 18;
            int slotY =
                    top
                            + 92
                            + row * 18;

            ItemStack actual =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(
                                    displaySlot);

            int have =
                    availableStructureCount(
                            locomotive,
                            id);

            boolean ready =
                    have >= need.count();

            drawBorder(
                    graphics,
                    slotX,
                    slotY,
                    ready
                            ? 0xFF2EFF52
                            : 0xFFFF5555);

            if (actual.isEmpty()
                    && !need.stack().isEmpty()) {
                ItemStack ghost =
                        need.stack().copy();
                ghost.setCount(
                        1);

                graphics.renderItem(
                        ghost,
                        slotX,
                        slotY);

                graphics.fill(
                        slotX,
                        slotY,
                        slotX + 16,
                        slotY + 16,
                        0x90FFFFFF);

                graphics.fill(
                        slotX,
                        slotY,
                        slotX + 16,
                        slotY + 16,
                        0x70000000);
            }

            drawCount(
                    graphics,
                    font,
                    have,
                    need.count(),
                    slotX,
                    slotY,
                    ready
                            ? 0xFF8DFF9D
                            : 0xFFFFC0C0);
        }
    }

    private static LinkedHashMap<String, Need>
            remainingStructureNeeds(
                    ItemStack blueprint) {
        // TC_DEV36_R1K4_FULL_STRUCTURE_BOM
        LinkedHashMap<String, Need> result =
                new LinkedHashMap<>();

        int total =
                traincraft.blueprint.BuilderStructurePlan.count(
                        blueprint);

        for (int index = 0;
             index < total;
             index++) {
            CompoundTag structureEntry =
                    traincraft.blueprint.BuilderStructurePlan.entry(
                            blueprint,
                            index);

            net.minecraft.world.level.block.state.BlockState planned =
                    traincraft.blueprint.BuilderStructurePlan.blockState(
                            structureEntry);

            if (planned.isAir()) {
                continue;
            }

            Item item =
                    planned.getBlock()
                            .asItem();

            if (item == net.minecraft.world.item.Items.AIR) {
                continue;
            }

            ResourceLocation key =
                    ForgeRegistries.ITEMS
                            .getKey(
                                    item);

            if (key == null) {
                continue;
            }

            String id =
                    key.toString();

            Need previous =
                    result.get(
                            id);

            ItemStack sample =
                    new ItemStack(
                            item);

            if (previous == null) {
                result.put(
                        id,
                        new Need(
                                sample,
                                1));
            }
            else {
                result.put(
                        id,
                        new Need(
                                previous.stack(),
                                previous.count()
                                        + 1));
            }
        }

        return result;
    }

    private static net.minecraft.core.BlockPos
            rotateStructureRelative(
                    net.minecraft.core.BlockPos relative,
                    int rotation) {
        return switch (Math.floorMod(
                rotation,
                4)) {
            case 1 ->
                    new net.minecraft.core.BlockPos(
                            -relative.getZ(),
                            relative.getY(),
                            relative.getX());
            case 2 ->
                    new net.minecraft.core.BlockPos(
                            -relative.getX(),
                            relative.getY(),
                            -relative.getZ());
            case 3 ->
                    new net.minecraft.core.BlockPos(
                            relative.getZ(),
                            relative.getY(),
                            -relative.getX());
            default ->
                    relative;
        };
    }

    private static net.minecraft.world.level.block.state.BlockState
            rotateStructureState(
                    net.minecraft.world.level.block.state.BlockState source,
                    int rotation) {
        return switch (Math.floorMod(
                rotation,
                4)) {
            case 1 ->
                    source.rotate(
                            net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            case 2 ->
                    source.rotate(
                            net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
            case 3 ->
                    source.rotate(
                            net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
            default ->
                    source;
        };
    }

    private static int matchingSupplySlot(
            LegacyPoweredLocomotive locomotive,
            String id,
            Set<Integer> assigned) {
        for (int slot = 1;
             slot <= 53;
             slot++) {
            if (!tcDev36R1k12SupplySlot(
                    slot)
                    || assigned.contains(
                            slot)) {
                continue;
            }

            ItemStack stack =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(
                                    slot);

            if (matchesStructureItem(
                    stack,
                    id)) {
                return slot;
            }
        }

        return -1;
    }

    private static int emptySupplySlot(
            LegacyPoweredLocomotive locomotive,
            Set<Integer> assigned) {
        for (int slot = 1;
             slot <= 53;
             slot++) {
            if (!tcDev36R1k12SupplySlot(
                    slot)
                    || assigned.contains(
                            slot)) {
                continue;
            }

            if (locomotive
                    .getPowerInventory()
                    .getStackInSlot(
                            slot)
                    .isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private static int availableStructureCount(
            LegacyPoweredLocomotive locomotive,
            String id) {
        int count =
                0;

        for (int slot = 1;
             slot <= 77;
             slot++) {
            if (slot == 52) {
                continue;
            }

            ItemStack stack =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(
                                    slot);

            if (matchesStructureItem(
                    stack,
                    id)) {
                count +=
                        stack.getCount();
            }
        }

        return count;
    }

    private static boolean matchesStructureItem(
            ItemStack stack,
            String id) {
        if (stack == null
                || stack.isEmpty()) {
            return false;
        }

        ResourceLocation key =
                ForgeRegistries.ITEMS
                        .getKey(
                                stack.getItem());

        return key != null
                && key.toString()
                        .equals(
                                id);
    }
    // TC_DEV36_R1K4_REQUIREMENT_TOOLTIPS
    private static void renderRequirementTooltip(
            GuiGraphics graphics,
            Font font,
            PoweredLocomotiveScreen screen,
            LegacyPoweredLocomotive locomotive,
            ItemStack blueprint,
            int progress,
            int total,
            int mouseX,
            int mouseY) {
        // TC_DEV36_R1K29V_REQUIREMENT_HITBOX_TRANSFORM
        int tcDev36R1k29vPhysicalMouseX =
                mouseX;
        int tcDev36R1k29vPhysicalMouseY =
                mouseY;

        mouseX =
                tcDev36R1k29vLogicalMouseX(
                        screen,
                        tcDev36R1k29vPhysicalMouseX);
        mouseY =
                tcDev36R1k29vLogicalMouseY(
                        screen,
                        tcDev36R1k29vPhysicalMouseY);
        ItemStack hovered =
                ItemStack.EMPTY;

        if (progress < total) {
            LinkedHashMap<String, Need> trackNeeds =
                    remainingNeeds(
                            blueprint,
                            progress,
                            total);

            Set<Integer> assignedTrack =
                    new HashSet<>();

            int left =
                    screen.getGuiLeft();
            int top =
                    screen.getGuiTop();

            for (Map.Entry<String, Need> entry :
                    trackNeeds.entrySet()) {
                int displaySlot =
                        matchingRackSlot(
                                locomotive,
                                entry.getKey(),
                                assignedTrack);

                if (displaySlot < 0) {
                    displaySlot =
                            emptyRackSlot(
                                    locomotive,
                                    assignedTrack);
                }

                if (displaySlot < 0) {
                    break;
                }

                assignedTrack.add(
                        displaySlot);

                int relative =
                    tcDev36R1k12BlueprintMaterialDisplayIndex(
                            displaySlot);
                int row =
                        relative / 8;
                int col =
                        relative % 8;

                int slotX =
                        left
                                + 106
                                + col * 18;
                int slotY =
                        top
                                + 50
                                + row * 18;

                if (mouseX >= slotX
                        && mouseX < slotX + 16
                        && mouseY >= slotY
                        && mouseY < slotY + 16) {
                    hovered =
                            entry.getValue()
                                    .stack()
                                    .copy();
                    break;
                }
            }
        }

        if (hovered.isEmpty()
                && traincraft.blueprint.BuilderStructurePlan.count(
                        blueprint) > 0) {
            LinkedHashMap<String, Need> structureNeeds =
                    remainingStructureNeeds(
                            blueprint);

            Set<Integer> assignedStructure =
                    new HashSet<>();

            int left =
                    screen.getGuiLeft();
            int top =
                    screen.getGuiTop();

            for (Map.Entry<String, Need> entry :
                    structureNeeds.entrySet()) {
                int displaySlot =
                        matchingSupplySlot(
                                locomotive,
                                entry.getKey(),
                                assignedStructure);

                if (displaySlot < 0) {
                    displaySlot =
                            emptySupplySlot(
                                    locomotive,
                                    assignedStructure);
                }

                if (displaySlot < 0) {
                    break;
                }

                assignedStructure.add(
                        displaySlot);

                int relative =
                    tcDev36R1k12SupplyDisplayIndex(
                            displaySlot);
                int row =
                        relative / 5;
                int col =
                        relative % 5;

                int slotX =
                        left
                                + 12
                                + col * 18;
                int slotY =
                        top
                                + 92
                                + row * 18;

                if (mouseX >= slotX
                        && mouseX < slotX + 16
                        && mouseY >= slotY
                        && mouseY < slotY + 16) {
                    hovered =
                            entry.getValue()
                                    .stack()
                                    .copy();
                    break;
                }
            }
        }

        if (hovered.isEmpty()) {
            // TC_DEV36_R1K4_PATTERN_HOVER_NAMES
            var patterns =
                    locomotive.getBuilderStructurePatterns();

            if (patterns != null
                    && patterns.getSlots() >= 12) {
                int left =
                        screen.getGuiLeft();
                int top =
                        screen.getGuiTop();

                for (int i = 0;
                     i < 12;
                     i++) {
                    int slotX;
                    int slotY;

                    if (i < 7) {
                        slotX =
                                left
                                        + 261
                                        + i * 18;
                        slotY =
                                top + 58;
                    }
                    else if (i < 9) {
                        slotX =
                                left
                                        + 306
                                        + (i - 7) * 18;
                        slotY =
                                top + 111;
                    }
                    else {
                        slotX =
                                left
                                        + 297
                                        + (i - 9) * 18;
                        slotY =
                                top + 164;
                    }

                    if (mouseX >= slotX
                            && mouseX < slotX + 16
                            && mouseY >= slotY
                            && mouseY < slotY + 16) {
                        ItemStack template =
                                patterns.getStackInSlot(
                                        i);

                        if (!template.isEmpty()) {
                            hovered =
                                    template.copy();
                        }
                        break;
                    }
                }
            }
        }

        if (!hovered.isEmpty()) {
            hovered.setCount(
                    1);

            graphics.renderTooltip(
                    font,
                    hovered,
                    tcDev36R1k29vPhysicalMouseX,
                    tcDev36R1k29vPhysicalMouseY);
        }
    }
    // TC_DEV36_R1K12_EXPANDED_REQUIREMENTS
    // TC_DEV36_R1K14_FINAL_MATERIAL_LAYOUT
    private static boolean tcDev36R1k12BlueprintMaterialSlot(
            int slot) {
        return (slot >= 20
                        && slot <= 51)
                || (slot >= 54
                        && slot <= 77);
    }

    private static boolean tcDev36R1k12SupplySlot(
            int slot) {
        return (slot >= 1
                        && slot <= 19)
                || slot == 53;
    }

    private static int tcDev36R1k12BlueprintMaterialDisplayIndex(
            int slot) {
        if (slot >= 20
                && slot <= 51) {
            return slot - 20;
        }

        // Preserve the already-used r1k12 fifth row exactly.
        if (slot >= 59
                && slot <= 66) {
            return 32 + (slot - 59);
        }

        // Former extra-SUPPLIES slots become first five cells of row 6.
        if (slot >= 54
                && slot <= 58) {
            return 40 + (slot - 54);
        }

        // Three previously-new r1k12 growth cells complete row 6.
        if (slot >= 67
                && slot <= 69) {
            return 45 + (slot - 67);
        }

        // New row 7.
        if (slot >= 70
                && slot <= 77) {
            return 48 + (slot - 70);
        }

        return -1;
    }

    private static int tcDev36R1k12SupplyDisplayIndex(
            int slot) {
        if (slot >= 1
                && slot <= 19) {
            return slot - 1;
        }

        if (slot == 53) {
            return 19;
        }

        return -1;
    }
    private static int matchingRackSlot(
            LegacyPoweredLocomotive locomotive,
            String id,
            Set<Integer> assigned) {
        for (int slot = 20;
             slot <= 77;
             slot++) {
            if (!tcDev36R1k12BlueprintMaterialSlot(
                    slot)
                    || assigned.contains(
                            slot)) {
                continue;
            }

            ItemStack stack =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(
                                    slot);

            if (matches(
                    stack,
                    id)) {
                return slot;
            }
        }

        return -1;
    }

    private static int emptyRackSlot(
            LegacyPoweredLocomotive locomotive,
            Set<Integer> assigned) {
        for (int slot = 20;
             slot <= 77;
             slot++) {
            if (!tcDev36R1k12BlueprintMaterialSlot(
                    slot)
                    || assigned.contains(
                            slot)) {
                continue;
            }

            if (locomotive
                    .getPowerInventory()
                    .getStackInSlot(
                            slot)
                    .isEmpty()) {
                return slot;
            }
        }

        return -1;
    }

    private static int availableCount(
            LegacyPoweredLocomotive locomotive,
            String id) {
        int count =
                0;

        for (int slot = 20;
             slot <= 77;
             slot++) {
            if (!tcDev36R1k12BlueprintMaterialSlot(
                    slot)) {
                continue;
            }

            ItemStack stack =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(
                                    slot);

            if (matches(
                    stack,
                    id)) {
                count +=
                        stack.getCount();
            }
        }

        int[] legacySlots = {
                1, 8, 9, 10
        };

        for (int slot :
                legacySlots) {
            ItemStack stack =
                    locomotive
                            .getPowerInventory()
                            .getStackInSlot(
                                    slot);

            if (matches(
                    stack,
                    id)) {
                count +=
                        stack.getCount();
            }
        }

        return count;
    }

    private static boolean matches(
            ItemStack stack,
            String id) {
        // TC_BATCH8_R4A5G6_BUILDER_RACK_MATCH_SYNC
        //
        // Keep the Blueprint Materials display aligned with the actual Builder
        // laying rules. Unified paired LEFT/RIGHT families already collapse to
        // the same canonical registry ID. 29x/32x are single registry IDs whose
        // hand is NBT, so hand does not make a second material requirement.
        if (stack == null
                || stack.isEmpty()) {
            return false;
        }

        ResourceLocation key =
                ForgeRegistries.ITEMS
                        .getKey(
                                stack.getItem());

        if (key == null) {
            return false;
        }

        String wanted =
                normalizeCanonical(id);

        String supplied =
                normalizeCanonical(
                        key.toString());

        // Exact canonical family/material remains first and authoritative.
        if (supplied.equals(wanted)) {
            return true;
        }

        ResourceLocation wantedKey =
                ResourceLocation
                        .tryParse(wanted);

        ResourceLocation suppliedKey =
                ResourceLocation
                        .tryParse(supplied);

        if (wantedKey == null
                || suppliedKey == null
                || !"traincraft".equals(
                        wantedKey.getNamespace())
                || !"traincraft".equals(
                        suppliedKey.getNamespace())) {
            return false;
        }

        String wantedQuarterBase =
                builderRackSingleIdQuarterBase(
                        wantedKey.getPath());

        // Only an UNSUFFIXED Blueprint 29x/32x geometry is material-flexible.
        // A Blueprint that explicitly saved _steel/_copper/_snow/_energy stays
        // exact-material, matching r4a5g4 Builder execution.
        if (wantedQuarterBase.isEmpty()
                || !wantedKey.getPath()
                        .equals(wantedQuarterBase)) {
            return false;
        }

        return wantedQuarterBase.equals(
                builderRackSingleIdQuarterBase(
                        suppliedKey.getPath()));
    }

    private static String builderRackSingleIdQuarterBase(
            String path) {
        if (path == null) {
            return "";
        }

        String[] bases = {
                "track_curve_29x",
                "track_curve_32x"
        };

        String[] suffixes = {
                "",
                "_steel",
                "_copper",
                "_snow",
                "_energy"
        };

        for (String base : bases) {
            for (String suffix : suffixes) {
                if (path.equals(base + suffix)) {
                    return base;
                }
            }
        }

        return "";
    }
    private static String normalizeCanonical(
            String raw) {
        if (raw == null) {
            return "";
        }

        String id =
                raw.trim();

        if (id.isEmpty()) {
            return "";
        }

        if (id.indexOf(':') < 0) {
            id =
                    "traincraft:" + id;
        }

        return TrackBlueprintItem
                .canonicalTrackId(id);
    }

    private static ItemStack stackForCanonical(
            String id) {
        ResourceLocation key =
                ResourceLocation
                        .tryParse(id);

        if (key == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                ForgeRegistries.ITEMS
                        .getValue(key);

        return item == null
                ? ItemStack.EMPTY
                : new ItemStack(item);
    }

    private static void drawBorder(
            GuiGraphics graphics,
            int x,
            int y,
            int color) {
        graphics.fill(
                x - 1,
                y - 1,
                x + 17,
                y,
                color);
        graphics.fill(
                x - 1,
                y + 16,
                x + 17,
                y + 17,
                color);
        graphics.fill(
                x - 1,
                y,
                x,
                y + 16,
                color);
        graphics.fill(
                x + 16,
                y,
                x + 17,
                y + 16,
                color);
    }

    private static void drawCount(
            GuiGraphics graphics,
            Font font,
            int have,
            int need,
            int x,
            int y,
            int color) {
        String text =
                have + "/" + need;

        float scale =
                Math.min(
                        0.60F,
                        15.0F
                                / Math.max(
                                        1,
                                        font.width(text)));

        int width =
                (int)Math.ceil(
                        font.width(text)
                                * scale);

        graphics.pose().pushPose();
        graphics.pose().translate(
                x + 16 - width,
                y + 9,
                350.0F);
        graphics.pose().scale(
                scale,
                scale,
                1.0F);

        graphics.drawString(
                font,
                text,
                1,
                1,
                0xD0000000,
                false);

        graphics.drawString(
                font,
                text,
                0,
                0,
                color,
                false);

        graphics.pose().popPose();
    }

    private static String trim(
            String text,
            int max) {
        if (text == null) {
            return "";
        }

        if (text.length() <= max) {
            return text;
        }

        return text.substring(
                        0,
                        Math.max(
                                0,
                                max - 3))
                + "...";
    }

    private record Need(
            ItemStack stack,
            int count) {}

    private TracksBuilderBlueprintOverlay() {}
}
