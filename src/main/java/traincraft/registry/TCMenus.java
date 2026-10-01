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
import traincraft.menu.GuideMenu;
import traincraft.menu.ProcessMachineMenu;
import traincraft.menu.PoweredLocomotiveMenu;
import traincraft.menu.SteamLocomotiveMenu;
import traincraft.menu.TenderMenu;
import traincraft.menu.TransportCarMenu;

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

    public static final RegistryObject<MenuType<PoweredLocomotiveMenu>> POWERED_LOCOMOTIVE =
            MENUS.register("powered_locomotive", () ->
                    new MenuType<>(PoweredLocomotiveMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<MenuType<ProcessMachineMenu>> PROCESS_MACHINE =
            MENUS.register("process_machine", () -> IForgeMenuType.create(ProcessMachineMenu::new));

    public static final RegistryObject<MenuType<GuideMenu>> GUIDE =
            MENUS.register("guide", () ->
                    new MenuType<>(GuideMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final RegistryObject<MenuType<TransportCarMenu>> TRANSPORT_CAR_MANIFEST =
            MENUS.register("transport_car_manifest", () ->
                    IForgeMenuType.create(TransportCarMenu::new));
    private TCMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
