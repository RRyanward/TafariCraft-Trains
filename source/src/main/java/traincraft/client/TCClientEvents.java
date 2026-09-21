/* Traincraft 1.20.1 client-only registration events. */
package traincraft.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import traincraft.Traincraft;
import traincraft.client.hud.TraincraftLocomotiveHud;
import traincraft.client.model.ClassicCabooseModel;
import traincraft.client.model.FreightCartModel;
import traincraft.client.model.HeavySteamLocomotiveModel;
import traincraft.client.model.HeavySteamTenderModel;
import traincraft.client.model.UssrSteamLocomotiveModel;
import traincraft.client.model.UssrSteamTenderModel;
import traincraft.client.model.Br01SteamLocomotiveModel;
import traincraft.client.model.Br01SteamTenderModel;
import traincraft.client.model.ForneySteamLocomotiveModel;
import traincraft.client.model.MogulSteamLocomotiveModel;
import traincraft.client.model.ShaySteamLocomotiveModel;
import traincraft.client.model.PassengerCoachModel;
import traincraft.client.model.SmallSteamLocomotiveModel;
import traincraft.client.model.SteamTenderModel;
import traincraft.client.renderer.ClassicCabooseRenderer;
import traincraft.client.renderer.FreightCartRenderer;
import traincraft.client.renderer.HeavySteamLocomotiveRenderer;
import traincraft.client.renderer.HeavySteamTenderRenderer;
import traincraft.client.renderer.UssrSteamLocomotiveRenderer;
import traincraft.client.renderer.UssrSteamTenderRenderer;
import traincraft.client.renderer.Br01SteamLocomotiveRenderer;
import traincraft.client.renderer.Br01SteamTenderRenderer;
import traincraft.client.renderer.ForneySteamLocomotiveRenderer;
import traincraft.client.renderer.MogulSteamLocomotiveRenderer;
import traincraft.client.renderer.ShaySteamLocomotiveRenderer;
import traincraft.client.renderer.PassengerCoachRenderer;
import traincraft.client.renderer.SmallSteamLocomotiveRenderer;
import traincraft.client.renderer.SteamTenderRenderer;
import traincraft.client.renderer.legacy.AdlerSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.AdlerSteamTenderRenderer;
import traincraft.client.renderer.legacy.Br80SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.U57SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.U57SteamTenderRenderer;
import traincraft.client.renderer.legacy.CherepanovSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.PannierSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.ClimaxSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.C41SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.C41SteamTenderRenderer;
import traincraft.client.renderer.legacy.FowlerSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.FowlerSteamTenderRenderer;
import traincraft.client.renderer.legacy.Southern1102SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Southern1102SteamTenderRenderer;
import traincraft.client.renderer.legacy.HallClassSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.KingClassSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.MilwClassASteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.MilwSteamTenderRenderer;
import traincraft.client.renderer.legacy.MilwBaggageCarRenderer;
import traincraft.client.renderer.legacy.MilwPassengerCarRenderer;
import traincraft.client.renderer.legacy.A4MallardSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.A4MallardSteamTenderRenderer;
import traincraft.client.renderer.legacy.C62SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.C62SteamTenderRenderer;
import traincraft.client.renderer.legacy.D51SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.D51LongSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.D51SteamTenderRenderer;
import traincraft.client.renderer.legacy.S100UkSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.S100UsSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.MilwTailCarRenderer;
import traincraft.client.renderer.legacy.Gs4SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Gs4SteamTenderRenderer;
import traincraft.client.renderer.legacy.Gs4BaggageCarRenderer;
import traincraft.client.renderer.legacy.Gs4PassengerCarRenderer;
import traincraft.client.renderer.legacy.Gs4ObservatoryCarRenderer;
import traincraft.client.renderer.legacy.Gs4TailCarRenderer;
import traincraft.client.renderer.legacy.Berkshire1225SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Berkshire765SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.BerkshireSteamTenderRenderer;
import traincraft.client.renderer.legacy.FourThousandGallonSteamTenderRenderer;
import traincraft.client.renderer.legacy.CoronationClassSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.CoronationClassSteamTenderRenderer;
import traincraft.client.renderer.legacy.RwType2SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.RwType2SteamTenderRenderer;
import traincraft.client.renderer.legacy.PeSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.PeSteamTenderRenderer;
import traincraft.client.renderer.legacy.SkookumSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.SkookumSteamTenderRenderer;
import traincraft.client.renderer.legacy.Gwr42xxSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Gwr72xxSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.RwType3SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Gwr101ClassSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Wwcp062tSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.J50SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.SentinelY3SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.FourFourZeroSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.FourFourZeroSteamTenderRenderer;
import traincraft.client.renderer.legacy.Lssp7SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Alice040SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Vb040SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.C41080SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.AlcoSc4SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.C41TSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.VbShaySteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Glyn042tSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.TwoSixTwoTSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.SteamSnowPlowLocomotiveRenderer;
import traincraft.client.renderer.legacy.VbShay2SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Climax2SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.C11SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Shay3TruckSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Shay3TruckSteamTenderRenderer;
import traincraft.client.renderer.legacy.BrBlack5SteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.BrBlack5SteamTenderRenderer;
import traincraft.client.renderer.legacy.BrBritanniaSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.Br1SteamTenderRenderer;
import traincraft.client.renderer.legacy.MidlandCompoundSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.MidlandCompoundSteamTenderRenderer;
import traincraft.client.renderer.legacy.StarClassSteamLocomotiveRenderer;
import traincraft.client.renderer.legacy.StarClassSteamTenderRenderer;
import traincraft.client.screen.FreightCartScreen;
import traincraft.client.screen.SteamLocomotiveScreen;
import traincraft.registry.TCEntities;
import traincraft.registry.TCMenus;

@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TCClientEvents {
    private TCClientEvents() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(TCMenus.STEAM_LOCOMOTIVE.get(), SteamLocomotiveScreen::new);
            MenuScreens.register(TCMenus.FREIGHT_CART.get(), FreightCartScreen::new);
        });
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("locomotive_hud", TraincraftLocomotiveHud.OVERLAY);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TCKeyMappings.OPEN_LOCOMOTIVE_GUI);
        event.register(TCKeyMappings.HAND_BRAKE);
        event.register(TCKeyMappings.WHISTLE);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SmallSteamLocomotiveModel.LAYER, SmallSteamLocomotiveModel::createBodyLayer);
        event.registerLayerDefinition(SteamTenderModel.LAYER, SteamTenderModel::createBodyLayer);
        event.registerLayerDefinition(HeavySteamLocomotiveModel.LAYER, HeavySteamLocomotiveModel::createBodyLayer);
        event.registerLayerDefinition(HeavySteamTenderModel.LAYER, HeavySteamTenderModel::createBodyLayer);
        event.registerLayerDefinition(UssrSteamLocomotiveModel.LAYER, UssrSteamLocomotiveModel::createBodyLayer);
        event.registerLayerDefinition(UssrSteamTenderModel.LAYER, UssrSteamTenderModel::createBodyLayer);
        event.registerLayerDefinition(Br01SteamLocomotiveModel.LAYER, Br01SteamLocomotiveModel::createBodyLayer);
        event.registerLayerDefinition(Br01SteamTenderModel.LAYER, Br01SteamTenderModel::createBodyLayer);
        event.registerLayerDefinition(ForneySteamLocomotiveModel.LAYER, ForneySteamLocomotiveModel::createBodyLayer);
        event.registerLayerDefinition(MogulSteamLocomotiveModel.LAYER, MogulSteamLocomotiveModel::createBodyLayer);
        event.registerLayerDefinition(ShaySteamLocomotiveModel.LAYER, ShaySteamLocomotiveModel::createBodyLayer);
        event.registerLayerDefinition(FreightCartModel.LAYER, FreightCartModel::createBodyLayer);
        event.registerLayerDefinition(PassengerCoachModel.LAYER, PassengerCoachModel::createBodyLayer);
        event.registerLayerDefinition(ClassicCabooseModel.LAYER, ClassicCabooseModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SMALL.get(), SmallSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER.get(), SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_HEAVY.get(), HeavySteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_HEAVY.get(), HeavySteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_USSR.get(), UssrSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_USSR.get(), UssrSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BR01.get(), Br01SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_BR01.get(), Br01SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_FORNEY.get(), ForneySteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_MOGUL.get(), MogulSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SHAY.get(), ShaySteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_ADLER.get(), AdlerSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_ADLER.get(), AdlerSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BR80.get(), Br80SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_U57.get(), U57SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_U57.get(), U57SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_CHEREPANOV.get(), CherepanovSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_PANNIER.get(), PannierSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_CLIMAX.get(), ClimaxSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C41.get(), C41SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_C41.get(), C41SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_FOWLER.get(), FowlerSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_FOWLER.get(), FowlerSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SOUTHERN1102.get(), Southern1102SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_SOUTHERN1102.get(), Southern1102SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_HALL_CLASS.get(), HallClassSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_KING_CLASS.get(), KingClassSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_MILW_CLASS_A.get(), MilwClassASteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_MILW.get(), MilwSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.MILW_BAGGAGE_CAR.get(), MilwBaggageCarRenderer::new);
        event.registerEntityRenderer(TCEntities.MILW_PASSENGER_CAR.get(), MilwPassengerCarRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_A4_MALLARD.get(), A4MallardSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_A4_MALLARD.get(), A4MallardSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C62.get(), C62SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_C62.get(), C62SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_D51.get(), D51SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_D51_LONG.get(), D51LongSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_D51.get(), D51SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_S100_UK.get(), S100UkSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_S100_US.get(), S100UsSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.MILW_TAIL_CAR.get(), MilwTailCarRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GS4.get(), Gs4SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_GS4.get(), Gs4SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.GS4_BAGGAGE_CAR.get(), Gs4BaggageCarRenderer::new);
        event.registerEntityRenderer(TCEntities.GS4_PASSENGER_CAR.get(), Gs4PassengerCarRenderer::new);
        event.registerEntityRenderer(TCEntities.GS4_OBSERVATORY_CAR.get(), Gs4ObservatoryCarRenderer::new);
        event.registerEntityRenderer(TCEntities.GS4_TAIL_CAR.get(), Gs4TailCarRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BERKSHIRE_1225.get(), Berkshire1225SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BERKSHIRE_765.get(), Berkshire765SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_BERKSHIRE.get(), BerkshireSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_4000_GALLON.get(), FourThousandGallonSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_CORONATION_CLASS.get(), CoronationClassSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_CORONATION_CLASS.get(), CoronationClassSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_RW_TYPE_2.get(), RwType2SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_RW_TYPE_2.get(), RwType2SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_PE.get(), PeSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_PE.get(), PeSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SKOOKUM.get(), SkookumSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_SKOOKUM.get(), SkookumSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GWR_42XX.get(), Gwr42xxSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GWR_72XX.get(), Gwr72xxSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_RW_TYPE_3.get(), RwType3SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GWR_101_CLASS.get(), Gwr101ClassSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_WWCP_062T.get(), Wwcp062tSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_J50.get(), J50SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SENTINEL_Y3.get(), SentinelY3SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_4_4_0.get(), FourFourZeroSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_4_4_0.get(), FourFourZeroSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_LSSP7.get(), Lssp7SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_ALICE_0_4_0.get(), Alice040SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_VB_0_4_0.get(), Vb040SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C41_080.get(), C41080SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_ALCO_SC4.get(), AlcoSc4SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C41T.get(), C41TSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_VB_SHAY.get(), VbShaySteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GLYN_042T.get(), Glyn042tSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_262T.get(), TwoSixTwoTSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SNOW_PLOW.get(), SteamSnowPlowLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_VB_SHAY_2.get(), VbShay2SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_CLIMAX_2.get(), Climax2SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C11.get(), C11SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SHAY_3_TRUCK.get(), Shay3TruckSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_SHAY_3_TRUCK.get(), Shay3TruckSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BR_BLACK_5.get(), BrBlack5SteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_BR_BLACK_5.get(), BrBlack5SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BR_BRITANNIA.get(), BrBritanniaSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_BR1.get(), Br1SteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_MIDLAND_COMPOUND.get(), MidlandCompoundSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_MIDLAND_COMPOUND.get(), MidlandCompoundSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_STAR_CLASS.get(), StarClassSteamLocomotiveRenderer::new);
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_STAR_CLASS.get(), StarClassSteamTenderRenderer::new);
        event.registerEntityRenderer(TCEntities.FREIGHT_CART.get(), FreightCartRenderer::new);
        event.registerEntityRenderer(TCEntities.PASSENGER_COACH_BLUE.get(), PassengerCoachRenderer::new);
        event.registerEntityRenderer(TCEntities.CABOOSE.get(), ClassicCabooseRenderer::new);
    }
}
