package traincraft.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TC_TRANSPORT_ROPE_GROUP_R1F20C
 *
 * Reusable group transport rope. Up to three mobs/players can be bound.
 * Real mobs receive a visible vanilla leash; players are tracked by UUID
 * because vanilla Minecraft cannot draw a normal leash onto a Player.
 */
public final class TransportRopeItem extends Item {
    private static final String TAG_TARGETS = "TcTransportRopeTargets";
    private static final String ENTRY_ID = "Id";
    private static final String ENTRY_NAME = "Name";
    private static final int MAX_TARGETS = 3;

    // Legacy r1f20b keys are migrated automatically.
    private static final String OLD_TARGET = "TcTransportRopeTarget";
    private static final String OLD_TARGET_NAME = "TcTransportRopeTargetName";

    public TransportRopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(
            ItemStack stack,
            Player player,
            LivingEntity target,
            InteractionHand hand) {

        if (hand != InteractionHand.MAIN_HAND || target == player) {
            return InteractionResult.PASS;
        }

        if (target instanceof Animal) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.literal("Use a normal Lead for the Livestock Car."),
                        true);
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide);
        }

        if (!(target instanceof Mob) && !(target instanceof Player)) {
            return InteractionResult.PASS;
        }

        if (!player.level().isClientSide) {
            boolean added = bind(stack, player, target);

            player.displayClientMessage(
                    Component.literal(added
                            ? "Transport Rope: " + target.getName().getString()
                                    + " bound (" + bindingCount(stack) + "/3)."
                            : "Transport Rope already contains that target or is full (3/3)."),
                    true);
        }

        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private static ListTag list(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();

        // Migrate a pre-r1f20c single binding.
        if (!root.contains(TAG_TARGETS, Tag.TAG_LIST) && root.hasUUID(OLD_TARGET)) {
            ListTag migrated = new ListTag();
            CompoundTag entry = new CompoundTag();
            entry.putUUID(ENTRY_ID, root.getUUID(OLD_TARGET));
            entry.putString(ENTRY_NAME,
                    root.contains(OLD_TARGET_NAME)
                            ? root.getString(OLD_TARGET_NAME)
                            : "");
            migrated.add(entry);
            root.put(TAG_TARGETS, migrated);
            root.remove(OLD_TARGET);
            root.remove(OLD_TARGET_NAME);
        }

        return root.getList(TAG_TARGETS, Tag.TAG_COMPOUND);
    }

    private static void saveList(ItemStack stack, ListTag list) {
        CompoundTag root = stack.getOrCreateTag();

        if (list.isEmpty()) {
            root.remove(TAG_TARGETS);
        } else {
            root.put(TAG_TARGETS, list);
        }

        root.remove(OLD_TARGET);
        root.remove(OLD_TARGET_NAME);
    }

    public static boolean bind(ItemStack stack, Player holder, Entity target) {
        ListTag bindings = list(stack);
        UUID id = target.getUUID();

        for (int i = 0; i < bindings.size(); i++) {
            CompoundTag entry = bindings.getCompound(i);

            if (entry.hasUUID(ENTRY_ID)
                    && id.equals(entry.getUUID(ENTRY_ID))) {

                // Already selected: reassert the physical leash instead of
                // treating a second click as a failure.
                if (target instanceof Mob mob
                        && !(target instanceof Animal)) {
                    mob.setLeashedTo(holder, true);
                }

                saveList(stack, bindings);
                return true;
            }
        }

        if (bindings.size() >= MAX_TARGETS) {
            return false;
        }

        CompoundTag entry = new CompoundTag();
        entry.putUUID(ENTRY_ID, id);
        entry.putString(
                ENTRY_NAME,
                target.getName().getString());
        bindings.add(entry);
        saveList(stack, bindings);

        if (target instanceof Mob mob
                && !(target instanceof Animal)) {
            mob.setLeashedTo(holder, true);
        }

        return true;
    }

    public static List<UUID> boundTargets(ItemStack stack) {
        ListTag bindings = list(stack);
        List<UUID> ids = new ArrayList<>();

        for (int i = 0; i < bindings.size(); i++) {
            CompoundTag entry = bindings.getCompound(i);
            if (entry.hasUUID(ENTRY_ID)) {
                ids.add(entry.getUUID(ENTRY_ID));
            }
        }

        return ids;
    }

    public static UUID boundTarget(ItemStack stack) {
        List<UUID> ids = boundTargets(stack);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public static String boundName(ItemStack stack) {
        ListTag bindings = list(stack);
        return bindings.isEmpty()
                ? ""
                : bindings.getCompound(0).getString(ENTRY_NAME);
    }

    public static int bindingCount(ItemStack stack) {
        return list(stack).size();
    }

    public static int freeBindings(ItemStack stack) {
        return Math.max(0, MAX_TARGETS - bindingCount(stack));
    }

    public static void removeBinding(ItemStack stack, UUID targetId) {
        ListTag bindings = list(stack);
        ListTag kept = new ListTag();

        for (int i = 0; i < bindings.size(); i++) {
            CompoundTag entry = bindings.getCompound(i);
            if (!entry.hasUUID(ENTRY_ID)
                    || !targetId.equals(entry.getUUID(ENTRY_ID))) {
                kept.add(entry.copy());
            }
        }

        saveList(stack, kept);
    }

    public static void clearBinding(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) return;

        root.remove(TAG_TARGETS);
        root.remove(OLD_TARGET);
        root.remove(OLD_TARGET_NAME);
    }

        public static void releaseMobLeash(Entity target, Player holder) {
        if (target instanceof Mob mob
                && mob.isLeashed()
                && mob.getLeashHolder() == holder) {
            // true = broadcast detach to tracking clients
            // false = do not spawn a duplicate leash item
            mob.dropLeash(true, false);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand) {

        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown() && bindingCount(stack) > 0) {
            if (!level.isClientSide) {
                int count = bindingCount(stack);
                clearBinding(stack);
                player.displayClientMessage(
                        Component.literal("Transport Rope cleared (" + count + " target"
                                + (count == 1 ? "" : "s") + ")."),
                        true);
            }

            return InteractionResultHolder.sidedSuccess(
                    stack, level.isClientSide);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return bindingCount(stack) > 0 || super.isFoil(stack);
    }
}