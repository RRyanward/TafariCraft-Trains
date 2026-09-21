/*
 * Traincraft
 * Copyright (c) 2011-2020.
 *
 * This file is part of the Traincraft 1.20.1 port.
 * Original project: https://github.com/Traincraft/Traincraft
 * Distributed under LGPL-v3.0.
 */
package traincraft;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import traincraft.network.TCNetwork;
import traincraft.registry.TCBlocks;
import traincraft.registry.TCCreativeTabs;
import traincraft.registry.TCEntities;
import traincraft.registry.TCMenus;
import traincraft.registry.TCSounds;

@Mod(Traincraft.MOD_ID)
public final class Traincraft {
    public static final String MOD_ID = "traincraft";
    public static final String MOD_NAME = "Traincraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Traincraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        TCBlocks.register(modBus);
        TCEntities.register(modBus);
        TCMenus.register(modBus);
        TCSounds.register(modBus);
        TCCreativeTabs.register(modBus);
        TCNetwork.register();

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Starting {} 1.20.1 port with steam boiler/fuel system", MOD_NAME);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("{} server initialization", MOD_NAME);
    }
}
