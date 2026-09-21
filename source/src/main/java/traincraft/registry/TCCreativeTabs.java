/*
 * Traincraft 1.20.1 creative tab.
 * Distributed under LGPL-v3.0 with the original project.
 */
package traincraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;

public final class TCCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Traincraft.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TRAINCRAFT = TABS.register("traincraft", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.traincraft"))
                    .icon(() -> new ItemStack(TCItems.WRENCH.get()))
                    .displayItems((parameters, output) -> {
                        TCItems.creativeItems().forEach(item -> output.accept(item.get()));
                        TCBlocks.BLOCK_ITEMS.values().forEach(item -> output.accept(item.get()));
                    })
                    .build());

    private TCCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
