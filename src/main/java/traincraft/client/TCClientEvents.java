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
import traincraft.client.model.recovered.*;
import traincraft.client.renderer.recovered.RecoveredPoweredLocomotiveRenderer;
import traincraft.client.renderer.recovered.RecoveredTracksBuilderRenderer;
import traincraft.client.screen.ProcessMachineScreen;
import traincraft.client.screen.TafariCraftGuideScreen;
import traincraft.client.screen.PoweredLocomotiveScreen;
import traincraft.client.renderer.recovered.ExactPoweredLocomotiveRenderer;
import traincraft.client.renderer.recovered.ExactFreightCarRenderer;
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
            MenuScreens.register(TCMenus.TRANSPORT_CAR_MANIFEST.get(), traincraft.client.screen.TransportCarScreen::new);
            MenuScreens.register(TCMenus.POWERED_LOCOMOTIVE.get(), PoweredLocomotiveScreen::new);
            MenuScreens.register(TCMenus.PROCESS_MACHINE.get(), ProcessMachineScreen::new);
            MenuScreens.register(TCMenus.GUIDE.get(), TafariCraftGuideScreen::new);
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
        event.registerLayerDefinition(RecoveredModelKof.LAYER, RecoveredModelKof::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelGP40.LAYER, RecoveredModelGP40::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelChME3.LAYER, RecoveredModelChME3::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelGP7.LAYER, RecoveredModelGP7::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelSD40.LAYER, RecoveredModelSD40::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelSD70.LAYER, RecoveredModelSD70::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelShunter.LAYER, RecoveredModelShunter::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelV60.LAYER, RecoveredModelV60::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelVL10.LAYER, RecoveredModelVL10::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelEU07.LAYER, RecoveredModelEU07::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelLocoMinetrain.LAYER, RecoveredModelLocoMinetrain::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelLocoHighSpeedZeroED.LAYER, RecoveredModelLocoHighSpeedZeroED::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelTram.LAYER, RecoveredModelTram::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelTramNY.LAYER, RecoveredModelTramNY::createBodyLayer);
        event.registerLayerDefinition(RecoveredModelBuilder.LAYER, RecoveredModelBuilder::createBodyLayer);
        event.registerLayerDefinition(RecoveredBuilderRotorModel.LAYER, RecoveredBuilderRotorModel::createBodyLayer);
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
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SMALL.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(SmallSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_HEAVY.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(HeavySteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_HEAVY.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(HeavySteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_USSR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(UssrSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_USSR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(UssrSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BR01.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Br01SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_BR01.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Br01SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_FORNEY.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ForneySteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_MOGUL.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(MogulSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SHAY.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ShaySteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_ADLER.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(AdlerSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_ADLER.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(AdlerSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BR80.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Br80SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_U57.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(U57SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_U57.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(U57SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_CHEREPANOV.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(CherepanovSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_PANNIER.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(PannierSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_CLIMAX.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ClimaxSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C41.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(C41SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_C41.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(C41SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_FOWLER.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(FowlerSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_FOWLER.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(FowlerSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SOUTHERN1102.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Southern1102SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_SOUTHERN1102.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Southern1102SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_HALL_CLASS.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(HallClassSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_KING_CLASS.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(KingClassSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_MILW_CLASS_A.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(MilwClassASteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_MILW.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(MilwSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.MILW_BAGGAGE_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(MilwBaggageCarRenderer::new));
        event.registerEntityRenderer(TCEntities.MILW_PASSENGER_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(MilwPassengerCarRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_A4_MALLARD.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(A4MallardSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_A4_MALLARD.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(A4MallardSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C62.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(C62SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_C62.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(C62SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_D51.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(D51SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_D51_LONG.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(D51LongSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_D51.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(D51SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_S100_UK.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(S100UkSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_S100_US.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(S100UsSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.MILW_TAIL_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(MilwTailCarRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GS4.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gs4SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_GS4.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gs4SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.GS4_BAGGAGE_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gs4BaggageCarRenderer::new));
        event.registerEntityRenderer(TCEntities.GS4_PASSENGER_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gs4PassengerCarRenderer::new));
        event.registerEntityRenderer(TCEntities.GS4_OBSERVATORY_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gs4ObservatoryCarRenderer::new));
        event.registerEntityRenderer(TCEntities.GS4_TAIL_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gs4TailCarRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BERKSHIRE_1225.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Berkshire1225SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BERKSHIRE_765.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Berkshire765SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_BERKSHIRE.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(BerkshireSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_4000_GALLON.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(FourThousandGallonSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_CORONATION_CLASS.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(CoronationClassSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_CORONATION_CLASS.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(CoronationClassSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_RW_TYPE_2.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(RwType2SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_RW_TYPE_2.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(RwType2SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_PE.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(PeSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_PE.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(PeSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SKOOKUM.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(SkookumSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_SKOOKUM.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(SkookumSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GWR_42XX.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gwr42xxSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GWR_72XX.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gwr72xxSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_RW_TYPE_3.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(RwType3SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GWR_101_CLASS.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Gwr101ClassSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_WWCP_062T.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Wwcp062tSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_J50.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(J50SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SENTINEL_Y3.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(SentinelY3SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_4_4_0.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(FourFourZeroSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_4_4_0.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(FourFourZeroSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_LSSP7.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Lssp7SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_ALICE_0_4_0.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Alice040SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_VB_0_4_0.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Vb040SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C41_080.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(C41080SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_ALCO_SC4.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(AlcoSc4SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C41T.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(C41TSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_VB_SHAY.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(VbShaySteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_GLYN_042T.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Glyn042tSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_262T.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(TwoSixTwoTSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SNOW_PLOW.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(SteamSnowPlowLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_VB_SHAY_2.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(VbShay2SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_CLIMAX_2.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Climax2SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_C11.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(C11SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_SHAY_3_TRUCK.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Shay3TruckSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_SHAY_3_TRUCK.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Shay3TruckSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BR_BLACK_5.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(BrBlack5SteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_BR_BLACK_5.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(BrBlack5SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_BR_BRITANNIA.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(BrBritanniaSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_BR1.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(Br1SteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_MIDLAND_COMPOUND.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(MidlandCompoundSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_MIDLAND_COMPOUND.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(MidlandCompoundSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_STEAM_STAR_CLASS.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(StarClassSteamLocomotiveRenderer::new));
        event.registerEntityRenderer(TCEntities.STEAM_TENDER_STAR_CLASS.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(StarClassSteamTenderRenderer::new));
        event.registerEntityRenderer(TCEntities.TCT_TANKER_UNIVERSAL.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(traincraft.client.renderer.TctTankerRenderer::new));
        event.registerEntityRenderer(TCEntities.TCT_TANKER_WATER.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(traincraft.client.renderer.TctTankerRenderer::new));
        event.registerEntityRenderer(TCEntities.TCT_TANKER_LAVA.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(traincraft.client.renderer.TctTankerRenderer::new));
        event.registerEntityRenderer(TCEntities.FREIGHT_CART.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(FreightCartRenderer::new));
        event.registerEntityRenderer(TCEntities.PASSENGER_COACH_BLUE.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(PassengerCoachRenderer::new));
        event.registerEntityRenderer(TCEntities.CABOOSE.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ClassicCabooseRenderer::new));
        event.registerEntityRenderer(TCEntities.LIVESTOCK_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new traincraft.client.renderer.custom.ConceptTransportCarRenderer<>(ctx, false)));
        // TC_MOB_PRISONER_CAGE_R1F18:
        // Reuse the proven Livestock barred/head-panel shell so mobs/players are
        // actually visible between bars. Entity/item identity remains Mob Transport.
        event.registerEntityRenderer(TCEntities.MOB_TRANSPORT_CAR.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new traincraft.client.renderer.custom.ConceptTransportCarRenderer<>(ctx, true)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_DIESEL_KOF.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new ExactPoweredLocomotiveRenderer<>(ctx,"kof",new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/loco_kof_db_red.png"),-1.0D,-0.44D,0D,0F,0F,0F,1F,1F,1F,1.0F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_DIESEL_CD742.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelGP40(ctx.bakeLayer(RecoveredModelGP40.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/cd742_yellow.png"),-0.8D,0.75D,0D,180F,180F,0F,0.8F,0.8F,0.8F,1.2F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_DIESEL_CHME3.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelChME3(ctx.bakeLayer(RecoveredModelChME3.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/chme3.png"),-0.5D,-0.47D,0D,0F,0F,0F,1F,1F,1F,1.1F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_DIESEL_GP7.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new ExactPoweredLocomotiveRenderer<>(ctx,"gp7",new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/gp7_red.png"),-0.8D,-0.47D,0D,0F,180F,0F,1F,1F,1F,1.2F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_DIESEL_SD40.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelSD40(ctx.bakeLayer(RecoveredModelSD40.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/loco_sd40_red.png"),-1.2D,-0.47D,0D,180F,90F,0F,-1F,-1F,1F,1.4F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_DIESEL_SD70.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelSD70(ctx.bakeLayer(RecoveredModelSD70.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/loco_sd70_orange.png"),-1.2D,-0.44D,0D,0F,90F,0F,1F,1F,1F,1.4F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_DIESEL_SHUNTER.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new ExactPoweredLocomotiveRenderer<>(ctx,"shunter",new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/shunter_blue.png"),-1.2D,-0.42D,0D,0F,0F,0F,1F,1F,1F,1.2F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_DIESEL_V60.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelV60(ctx.bakeLayer(RecoveredModelV60.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/loco_v60_db_red.png"),-0.5D,-0.44D,0D,0F,0F,0F,1F,1F,1F,1.1F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_ELECTRIC_VL10.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelVL10(ctx.bakeLayer(RecoveredModelVL10.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/vl10.png"),-1.5D,-0.47D,0D,0F,0F,0F,1F,1F,1F,1.4F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_ELECTRIC_BR_E69.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new ExactPoweredLocomotiveRenderer<>(ctx,"br_e69",new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/loco_br_e69_green.png"),0D,-0.42D,0D,0F,0F,0F,1F,1F,1F,1.0F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_ELECTRIC_MINETRAIN.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new ExactPoweredLocomotiveRenderer<>(ctx,"minetrain",new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/loco_minetrain.png"),-0.8D,-0.47D,0D,0F,0F,0F,1F,1F,1F,0.9F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_ELECTRIC_HIGH_SPEED_ZEROED.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelLocoHighSpeedZeroED(ctx.bakeLayer(RecoveredModelLocoHighSpeedZeroED.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/loco_high_speed_zeroed.png"),-1.7D,-0.44D,0D,0F,0F,0F,1F,1F,1F,1.4F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_ELECTRIC_TRAM.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelTram(ctx.bakeLayer(RecoveredModelTram.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/tram.png"),-0.8D,-0.44D,0D,0F,0F,0F,1F,1F,1F,1.1F)));
        event.registerEntityRenderer(TCEntities.LOCOMOTIVE_ELECTRIC_TRAM_NY.get(), traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(ctx -> new RecoveredPoweredLocomotiveRenderer<>(ctx,new RecoveredModelTramNY(ctx.bakeLayer(RecoveredModelTramNY.LAYER)),new net.minecraft.resources.ResourceLocation(Traincraft.MOD_ID,"textures/trains/loco_tram_ny.png"),-1.5D,-0.44D,0D,0F,0F,0F,1F,1F,1F,1.2F)));
        event.registerEntityRenderer(
                TCEntities.TRACKS_BUILDER.get(),
                traincraft.client.renderer.calibration.RollingStockCalibrationRenderer.wrap(
                        ctx -> new RecoveredTracksBuilderRenderer(
                                ctx,
                                new RecoveredModelBuilder(
                                        ctx.bakeLayer(
                                                RecoveredModelBuilder.LAYER)),
                                new RecoveredBuilderRotorModel(
                                        ctx.bakeLayer(
                                                RecoveredBuilderRotorModel.LAYER)))));
    }
}
