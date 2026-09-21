/* Traincraft 1.20.1 menu registry. */
package traincraft.registry;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;
import traincraft.menu.FreightCartMenu;
import traincraft.menu.SteamLocomotiveMenu;
import traincraft.menu.TenderMenu;

public final class TCMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Traincraft.MOD_ID);

    public static final RegistryObject<MenuType<SteamLocomotiveMenu>> STEAM_LOCOMOTIVE =
            MENUS.register("steam_locomotive", () ->
                    new MenuType<>(SteamLocomotiveMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<MenuType<TenderMenu>> TENDER =
            MENUS.register("tender", () -> IForgeMenuType.create(TenderMenu::new));

    public static final RegistryObject<MenuType<FreightCartMenu>> FREIGHT_CART =
            MENUS.register("freight_cart", () -> IForgeMenuType.create(FreightCartMenu::new));

    private TCMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
