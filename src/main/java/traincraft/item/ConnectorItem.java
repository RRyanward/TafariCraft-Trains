/*
 * Traincraft 1.20.1 rolling-stock connector.
 * Ported from the original Traincraft ItemConnector interaction pattern.
 * Distributed under LGPL-v3.0.
 */
package traincraft.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import traincraft.entity.train.CoupleableRollingStock;
import traincraft.entity.train.CouplingManager;

import java.util.UUID;

/** Two-click connector for locomotive/tender and Step 7.2.1 tender-chain coupling. */
public class ConnectorItem extends Item {
    private static final String SELECTED_ROLLING_STOCK_KEY = "SelectedRollingStock";

    public ConnectorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && hasSelection(stack)) {
            if (!level.isClientSide) {
                clearSelection(stack);
                player.displayClientMessage(Component.translatable("message.traincraft.connector.cleared"), false);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    public static InteractionResult handleRollingStockClick(Entity clicked, Player player, InteractionHand hand) {
        ItemStack connector = player.getItemInHand(hand);
        if (!(connector.getItem() instanceof ConnectorItem)) {
            return InteractionResult.PASS;
        }

        if (!(clicked instanceof CoupleableRollingStock clickedStock)) {
            return InteractionResult.PASS;
        }

        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // With two couplers active, shift-click means "separate this vehicle" and
        // safely releases both ends.  Two-clicking an exact pair still toggles only
        // that pair, which is useful for removing a single car from a consist.
        if (player.isShiftKeyDown()) {
            boolean unlinked = CouplingManager.unlinkAll(clicked, clickedStock);
            clearSelection(connector);
            player.displayClientMessage(Component.translatable(unlinked
                    ? "message.traincraft.connector.uncoupled"
                    : "message.traincraft.connector.cleared"), false);
            return InteractionResult.CONSUME;
        }

        UUID selectedUuid = getSelection(connector);
        if (selectedUuid == null) {
            saveSelection(connector, clicked.getUUID());
            player.displayClientMessage(
                    Component.translatable("message.traincraft.connector.selected", clicked.getName()), false);
            return InteractionResult.CONSUME;
        }

        if (selectedUuid.equals(clicked.getUUID())) {
            clearSelection(connector);
            player.displayClientMessage(Component.translatable("message.traincraft.connector.cleared"), false);
            return InteractionResult.CONSUME;
        }

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.CONSUME;
        }

        Entity selected = serverLevel.getEntity(selectedUuid);
        if (!(selected instanceof CoupleableRollingStock selectedStock) || selected.isRemoved()) {
            clearSelection(connector);
            player.displayClientMessage(Component.translatable("message.traincraft.connector.invalid_first"), false);
            return InteractionResult.CONSUME;
        }

        if (selected.distanceToSqr(clicked) > CouplingManager.MAX_LINK_DISTANCE * CouplingManager.MAX_LINK_DISTANCE) {
            clearSelection(connector);
            player.displayClientMessage(Component.translatable("message.traincraft.connector.too_far"), false);
            return InteractionResult.CONSUME;
        }

        if (!CouplingManager.isSupportedPair(selected, clicked)) {
            clearSelection(connector);
            player.displayClientMessage(Component.translatable("message.traincraft.connector.unsupported"), false);
            return InteractionResult.CONSUME;
        }

        CouplingManager.cleanupStaleLinks(selected, selectedStock);
        CouplingManager.cleanupStaleLinks(clicked, clickedStock);

        // Exact connected pair is a toggle regardless of which coupler slot each
        // vehicle uses.
        if (CouplingManager.areMutuallyLinked(selected, selectedStock, clicked, clickedStock)) {
            CouplingManager.unlinkPair(selected, selectedStock, clicked, clickedStock);
            clearSelection(connector);
            player.displayClientMessage(Component.translatable("message.traincraft.connector.uncoupled"), false);
            return InteractionResult.CONSUME;
        }

        if (!CouplingManager.canLink(selected, selectedStock, clicked, clickedStock)) {
            clearSelection(connector);
            player.displayClientMessage(Component.translatable("message.traincraft.connector.already_coupled"), false);
            return InteractionResult.CONSUME;
        }

        if (CouplingManager.link(selected, selectedStock, clicked, clickedStock)) {
            clearSelection(connector);
            player.displayClientMessage(Component.translatable("message.traincraft.connector.coupled"), false);
        }
        return InteractionResult.CONSUME;
    }

    private static boolean hasSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(SELECTED_ROLLING_STOCK_KEY);
    }

    private static UUID getSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(SELECTED_ROLLING_STOCK_KEY)
                ? tag.getUUID(SELECTED_ROLLING_STOCK_KEY)
                : null;
    }

    private static void saveSelection(ItemStack stack, UUID uuid) {
        stack.getOrCreateTag().putUUID(SELECTED_ROLLING_STOCK_KEY, uuid);
    }

    private static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            tag.remove(SELECTED_ROLLING_STOCK_KEY);
        }
    }
}
