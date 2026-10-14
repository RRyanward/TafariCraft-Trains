package traincraft.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * DEV37 r1d - classic Traincraft Assembly Table bill-of-materials.
 *
 * Original recipe quantities/tier assignments were reconstructed from
 * EternalBlueFlame/Traincraft-5 branch 1.7-Community-4.3,
 * AssemblyTableRecipes.java.  The older addRecipe component order is
 * translated into the later Traincraft 10-slot Assembly Table GUI order:
 * planks, chimney, cab, dye, component, boiler, firebox, wheels, frame, coupler.
 *
 * U57 locomotive/tender and the current 4-4-0 tender did not have enabled
 * legacy recipe outputs in that source; those three are explicit port fallbacks.
 */
public final class ClassicAssemblyRecipes {
    public enum Kind { EMPTY, ITEM, TAG, DYE }

    public record Spec(Kind kind, String key, int count) {
        public boolean matches(ItemStack stack) {
            if (kind == Kind.EMPTY) {
                return stack.isEmpty();
            }
            if (stack.isEmpty() || stack.getCount() < count) {
                return false;
            }
            if (kind == Kind.DYE) {
                return stack.getItem() instanceof DyeItem;
            }
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) {
                return false;
            }
            if (kind == Kind.TAG) {
                TagKey<Item> tag = ItemTags.create(id);
                return stack.is(tag);
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            return item != null && item != Items.AIR && stack.is(item);
        }
    }

    public record Definition(
            int tier,
            String outputId,
            String source,
            List<Spec> slots) {
        public boolean matches(int tableTier, ItemStackHandler inventory) {
            if (tableTier < tier) {
                return false;
            }
            for (int slot = 0; slot < 10; slot++) {
                if (!slots.get(slot).matches(inventory.getStackInSlot(slot))) {
                    return false;
                }
            }
            return !createOutput().isEmpty();
        }

        public ItemStack createOutput() {
            ResourceLocation id = ResourceLocation.tryParse(outputId);
            if (id == null) {
                return ItemStack.EMPTY;
            }
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        }

        public void consume(ItemStackHandler inventory) {
            for (int slot = 0; slot < 10; slot++) {
                Spec spec = slots.get(slot);
                if (spec.kind() != Kind.EMPTY && spec.count() > 0) {
                    inventory.extractItem(slot, spec.count(), false);
                }
            }
        }
    }

    private static Spec empty() {
        return new Spec(Kind.EMPTY, "", 0);
    }

    private static Spec item(String id, int count) {
        return new Spec(Kind.ITEM, id, count);
    }

    private static Spec tag(String id, int count) {
        return new Spec(Kind.TAG, id, count);
    }

    private static Spec dye(int count) {
        return new Spec(Kind.DYE, "", count);
    }

    private static Definition r(
            int tier,
            String output,
            String source,
            Spec planks,
            Spec chimney,
            Spec cab,
            Spec dye,
            Spec component,
            Spec boiler,
            Spec firebox,
            Spec wheels,
            Spec frame,
            Spec coupler) {
        return new Definition(
                tier,
                output,
                source,
                List.of(
                        planks,
                        chimney,
                        cab,
                        dye,
                        component,
                        boiler,
                        firebox,
                        wheels,
                        frame,
                        coupler));
    }

    public static final List<Definition> ALL = List.of(
        r(1, "traincraft:locomotive_steam_262t", "minecartLocoSteam262T / 240", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), empty(), item("traincraft:boiler_steel", 1), item("traincraft:steel_firebox", 1), item("traincraft:bogie_iron", 4), item("traincraft:frame_wood", 1), empty()),
        r(2, "traincraft:locomotive_steam_4_4_0", "minecartLoco4 / 449", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), empty(), empty(), item("traincraft:boiler_steel", 4), item("traincraft:steel_firebox", 2), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:locomotive_steam_a4_mallard", "minecartLocoA4Mallard / 549", item("minecraft:iron_ingot", 2), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), empty(), empty(), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:steel_frame", 2), empty()),
        r(1, "traincraft:locomotive_steam_adler", "minecartLocoSteamAdler / 142", tag("minecraft:planks", 8), item("traincraft:chimney_iron", 1), empty(), dye(1), item("minecraft:gold_ingot", 2), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_wood", 3), item("traincraft:frame_wood", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_alco_sc4", "minecartLocoAlcoSC4 / 96", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 2), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_alice_0_4_0", "minecartLocoSteamAlice / 231", tag("minecraft:planks", 2), item("traincraft:chimney_iron", 1), empty(), empty(), empty(), item("traincraft:boiler_steel", 1), item("traincraft:steel_firebox", 1), item("traincraft:bogie_iron", 2), item("traincraft:frame_wood", 1), empty()),
        r(2, "traincraft:locomotive_steam_berkshire_1225", "minecartLocoBerk1225 / 385", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 3), item("traincraft:steel_bogie", 4), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:locomotive_steam_berkshire_765", "minecartLocoBerk765 / 390", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 3), item("traincraft:steel_bogie", 4), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_br_black_5", "minecartBR_Black_5 / 79", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_br_britannia", "minecartBRBritanniaClass / 81", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:locomotive_steam_br01", "minecartLocoBR01_DB / 442", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), empty(), empty(), item("traincraft:boiler_steel", 2), item("traincraft:steel_firebox", 1), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_br80", "minecartLocoBR80_DB / 68", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), dye(1), item("minecraft:water_bucket", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_c11", "minecartLocoC11 / 102", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_c41", "minecartLocoC41 / 97", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_c41_080", "minecartLocoC41_080 / 98", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 6), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_c41t", "minecartLocoC41T / 99", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:locomotive_steam_c62", "minecartLocoC62Class / 443", item("traincraft:steel_ingot", 2), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), empty(), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 1), item("traincraft:steel_bogie", 5), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_cherepanov", "minecartLocoCherepanov / 59", empty(), item("traincraft:chimney_iron", 1), empty(), empty(), empty(), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_wood", 2), item("traincraft:frame_wood", 1), item("minecraft:stick", 1)),
        r(1, "traincraft:locomotive_steam_climax", "minecartLocoSteamClimax / 241", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_wood", 1), empty(), empty(), item("traincraft:boiler_steel", 1), item("traincraft:steel_firebox", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 1), empty()),
        r(1, "traincraft:locomotive_steam_climax_2", "minecartClimaxNew / 93", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 2), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 2), item("traincraft:bogie_iron", 4), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 3)),
        r(2, "traincraft:locomotive_steam_coronation_class", "minecartLocoCoranationClass / 359", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 2), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:locomotive_steam_d51", "minecartLocoD51Short / 444", item("traincraft:steel_ingot", 2), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), empty(), empty(), item("traincraft:boiler_steel", 2), item("traincraft:steel_firebox", 1), item("traincraft:steel_bogie", 5), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:locomotive_steam_d51_long", "minecartLocoD51Long / 445", item("traincraft:steel_ingot", 2), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), empty(), empty(), item("traincraft:boiler_steel", 2), item("traincraft:steel_firebox", 1), item("traincraft:steel_bogie", 5), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_forney", "minecartLocoForneyRed / 123", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), dye(1), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:locomotive_steam_fowler", "minecartLocoFowler / 395", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 3), item("traincraft:steel_bogie", 4), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_glyn_042t", "minecartLocoSteamGLYN / 239", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_wood", 1), empty(), empty(), item("traincraft:boiler_steel", 1), item("traincraft:steel_firebox", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 1), empty()),
        r(3, "traincraft:locomotive_steam_gs4", "minecartGS4_Loco / 709", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 4), item("traincraft:steel_firebox", 1), item("traincraft:steel_bogie", 4), item("traincraft:steel_frame", 1), empty()),
        r(1, "traincraft:locomotive_steam_gwr_101_class", "minecartGWR101Class / 86", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_gwr_42xx", "minecartGWR42xx / 89", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_gwr_72xx", "minecartGWR72xx / 90", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:locomotive_steam_hall_class", "minecartLocoHallClass / 275", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 2), item("traincraft:steel_firebox", 2), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 1)),
        r(2, "traincraft:locomotive_steam_heavy", "minecartHeavySteam / 450", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), empty(), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 2), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_j50", "minecartJ50 / 84", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:locomotive_steam_king_class", "minecartLocoKingClass / 280", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 2), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_lssp7", "minecartLocoLSSP7 / 132", empty(), empty(), item("traincraft:cab_iron", 1), empty(), empty(), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_wood", 2), item("traincraft:frame_wood", 2), empty()),
        r(2, "traincraft:locomotive_steam_midland_compound", "minecartMRcompound / 400", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 4), item("traincraft:steel_firebox", 4), item("traincraft:steel_bogie", 8), item("traincraft:steel_frame", 5), item("traincraft:steel_ingot", 2)),
        r(3, "traincraft:locomotive_steam_milw_class_a", "minecartLocoMILWClassA / 717", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 1), item("traincraft:steel_bogie", 4), item("traincraft:steel_frame", 1), empty()),
        r(2, "traincraft:locomotive_steam_mogul", "minecartLocomogulBlue / 307", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 2), item("traincraft:steel_firebox", 1), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:locomotive_steam_pannier", "minecartLocoSteamPannier / 302", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 2), item("traincraft:steel_firebox", 1), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:locomotive_steam_pe", "minecartPELocomotive / 412", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), dye(1), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 3), item("traincraft:steel_bogie", 4), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_rw_type_2", "minecartRWType2 / 85", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_rw_type_3", "minecartRWType3 / 83", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_s100_uk", "minecartLocoUSATCUK / 110", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), dye(1), item("minecraft:coal", 1), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_s100_us", "minecartLocoUSATCUS / 106", empty(), item("traincraft:steel_chimney", 1), item("traincraft:steel_cabin", 1), dye(1), item("minecraft:coal", 1), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_sentinel_y3", "minecartSentinelY3 / 87", empty(), item("traincraft:chimney_iron", 2), item("traincraft:cab_wood", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_wood", 2), item("traincraft:frame_wood", 1), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_shay", "minecartLocoSteamShay / 78", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_shay_3_truck", "minecartShay3Truck / 73", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), dye(1), item("minecraft:coal", 2), item("traincraft:boiler_iron", 3), item("traincraft:firebox_iron", 2), item("traincraft:bogie_iron", 4), item("traincraft:frame_iron", 4), item("minecraft:iron_ingot", 4)),
        r(2, "traincraft:locomotive_steam_skookum", "minecartSkook / 272", item("minecraft:torch", 1), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 2), item("minecraft:iron_ingot", 1), item("minecraft:iron_ingot", 2), item("traincraft:boiler_steel", 4), item("traincraft:steel_firebox", 2), item("traincraft:steel_bogie", 6), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_snow_plow", "minecartLocoSnowPlow / 128", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_wood", 1), empty(), empty(), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 2), item("traincraft:frame_wood", 4), empty()),
        r(1, "traincraft:locomotive_steam_southern1102", "minecartLocoSouthern1102 / 104", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_star_class", "minecartStarClass / 151", tag("minecraft:planks", 8), item("traincraft:chimney_iron", 1), empty(), dye(1), item("minecraft:gold_ingot", 2), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_wood", 3), item("traincraft:frame_wood", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:locomotive_steam_ussr", "minecartLocoEr / 518", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), empty(), empty(), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 2), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:locomotive_steam_vb_0_4_0", "minecartLocoSteam040vb / 232", tag("minecraft:planks", 4), item("traincraft:chimney_iron", 1), empty(), empty(), empty(), item("traincraft:boiler_steel", 1), item("traincraft:steel_firebox", 1), item("traincraft:bogie_iron", 2), item("traincraft:frame_wood", 1), empty()),
        r(1, "traincraft:locomotive_steam_vb_shay", "minecartLocoSteamVBShay / 230", tag("minecraft:planks", 2), item("traincraft:chimney_iron", 1), item("traincraft:cab_wood", 1), tag("minecraft:planks", 4), empty(), item("traincraft:boiler_steel", 1), item("traincraft:steel_firebox", 1), item("traincraft:bogie_iron", 2), item("traincraft:frame_wood", 2), empty()),
        r(1, "traincraft:locomotive_steam_vb_shay_2", "minecartVBShay2 / 92", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_wood", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 4), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_wwcp_062t", "minecartWWCPClass062T / 91", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_iron", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_iron", 2), item("traincraft:firebox_iron", 1), item("traincraft:bogie_iron", 3), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:steam_tender_4000_gallon", "minecart4000GallonTender / 527", item("traincraft:steel_ingot", 6), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:steam_tender_a4_mallard", "minecartLocoA4MallardTender / 552", empty(), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:steel_frame", 2), item("minecraft:iron_ingot", 1)),
        r(1, "traincraft:steam_tender_adler", "minecartTenderAdler / 144", item("minecraft:iron_ingot", 2), empty(), empty(), dye(1), item("minecraft:coal", 1), empty(), empty(), item("traincraft:bogie_wood", 2), item("traincraft:frame_wood", 1), item("minecraft:iron_ingot", 1)),
        r(2, "traincraft:steam_tender_berkshire", "minecarttenderBerk1225 / 418", empty(), empty(), empty(), dye(1), item("minecraft:coal", 2), empty(), empty(), item("traincraft:steel_bogie", 4), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:steam_tender_br_black_5", "minecartBR_Black_5_Tender / 80", item("minecraft:iron_ingot", 2), empty(), empty(), empty(), item("minecraft:coal", 2), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:steam_tender_br01", "minecartTenderBR01_DB / 357", item("traincraft:steel_ingot", 4), empty(), empty(), dye(1), item("minecraft:coal", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:steam_tender_br1", "minecartBR1Tender / 82", item("minecraft:iron_ingot", 2), empty(), empty(), empty(), item("minecraft:coal", 2), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:steam_tender_c41", "minecartTenderC41 / 100", item("minecraft:iron_ingot", 2), empty(), empty(), empty(), item("minecraft:coal", 2), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(2, "traincraft:steam_tender_c62", "minecartTenderC62Class / 507", item("traincraft:steel_ingot", 6), empty(), empty(), empty(), item("minecraft:coal", 2), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:steam_tender_coronation_class", "minecartLocoCoranationClassTender / 522", item("traincraft:steel_ingot", 6), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:steam_tender_d51", "minecartTenderD51 / 447", item("traincraft:steel_ingot", 2), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:steam_tender_fowler", "minecartFowler4FTender / 422", empty(), empty(), empty(), dye(1), item("minecraft:coal", 2), empty(), empty(), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(3, "traincraft:steam_tender_gs4", "minecartGS4_Tender / 706", item("minecraft:coal", 2), empty(), empty(), dye(1), empty(), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(2, "traincraft:steam_tender_heavy", "minecartTenderHeavy / 532", item("traincraft:steel_ingot", 4), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:steam_tender_midland_compound", "minecartMRcompoundTender / 426", empty(), empty(), empty(), dye(1), item("minecraft:coal", 2), empty(), empty(), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(3, "traincraft:steam_tender_milw", "minecartMILWTender / 714", item("minecraft:coal", 2), empty(), empty(), dye(1), empty(), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(2, "traincraft:steam_tender_pe", "minecartPETender / 434", empty(), empty(), empty(), dye(1), item("minecraft:coal", 2), empty(), empty(), item("traincraft:steel_bogie", 4), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:steam_tender_rw_type_2", "minecartRWType2Tender / 101", item("minecraft:iron_ingot", 2), empty(), empty(), empty(), item("minecraft:coal", 2), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:steam_tender_shay_3_truck", "minecartShay3TruckTender / 74", item("minecraft:iron_ingot", 2), empty(), empty(), dye(1), item("minecraft:coal", 2), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 3)),
        r(2, "traincraft:steam_tender_skookum", "minecartSkookTender / 533", item("traincraft:steel_ingot", 4), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 4), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:steam_tender_southern1102", "minecartTenderSouthern1102 / 115", item("minecraft:iron_ingot", 2), empty(), empty(), empty(), item("minecraft:coal", 2), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:steam_tender_star_class", "minecartStarClassTender / 153", item("minecraft:iron_ingot", 2), empty(), empty(), dye(1), item("minecraft:coal", 1), empty(), empty(), item("traincraft:bogie_wood", 2), item("traincraft:frame_wood", 1), item("minecraft:iron_ingot", 1)),
        r(2, "traincraft:steam_tender_ussr", "minecartTenderEr / 520", item("traincraft:steel_ingot", 6), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(3, "traincraft:gs4_baggage_car", "minecartGS4_Baggage / 708", empty(), empty(), empty(), dye(1), item("minecraft:chest", 2), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(3, "traincraft:gs4_observatory_car", "minecartGS4_Observatory / 704", item("minecraft:glass", 1), empty(), item("traincraft:steel_cabin", 1), dye(1), item("traincraft:seats", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(3, "traincraft:gs4_passenger_car", "minecartGS4_Passenger / 340", empty(), empty(), item("traincraft:steel_cabin", 1), dye(1), item("traincraft:seats", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(3, "traincraft:gs4_tail_car", "minecartGS4_Tail / 341", empty(), empty(), item("traincraft:steel_cabin", 1), dye(1), item("traincraft:seats", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(3, "traincraft:milw_baggage_car", "minecartBaggageMILW / 716", empty(), empty(), empty(), dye(1), item("minecraft:chest", 2), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(3, "traincraft:milw_passenger_car", "minecartPassengerMILW / 711", empty(), empty(), item("traincraft:steel_cabin", 1), dye(1), item("traincraft:seats", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(3, "traincraft:milw_tail_car", "minecartPassengerMILWTail / 712", empty(), empty(), item("traincraft:steel_cabin", 1), dye(1), item("traincraft:seats", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 1), empty()),
        r(2, "traincraft:locomotive_steam_u57", "minecartLocoU57 (legacy ItemID disabled) / fallback-U57", empty(), item("traincraft:steel_chimney", 2), item("traincraft:steel_cabin", 1), empty(), item("minecraft:coal", 1), item("traincraft:boiler_steel", 3), item("traincraft:steel_firebox", 2), item("traincraft:steel_bogie", 3), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:steam_tender_u57", "minecartTenderU57 (legacy ItemID disabled) / fallback-U57Tender", item("traincraft:steel_ingot", 6), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 3), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:steam_tender_4_4_0", "4-4-0 tender current-port companion (no enabled legacy output) / fallback-440Tender", item("minecraft:iron_ingot", 2), empty(), empty(), empty(), item("minecraft:coal", 2), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:steel_frame", 2), item("minecraft:iron_ingot", 2)),
        r(1, "traincraft:locomotive_steam_small", "minecartLoco3 / legacy-approx-L130", empty(), item("traincraft:chimney_iron", 1), item("traincraft:cab_wood", 1), dye(1), item("minecraft:torch", 1), item("traincraft:boiler_iron", 1), item("traincraft:firebox_iron", 1), item("traincraft:bogie_wood", 2), item("traincraft:frame_wood", 2), item("minecraft:stick", 2)),
        r(1, "traincraft:steam_tender", "minecartTender / legacy-L62", empty(), empty(), empty(), empty(), item("minecraft:coal", 1), empty(), empty(), item("traincraft:bogie_wood", 2), item("traincraft:frame_wood", 1), item("minecraft:stick", 2)),
        r(1, "traincraft:freight_cart", "minecartFreightCart2 / legacy-L177", tag("minecraft:planks", 6), empty(), empty(), empty(), item("minecraft:chest", 2), empty(), empty(), item("traincraft:bogie_iron", 2), item("traincraft:frame_iron", 2), item("minecraft:iron_ingot", 2)),
        r(3, "traincraft:passenger_coach_blue", "minecartPassengerBlue / legacy-L864", item("traincraft:steel_ingot", 2), empty(), item("traincraft:steel_cabin", 1), dye(1), empty(), empty(), item("traincraft:seats", 1), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(1, "traincraft:caboose", "minecartCaboose / legacy-L170", tag("minecraft:planks", 6), empty(), item("traincraft:cab_wood", 1), empty(), item("traincraft:seats", 1), empty(), empty(), item("traincraft:bogie_wood", 2), item("traincraft:frame_wood", 2), item("minecraft:stick", 2)),
        r(2, "traincraft:livestock_car", "minecartStockCar / legacy-L575", tag("minecraft:planks", 6), empty(), empty(), empty(), item("minecraft:leather", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2)),
        r(2, "traincraft:mob_transport_car", "current-port prisoner/livestock variant / port-derived", item("minecraft:iron_bars", 4), empty(), empty(), empty(), item("minecraft:leather", 1), empty(), empty(), item("traincraft:steel_bogie", 2), item("traincraft:steel_frame", 2), item("traincraft:steel_ingot", 2))
    );

    private ClassicAssemblyRecipes() {}

    public static Definition findMatch(int tableTier, ItemStackHandler inventory) {
        for (Definition recipe : ALL) {
            if (recipe.matches(tableTier, inventory)) {
                return recipe;
            }
        }
        return null;
    }

    public static boolean hasOutput(String id) {
        String full = id.indexOf(':') >= 0 ? id : "traincraft:" + id;
        for (Definition recipe : ALL) {
            if (recipe.outputId().equals(full)) {
                return true;
            }
        }
        return false;
    }
}