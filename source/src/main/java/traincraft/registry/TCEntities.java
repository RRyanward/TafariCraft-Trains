/*
 * Traincraft 1.20.1 entity registry.
 * Original project: https://github.com/Traincraft/Traincraft
 * Distributed under LGPL-v3.0.
 */
package traincraft.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import traincraft.Traincraft;
import traincraft.entity.train.caboose.ClassicCaboose;
import traincraft.entity.train.freight.FreightCart;
import traincraft.entity.train.passenger.PassengerCoachBlue;
import traincraft.entity.train.steam.small.SmallSteamLocomotive;
import traincraft.entity.train.steam.heavy.HeavySteamLocomotive;
import traincraft.entity.train.steam.ussr.UssrSteamLocomotive;
import traincraft.entity.train.steam.classics.Br01SteamLocomotive;
import traincraft.entity.train.steam.classics.ForneySteamLocomotive;
import traincraft.entity.train.steam.classics.MogulSteamLocomotive;
import traincraft.entity.train.steam.classics.ShaySteamLocomotive;
import traincraft.entity.train.tender.SteamTender;
import traincraft.entity.train.tender.HeavySteamTender;
import traincraft.entity.train.tender.UssrSteamTender;
import traincraft.entity.train.tender.Br01SteamTender;
import traincraft.entity.train.steam.legacy.AdlerSteamLocomotive;
import traincraft.entity.train.steam.legacy.Br80SteamLocomotive;
import traincraft.entity.train.steam.legacy.U57SteamLocomotive;
import traincraft.entity.train.steam.legacy.CherepanovSteamLocomotive;
import traincraft.entity.train.steam.legacy.PannierSteamLocomotive;
import traincraft.entity.train.steam.legacy.ClimaxSteamLocomotive;
import traincraft.entity.train.steam.legacy.C41SteamLocomotive;
import traincraft.entity.train.tender.AdlerSteamTender;
import traincraft.entity.train.tender.U57SteamTender;
import traincraft.entity.train.tender.C41SteamTender;
import traincraft.entity.train.steam.legacy.FowlerSteamLocomotive;
import traincraft.entity.train.steam.legacy.Southern1102SteamLocomotive;
import traincraft.entity.train.steam.legacy.HallClassSteamLocomotive;
import traincraft.entity.train.steam.legacy.KingClassSteamLocomotive;
import traincraft.entity.train.steam.legacy.MilwClassASteamLocomotive;
import traincraft.entity.train.tender.FowlerSteamTender;
import traincraft.entity.train.tender.Southern1102SteamTender;
import traincraft.entity.train.tender.MilwSteamTender;
import traincraft.entity.train.freight.MilwBaggageCar;
import traincraft.entity.train.passenger.MilwPassengerCar;
import traincraft.entity.train.steam.legacy.A4MallardSteamLocomotive;
import traincraft.entity.train.tender.A4MallardSteamTender;
import traincraft.entity.train.steam.legacy.C62SteamLocomotive;
import traincraft.entity.train.tender.C62SteamTender;
import traincraft.entity.train.steam.legacy.D51SteamLocomotive;
import traincraft.entity.train.steam.legacy.D51LongSteamLocomotive;
import traincraft.entity.train.tender.D51SteamTender;
import traincraft.entity.train.steam.legacy.S100UkSteamLocomotive;
import traincraft.entity.train.steam.legacy.S100UsSteamLocomotive;
import traincraft.entity.train.passenger.MilwTailCar;
import traincraft.entity.train.steam.legacy.Gs4SteamLocomotive;
import traincraft.entity.train.tender.Gs4SteamTender;
import traincraft.entity.train.freight.Gs4BaggageCar;
import traincraft.entity.train.passenger.Gs4PassengerCar;
import traincraft.entity.train.passenger.Gs4ObservatoryCar;
import traincraft.entity.train.passenger.Gs4TailCar;
import traincraft.entity.train.steam.legacy.Berkshire1225SteamLocomotive;
import traincraft.entity.train.steam.legacy.Berkshire765SteamLocomotive;
import traincraft.entity.train.tender.BerkshireSteamTender;
import traincraft.entity.train.tender.FourThousandGallonSteamTender;
import traincraft.entity.train.steam.legacy.CoronationClassSteamLocomotive;
import traincraft.entity.train.tender.CoronationClassSteamTender;
import traincraft.entity.train.steam.legacy.RwType2SteamLocomotive;
import traincraft.entity.train.tender.RwType2SteamTender;
import traincraft.entity.train.steam.legacy.PeSteamLocomotive;
import traincraft.entity.train.tender.PeSteamTender;
import traincraft.entity.train.steam.legacy.SkookumSteamLocomotive;
import traincraft.entity.train.tender.SkookumSteamTender;
import traincraft.entity.train.steam.legacy.Gwr42xxSteamLocomotive;
import traincraft.entity.train.steam.legacy.Gwr72xxSteamLocomotive;
import traincraft.entity.train.steam.legacy.RwType3SteamLocomotive;
import traincraft.entity.train.steam.legacy.Gwr101ClassSteamLocomotive;
import traincraft.entity.train.steam.legacy.Wwcp062tSteamLocomotive;
import traincraft.entity.train.steam.legacy.J50SteamLocomotive;
import traincraft.entity.train.steam.legacy.SentinelY3SteamLocomotive;
import traincraft.entity.train.steam.legacy.FourFourZeroSteamLocomotive;
import traincraft.entity.train.tender.FourFourZeroSteamTender;
import traincraft.entity.train.steam.legacy.Lssp7SteamLocomotive;
import traincraft.entity.train.steam.legacy.Alice040SteamLocomotive;
import traincraft.entity.train.steam.legacy.Vb040SteamLocomotive;
import traincraft.entity.train.steam.legacy.C41080SteamLocomotive;
import traincraft.entity.train.steam.legacy.AlcoSc4SteamLocomotive;
import traincraft.entity.train.steam.legacy.C41TSteamLocomotive;
import traincraft.entity.train.steam.legacy.VbShaySteamLocomotive;
import traincraft.entity.train.steam.legacy.Glyn042tSteamLocomotive;
import traincraft.entity.train.steam.legacy.TwoSixTwoTSteamLocomotive;
import traincraft.entity.train.steam.legacy.SteamSnowPlowLocomotive;
import traincraft.entity.train.steam.legacy.VbShay2SteamLocomotive;
import traincraft.entity.train.steam.legacy.Climax2SteamLocomotive;
import traincraft.entity.train.steam.legacy.C11SteamLocomotive;
import traincraft.entity.train.steam.legacy.Shay3TruckSteamLocomotive;
import traincraft.entity.train.tender.Shay3TruckSteamTender;
import traincraft.entity.train.steam.legacy.BrBlack5SteamLocomotive;
import traincraft.entity.train.tender.BrBlack5SteamTender;
import traincraft.entity.train.steam.legacy.BrBritanniaSteamLocomotive;
import traincraft.entity.train.tender.Br1SteamTender;
import traincraft.entity.train.steam.legacy.MidlandCompoundSteamLocomotive;
import traincraft.entity.train.tender.MidlandCompoundSteamTender;
import traincraft.entity.train.steam.legacy.StarClassSteamLocomotive;
import traincraft.entity.train.tender.StarClassSteamTender;

public final class TCEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Traincraft.MOD_ID);

    /**
     * First rolling-stock type in the 1.20.1 port.
     * Dimensions preserve the original Traincraft definition as closely as
     * Minecraft's width/height entity box permits.
     */
    public static final RegistryObject<EntityType<SmallSteamLocomotive>> LOCOMOTIVE_STEAM_SMALL =
            ENTITY_TYPES.register("locomotive_steam_small", () ->
                    EntityType.Builder.<SmallSteamLocomotive>of(SmallSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.856F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_small"));

    /** Step 8.1.0 legacy Heavy Steam locomotive foundation. */
    public static final RegistryObject<EntityType<HeavySteamLocomotive>> LOCOMOTIVE_STEAM_HEAVY =
            ENTITY_TYPES.register("locomotive_steam_heavy", () ->
                    EntityType.Builder.<HeavySteamLocomotive>of(HeavySteamLocomotive::new, MobCategory.MISC)
                            .sized(1.10F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_heavy"));

    /** Step 8.2.1 production USSR 0-5-0 steam locomotive foundation. */
    public static final RegistryObject<EntityType<UssrSteamLocomotive>> LOCOMOTIVE_STEAM_USSR =
            ENTITY_TYPES.register("locomotive_steam_ussr", () ->
                    EntityType.Builder.<UssrSteamLocomotive>of(UssrSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_ussr"));

    /** Step 7.0 classic steam tender used by the first physical coupling system. */
    public static final RegistryObject<EntityType<SteamTender>> STEAM_TENDER =
            ENTITY_TYPES.register("steam_tender", () ->
                    EntityType.Builder.<SteamTender>of(SteamTender::new, MobCategory.MISC)
                            .sized(0.96F, 1.20F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender"));

    /** Step 8.1.0 matching legacy Heavy Steam tender foundation. */
    public static final RegistryObject<EntityType<HeavySteamTender>> STEAM_TENDER_HEAVY =
            ENTITY_TYPES.register("steam_tender_heavy", () ->
                    EntityType.Builder.<HeavySteamTender>of(HeavySteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_heavy"));

    /** Step 8.2.1 matching USSR 0-5-0 tender foundation. */
    public static final RegistryObject<EntityType<UssrSteamTender>> STEAM_TENDER_USSR =
            ENTITY_TYPES.register("steam_tender_ussr", () ->
                    EntityType.Builder.<UssrSteamTender>of(UssrSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_ussr"));

    /** Step 8.3.0 production BR01 Steam Locomotive visual foundation. */
    public static final RegistryObject<EntityType<Br01SteamLocomotive>> LOCOMOTIVE_STEAM_BR01 =
            ENTITY_TYPES.register("locomotive_steam_br01", () ->
                    EntityType.Builder.<Br01SteamLocomotive>of(Br01SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_br01"));

    /** Step 8.3.0 production BR01 Tender visual foundation. */
    public static final RegistryObject<EntityType<Br01SteamTender>> STEAM_TENDER_BR01 =
            ENTITY_TYPES.register("steam_tender_br01", () ->
                    EntityType.Builder.<Br01SteamTender>of(Br01SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_br01"));

    /** Step 8.3.0 production Forney Steam Locomotive visual foundation. */
    public static final RegistryObject<EntityType<ForneySteamLocomotive>> LOCOMOTIVE_STEAM_FORNEY =
            ENTITY_TYPES.register("locomotive_steam_forney", () ->
                    EntityType.Builder.<ForneySteamLocomotive>of(ForneySteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_forney"));

    /** Step 8.3.0 production Mogul Steam Locomotive visual foundation. */
    public static final RegistryObject<EntityType<MogulSteamLocomotive>> LOCOMOTIVE_STEAM_MOGUL =
            ENTITY_TYPES.register("locomotive_steam_mogul", () ->
                    EntityType.Builder.<MogulSteamLocomotive>of(MogulSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_mogul"));

    /** Step 8.3.0 production Shay Steam Locomotive visual foundation. */
    public static final RegistryObject<EntityType<ShaySteamLocomotive>> LOCOMOTIVE_STEAM_SHAY =
            ENTITY_TYPES.register("locomotive_steam_shay", () ->
                    EntityType.Builder.<ShaySteamLocomotive>of(ShaySteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_shay"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<AdlerSteamLocomotive>> LOCOMOTIVE_STEAM_ADLER =
            ENTITY_TYPES.register("locomotive_steam_adler", () ->
                    EntityType.Builder.<AdlerSteamLocomotive>of(AdlerSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_adler"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<AdlerSteamTender>> STEAM_TENDER_ADLER =
            ENTITY_TYPES.register("steam_tender_adler", () ->
                    EntityType.Builder.<AdlerSteamTender>of(AdlerSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_adler"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<Br80SteamLocomotive>> LOCOMOTIVE_STEAM_BR80 =
            ENTITY_TYPES.register("locomotive_steam_br80", () ->
                    EntityType.Builder.<Br80SteamLocomotive>of(Br80SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_br80"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<U57SteamLocomotive>> LOCOMOTIVE_STEAM_U57 =
            ENTITY_TYPES.register("locomotive_steam_u57", () ->
                    EntityType.Builder.<U57SteamLocomotive>of(U57SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_u57"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<U57SteamTender>> STEAM_TENDER_U57 =
            ENTITY_TYPES.register("steam_tender_u57", () ->
                    EntityType.Builder.<U57SteamTender>of(U57SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_u57"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<CherepanovSteamLocomotive>> LOCOMOTIVE_STEAM_CHEREPANOV =
            ENTITY_TYPES.register("locomotive_steam_cherepanov", () ->
                    EntityType.Builder.<CherepanovSteamLocomotive>of(CherepanovSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_cherepanov"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<PannierSteamLocomotive>> LOCOMOTIVE_STEAM_PANNIER =
            ENTITY_TYPES.register("locomotive_steam_pannier", () ->
                    EntityType.Builder.<PannierSteamLocomotive>of(PannierSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_pannier"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<ClimaxSteamLocomotive>> LOCOMOTIVE_STEAM_CLIMAX =
            ENTITY_TYPES.register("locomotive_steam_climax", () ->
                    EntityType.Builder.<ClimaxSteamLocomotive>of(ClimaxSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_climax"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<C41SteamLocomotive>> LOCOMOTIVE_STEAM_C41 =
            ENTITY_TYPES.register("locomotive_steam_c41", () ->
                    EntityType.Builder.<C41SteamLocomotive>of(C41SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_c41"));

    /** Step 8.4.0 Batch B1 legacy roster. */
    public static final RegistryObject<EntityType<C41SteamTender>> STEAM_TENDER_C41 =
            ENTITY_TYPES.register("steam_tender_c41", () ->
                    EntityType.Builder.<C41SteamTender>of(C41SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_c41"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<FowlerSteamLocomotive>> LOCOMOTIVE_STEAM_FOWLER =
            ENTITY_TYPES.register("locomotive_steam_fowler", () ->
                    EntityType.Builder.<FowlerSteamLocomotive>of(FowlerSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_fowler"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<FowlerSteamTender>> STEAM_TENDER_FOWLER =
            ENTITY_TYPES.register("steam_tender_fowler", () ->
                    EntityType.Builder.<FowlerSteamTender>of(FowlerSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_fowler"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<Southern1102SteamLocomotive>> LOCOMOTIVE_STEAM_SOUTHERN1102 =
            ENTITY_TYPES.register("locomotive_steam_southern1102", () ->
                    EntityType.Builder.<Southern1102SteamLocomotive>of(Southern1102SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_southern1102"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<Southern1102SteamTender>> STEAM_TENDER_SOUTHERN1102 =
            ENTITY_TYPES.register("steam_tender_southern1102", () ->
                    EntityType.Builder.<Southern1102SteamTender>of(Southern1102SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_southern1102"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<HallClassSteamLocomotive>> LOCOMOTIVE_STEAM_HALL_CLASS =
            ENTITY_TYPES.register("locomotive_steam_hall_class", () ->
                    EntityType.Builder.<HallClassSteamLocomotive>of(HallClassSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_hall_class"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<KingClassSteamLocomotive>> LOCOMOTIVE_STEAM_KING_CLASS =
            ENTITY_TYPES.register("locomotive_steam_king_class", () ->
                    EntityType.Builder.<KingClassSteamLocomotive>of(KingClassSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_king_class"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<MilwClassASteamLocomotive>> LOCOMOTIVE_STEAM_MILW_CLASS_A =
            ENTITY_TYPES.register("locomotive_steam_milw_class_a", () ->
                    EntityType.Builder.<MilwClassASteamLocomotive>of(MilwClassASteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_milw_class_a"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<MilwSteamTender>> STEAM_TENDER_MILW =
            ENTITY_TYPES.register("steam_tender_milw", () ->
                    EntityType.Builder.<MilwSteamTender>of(MilwSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_milw"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<MilwBaggageCar>> MILW_BAGGAGE_CAR =
            ENTITY_TYPES.register("milw_baggage_car", () ->
                    EntityType.Builder.<MilwBaggageCar>of(MilwBaggageCar::new, MobCategory.MISC)
                            .sized(0.98F, 1.60F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":milw_baggage_car"));

    /** Step 8.4.1 Batch B2 legacy roster. */
    public static final RegistryObject<EntityType<MilwPassengerCar>> MILW_PASSENGER_CAR =
            ENTITY_TYPES.register("milw_passenger_car", () ->
                    EntityType.Builder.<MilwPassengerCar>of(MilwPassengerCar::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":milw_passenger_car"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<A4MallardSteamLocomotive>> LOCOMOTIVE_STEAM_A4_MALLARD =
            ENTITY_TYPES.register("locomotive_steam_a4_mallard", () ->
                    EntityType.Builder.<A4MallardSteamLocomotive>of(A4MallardSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_a4_mallard"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<A4MallardSteamTender>> STEAM_TENDER_A4_MALLARD =
            ENTITY_TYPES.register("steam_tender_a4_mallard", () ->
                    EntityType.Builder.<A4MallardSteamTender>of(A4MallardSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_a4_mallard"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<C62SteamLocomotive>> LOCOMOTIVE_STEAM_C62 =
            ENTITY_TYPES.register("locomotive_steam_c62", () ->
                    EntityType.Builder.<C62SteamLocomotive>of(C62SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_c62"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<C62SteamTender>> STEAM_TENDER_C62 =
            ENTITY_TYPES.register("steam_tender_c62", () ->
                    EntityType.Builder.<C62SteamTender>of(C62SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_c62"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<D51SteamLocomotive>> LOCOMOTIVE_STEAM_D51 =
            ENTITY_TYPES.register("locomotive_steam_d51", () ->
                    EntityType.Builder.<D51SteamLocomotive>of(D51SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_d51"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<D51LongSteamLocomotive>> LOCOMOTIVE_STEAM_D51_LONG =
            ENTITY_TYPES.register("locomotive_steam_d51_long", () ->
                    EntityType.Builder.<D51LongSteamLocomotive>of(D51LongSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_d51_long"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<D51SteamTender>> STEAM_TENDER_D51 =
            ENTITY_TYPES.register("steam_tender_d51", () ->
                    EntityType.Builder.<D51SteamTender>of(D51SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_d51"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<S100UkSteamLocomotive>> LOCOMOTIVE_STEAM_S100_UK =
            ENTITY_TYPES.register("locomotive_steam_s100_uk", () ->
                    EntityType.Builder.<S100UkSteamLocomotive>of(S100UkSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_s100_uk"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<S100UsSteamLocomotive>> LOCOMOTIVE_STEAM_S100_US =
            ENTITY_TYPES.register("locomotive_steam_s100_us", () ->
                    EntityType.Builder.<S100UsSteamLocomotive>of(S100UsSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_s100_us"));

    /** Step 8.4.2 Batch B3 legacy roster. */
    public static final RegistryObject<EntityType<MilwTailCar>> MILW_TAIL_CAR =
            ENTITY_TYPES.register("milw_tail_car", () ->
                    EntityType.Builder.<MilwTailCar>of(MilwTailCar::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":milw_tail_car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<Gs4SteamLocomotive>> LOCOMOTIVE_STEAM_GS4 =
            ENTITY_TYPES.register("locomotive_steam_gs4", () ->
                    EntityType.Builder.<Gs4SteamLocomotive>of(Gs4SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_gs4"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<Gs4SteamTender>> STEAM_TENDER_GS4 =
            ENTITY_TYPES.register("steam_tender_gs4", () ->
                    EntityType.Builder.<Gs4SteamTender>of(Gs4SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_gs4"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<Gs4BaggageCar>> GS4_BAGGAGE_CAR =
            ENTITY_TYPES.register("gs4_baggage_car", () ->
                    EntityType.Builder.<Gs4BaggageCar>of(Gs4BaggageCar::new, MobCategory.MISC)
                            .sized(0.98F, 1.60F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":gs4_baggage_car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<Gs4PassengerCar>> GS4_PASSENGER_CAR =
            ENTITY_TYPES.register("gs4_passenger_car", () ->
                    EntityType.Builder.<Gs4PassengerCar>of(Gs4PassengerCar::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":gs4_passenger_car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<Gs4ObservatoryCar>> GS4_OBSERVATORY_CAR =
            ENTITY_TYPES.register("gs4_observatory_car", () ->
                    EntityType.Builder.<Gs4ObservatoryCar>of(Gs4ObservatoryCar::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":gs4_observatory_car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<Gs4TailCar>> GS4_TAIL_CAR =
            ENTITY_TYPES.register("gs4_tail_car", () ->
                    EntityType.Builder.<Gs4TailCar>of(Gs4TailCar::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":gs4_tail_car"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<Berkshire1225SteamLocomotive>> LOCOMOTIVE_STEAM_BERKSHIRE_1225 =
            ENTITY_TYPES.register("locomotive_steam_berkshire_1225", () ->
                    EntityType.Builder.<Berkshire1225SteamLocomotive>of(Berkshire1225SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_berkshire_1225"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<Berkshire765SteamLocomotive>> LOCOMOTIVE_STEAM_BERKSHIRE_765 =
            ENTITY_TYPES.register("locomotive_steam_berkshire_765", () ->
                    EntityType.Builder.<Berkshire765SteamLocomotive>of(Berkshire765SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_berkshire_765"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<BerkshireSteamTender>> STEAM_TENDER_BERKSHIRE =
            ENTITY_TYPES.register("steam_tender_berkshire", () ->
                    EntityType.Builder.<BerkshireSteamTender>of(BerkshireSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_berkshire"));

    /** Step 8.4.3 Batch B4 legacy roster. */
    public static final RegistryObject<EntityType<FourThousandGallonSteamTender>> STEAM_TENDER_4000_GALLON =
            ENTITY_TYPES.register("steam_tender_4000_gallon", () ->
                    EntityType.Builder.<FourThousandGallonSteamTender>of(FourThousandGallonSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_4000_gallon"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<Shay3TruckSteamLocomotive>> LOCOMOTIVE_STEAM_SHAY_3_TRUCK =
            ENTITY_TYPES.register("locomotive_steam_shay_3_truck", () ->
                    EntityType.Builder.<Shay3TruckSteamLocomotive>of(Shay3TruckSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_shay_3_truck"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<Shay3TruckSteamTender>> STEAM_TENDER_SHAY_3_TRUCK =
            ENTITY_TYPES.register("steam_tender_shay_3_truck", () ->
                    EntityType.Builder.<Shay3TruckSteamTender>of(Shay3TruckSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_shay_3_truck"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<BrBlack5SteamLocomotive>> LOCOMOTIVE_STEAM_BR_BLACK_5 =
            ENTITY_TYPES.register("locomotive_steam_br_black_5", () ->
                    EntityType.Builder.<BrBlack5SteamLocomotive>of(BrBlack5SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_br_black_5"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<BrBlack5SteamTender>> STEAM_TENDER_BR_BLACK_5 =
            ENTITY_TYPES.register("steam_tender_br_black_5", () ->
                    EntityType.Builder.<BrBlack5SteamTender>of(BrBlack5SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_br_black_5"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<BrBritanniaSteamLocomotive>> LOCOMOTIVE_STEAM_BR_BRITANNIA =
            ENTITY_TYPES.register("locomotive_steam_br_britannia", () ->
                    EntityType.Builder.<BrBritanniaSteamLocomotive>of(BrBritanniaSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_br_britannia"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<Br1SteamTender>> STEAM_TENDER_BR1 =
            ENTITY_TYPES.register("steam_tender_br1", () ->
                    EntityType.Builder.<Br1SteamTender>of(Br1SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_br1"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<MidlandCompoundSteamLocomotive>> LOCOMOTIVE_STEAM_MIDLAND_COMPOUND =
            ENTITY_TYPES.register("locomotive_steam_midland_compound", () ->
                    EntityType.Builder.<MidlandCompoundSteamLocomotive>of(MidlandCompoundSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_midland_compound"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<MidlandCompoundSteamTender>> STEAM_TENDER_MIDLAND_COMPOUND =
            ENTITY_TYPES.register("steam_tender_midland_compound", () ->
                    EntityType.Builder.<MidlandCompoundSteamTender>of(MidlandCompoundSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_midland_compound"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<StarClassSteamLocomotive>> LOCOMOTIVE_STEAM_STAR_CLASS =
            ENTITY_TYPES.register("locomotive_steam_star_class", () ->
                    EntityType.Builder.<StarClassSteamLocomotive>of(StarClassSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_star_class"));

    /** Step 8.4.4 Batch B5 legacy roster. */
    public static final RegistryObject<EntityType<StarClassSteamTender>> STEAM_TENDER_STAR_CLASS =
            ENTITY_TYPES.register("steam_tender_star_class", () ->
                    EntityType.Builder.<StarClassSteamTender>of(StarClassSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_star_class"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<CoronationClassSteamLocomotive>> LOCOMOTIVE_STEAM_CORONATION_CLASS =
            ENTITY_TYPES.register("locomotive_steam_coronation_class", () ->
                    EntityType.Builder.<CoronationClassSteamLocomotive>of(CoronationClassSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_coronation_class"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<CoronationClassSteamTender>> STEAM_TENDER_CORONATION_CLASS =
            ENTITY_TYPES.register("steam_tender_coronation_class", () ->
                    EntityType.Builder.<CoronationClassSteamTender>of(CoronationClassSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_coronation_class"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<RwType2SteamLocomotive>> LOCOMOTIVE_STEAM_RW_TYPE_2 =
            ENTITY_TYPES.register("locomotive_steam_rw_type_2", () ->
                    EntityType.Builder.<RwType2SteamLocomotive>of(RwType2SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_rw_type_2"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<RwType2SteamTender>> STEAM_TENDER_RW_TYPE_2 =
            ENTITY_TYPES.register("steam_tender_rw_type_2", () ->
                    EntityType.Builder.<RwType2SteamTender>of(RwType2SteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_rw_type_2"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<PeSteamLocomotive>> LOCOMOTIVE_STEAM_PE =
            ENTITY_TYPES.register("locomotive_steam_pe", () ->
                    EntityType.Builder.<PeSteamLocomotive>of(PeSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_pe"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<PeSteamTender>> STEAM_TENDER_PE =
            ENTITY_TYPES.register("steam_tender_pe", () ->
                    EntityType.Builder.<PeSteamTender>of(PeSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_pe"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<SkookumSteamLocomotive>> LOCOMOTIVE_STEAM_SKOOKUM =
            ENTITY_TYPES.register("locomotive_steam_skookum", () ->
                    EntityType.Builder.<SkookumSteamLocomotive>of(SkookumSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_skookum"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<SkookumSteamTender>> STEAM_TENDER_SKOOKUM =
            ENTITY_TYPES.register("steam_tender_skookum", () ->
                    EntityType.Builder.<SkookumSteamTender>of(SkookumSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_skookum"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<Gwr42xxSteamLocomotive>> LOCOMOTIVE_STEAM_GWR_42XX =
            ENTITY_TYPES.register("locomotive_steam_gwr_42xx", () ->
                    EntityType.Builder.<Gwr42xxSteamLocomotive>of(Gwr42xxSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_gwr_42xx"));

    /** Step 8.4.5 Batch B6 legacy roster. */
    public static final RegistryObject<EntityType<Gwr72xxSteamLocomotive>> LOCOMOTIVE_STEAM_GWR_72XX =
            ENTITY_TYPES.register("locomotive_steam_gwr_72xx", () ->
                    EntityType.Builder.<Gwr72xxSteamLocomotive>of(Gwr72xxSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_gwr_72xx"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<RwType3SteamLocomotive>> LOCOMOTIVE_STEAM_RW_TYPE_3 =
            ENTITY_TYPES.register("locomotive_steam_rw_type_3", () ->
                    EntityType.Builder.<RwType3SteamLocomotive>of(RwType3SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_rw_type_3"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<Gwr101ClassSteamLocomotive>> LOCOMOTIVE_STEAM_GWR_101_CLASS =
            ENTITY_TYPES.register("locomotive_steam_gwr_101_class", () ->
                    EntityType.Builder.<Gwr101ClassSteamLocomotive>of(Gwr101ClassSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_gwr_101_class"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<Wwcp062tSteamLocomotive>> LOCOMOTIVE_STEAM_WWCP_062T =
            ENTITY_TYPES.register("locomotive_steam_wwcp_062t", () ->
                    EntityType.Builder.<Wwcp062tSteamLocomotive>of(Wwcp062tSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_wwcp_062t"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<J50SteamLocomotive>> LOCOMOTIVE_STEAM_J50 =
            ENTITY_TYPES.register("locomotive_steam_j50", () ->
                    EntityType.Builder.<J50SteamLocomotive>of(J50SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_j50"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<SentinelY3SteamLocomotive>> LOCOMOTIVE_STEAM_SENTINEL_Y3 =
            ENTITY_TYPES.register("locomotive_steam_sentinel_y3", () ->
                    EntityType.Builder.<SentinelY3SteamLocomotive>of(SentinelY3SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_sentinel_y3"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<FourFourZeroSteamLocomotive>> LOCOMOTIVE_STEAM_4_4_0 =
            ENTITY_TYPES.register("locomotive_steam_4_4_0", () ->
                    EntityType.Builder.<FourFourZeroSteamLocomotive>of(FourFourZeroSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_4_4_0"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<FourFourZeroSteamTender>> STEAM_TENDER_4_4_0 =
            ENTITY_TYPES.register("steam_tender_4_4_0", () ->
                    EntityType.Builder.<FourFourZeroSteamTender>of(FourFourZeroSteamTender::new, MobCategory.MISC)
                            .sized(0.98F, 1.25F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":steam_tender_4_4_0"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<Lssp7SteamLocomotive>> LOCOMOTIVE_STEAM_LSSP7 =
            ENTITY_TYPES.register("locomotive_steam_lssp7", () ->
                    EntityType.Builder.<Lssp7SteamLocomotive>of(Lssp7SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_lssp7"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<Alice040SteamLocomotive>> LOCOMOTIVE_STEAM_ALICE_0_4_0 =
            ENTITY_TYPES.register("locomotive_steam_alice_0_4_0", () ->
                    EntityType.Builder.<Alice040SteamLocomotive>of(Alice040SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_alice_0_4_0"));

    /** Step 8.4.6 Batch B7 legacy roster. */
    public static final RegistryObject<EntityType<Vb040SteamLocomotive>> LOCOMOTIVE_STEAM_VB_0_4_0 =
            ENTITY_TYPES.register("locomotive_steam_vb_0_4_0", () ->
                    EntityType.Builder.<Vb040SteamLocomotive>of(Vb040SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_vb_0_4_0"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<C41080SteamLocomotive>> LOCOMOTIVE_STEAM_C41_080 =
            ENTITY_TYPES.register("locomotive_steam_c41_080", () ->
                    EntityType.Builder.<C41080SteamLocomotive>of(C41080SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_c41_080"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<AlcoSc4SteamLocomotive>> LOCOMOTIVE_STEAM_ALCO_SC4 =
            ENTITY_TYPES.register("locomotive_steam_alco_sc4", () ->
                    EntityType.Builder.<AlcoSc4SteamLocomotive>of(AlcoSc4SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_alco_sc4"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<C41TSteamLocomotive>> LOCOMOTIVE_STEAM_C41T =
            ENTITY_TYPES.register("locomotive_steam_c41t", () ->
                    EntityType.Builder.<C41TSteamLocomotive>of(C41TSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_c41t"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<VbShaySteamLocomotive>> LOCOMOTIVE_STEAM_VB_SHAY =
            ENTITY_TYPES.register("locomotive_steam_vb_shay", () ->
                    EntityType.Builder.<VbShaySteamLocomotive>of(VbShaySteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_vb_shay"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<Glyn042tSteamLocomotive>> LOCOMOTIVE_STEAM_GLYN_042T =
            ENTITY_TYPES.register("locomotive_steam_glyn_042t", () ->
                    EntityType.Builder.<Glyn042tSteamLocomotive>of(Glyn042tSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_glyn_042t"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<TwoSixTwoTSteamLocomotive>> LOCOMOTIVE_STEAM_262T =
            ENTITY_TYPES.register("locomotive_steam_262t", () ->
                    EntityType.Builder.<TwoSixTwoTSteamLocomotive>of(TwoSixTwoTSteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_262t"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<SteamSnowPlowLocomotive>> LOCOMOTIVE_STEAM_SNOW_PLOW =
            ENTITY_TYPES.register("locomotive_steam_snow_plow", () ->
                    EntityType.Builder.<SteamSnowPlowLocomotive>of(SteamSnowPlowLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_snow_plow"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<VbShay2SteamLocomotive>> LOCOMOTIVE_STEAM_VB_SHAY_2 =
            ENTITY_TYPES.register("locomotive_steam_vb_shay_2", () ->
                    EntityType.Builder.<VbShay2SteamLocomotive>of(VbShay2SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_vb_shay_2"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<Climax2SteamLocomotive>> LOCOMOTIVE_STEAM_CLIMAX_2 =
            ENTITY_TYPES.register("locomotive_steam_climax_2", () ->
                    EntityType.Builder.<Climax2SteamLocomotive>of(Climax2SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_climax_2"));

    /** Step 8.4.7 Batch B8 legacy roster. */
    public static final RegistryObject<EntityType<C11SteamLocomotive>> LOCOMOTIVE_STEAM_C11 =
            ENTITY_TYPES.register("locomotive_steam_c11", () ->
                    EntityType.Builder.<C11SteamLocomotive>of(C11SteamLocomotive::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":locomotive_steam_c11"));

    /** Step 7.3.0 first classic non-tender rolling-stock vehicle. */
    public static final RegistryObject<EntityType<FreightCart>> FREIGHT_CART =
            ENTITY_TYPES.register("freight_cart", () ->
                    EntityType.Builder.<FreightCart>of(FreightCart::new, MobCategory.MISC)
                            .sized(0.98F, 1.60F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":freight_cart"));

    /** Step 7.3.1 first classic rideable passenger coach. */
    public static final RegistryObject<EntityType<PassengerCoachBlue>> PASSENGER_COACH_BLUE =
            ENTITY_TYPES.register("passenger_coach_blue", () ->
                    EntityType.Builder.<PassengerCoachBlue>of(PassengerCoachBlue::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":passenger_coach_blue"));

    /** Step 7.3.2 classic single-rider caboose. */
    public static final RegistryObject<EntityType<ClassicCaboose>> CABOOSE =
            ENTITY_TYPES.register("caboose", () ->
                    EntityType.Builder.<ClassicCaboose>of(ClassicCaboose::new, MobCategory.MISC)
                            .sized(0.98F, 1.70F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build(Traincraft.MOD_ID + ":caboose"));

    private TCEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
