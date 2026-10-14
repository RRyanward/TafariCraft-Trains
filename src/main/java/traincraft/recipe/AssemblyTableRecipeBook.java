package traincraft.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AssemblyTableRecipeBook {
    public static final int EXPECTED_RECIPE_COUNT = 114;
    public record IngredientSpec(String id, int count) {
        public boolean matches(ItemStack stack) {
            if (id == null || id.isEmpty()) return stack.isEmpty();
            if (stack.isEmpty() || stack.getCount() < count) return false;
            if (id.equals("@dye")) return stack.getItem() instanceof DyeItem;
            if (id.equals("@planks")) return stack.is(ItemTags.PLANKS);
            if (id.equals("@logs")) return stack.is(ItemTags.LOGS);
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            return item != Items.AIR && stack.is(item);
        }
    }

    public record Recipe(int tier, String outputId, IngredientSpec[] ingredients) {
        public boolean matches(int tableTier, ItemStackHandler inv) {
            if (tier > tableTier) return false;
            for (int i = 0; i < 10; i++) {
                IngredientSpec spec = ingredients[i];
                ItemStack stack = inv.getStackInSlot(i);
                if (spec == null) {
                    if (!stack.isEmpty()) return false;
                } else if (!spec.matches(stack)) {
                    return false;
                }
            }
            return true;
        }

        public ItemStack output() {
            Item item = BuiltInRegistries.ITEM.get(new ResourceLocation("traincraft", outputId));
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }
    }

    private static final String RAW = """
1|locomotive_steam_cherepanov||traincraft:bogie_wood*2|traincraft:frame_wood*1|minecraft:stick*1|traincraft:chimney_iron*1||traincraft:boiler_iron*1|traincraft:firebox_iron*1||
1|locomotive_steam_br80||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:water_bucket*1|@dye*1
1|locomotive_steam_shay_3_truck||traincraft:bogie_iron*4|traincraft:frame_iron*4|minecraft:iron_ingot*4|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*3|traincraft:firebox_iron*2|minecraft:coal*2|@dye*1
1|steam_tender_shay_3_truck|minecraft:iron_ingot*2|traincraft:bogie_iron*2|traincraft:frame_iron*2|minecraft:iron_ingot*3|||||minecraft:coal*2|@dye*1
1|locomotive_steam_shay||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_br_black_5||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|steam_tender_br_black_5|minecraft:iron_ingot*2|traincraft:bogie_iron*2|traincraft:frame_iron*2|minecraft:iron_ingot*2|||||minecraft:coal*2|
1|locomotive_steam_br_britannia||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|steam_tender_br1|minecraft:iron_ingot*2|traincraft:bogie_iron*2|traincraft:frame_iron*2|minecraft:iron_ingot*2|||||minecraft:coal*2|
1|locomotive_steam_rw_type_3||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_j50||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_rw_type_2||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_gwr_101_class||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_sentinel_y3||traincraft:bogie_wood*2|traincraft:frame_wood*1|minecraft:iron_ingot*2|traincraft:chimney_iron*2|traincraft:cab_wood*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_gwr_42xx||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_gwr_72xx||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_wwcp_062t||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_vb_shay_2||traincraft:bogie_iron*4|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_wood*1|traincraft:boiler_iron*1|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_climax_2||traincraft:bogie_iron*4|traincraft:frame_iron*2|minecraft:iron_ingot*3|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*2|minecraft:coal*2|
1|locomotive_steam_alco_sc4||traincraft:bogie_iron*2|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*1|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_c41||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_c41_080||traincraft:bogie_iron*6|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_c41t||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|steam_tender_c41|minecraft:iron_ingot*2|traincraft:bogie_iron*2|traincraft:frame_iron*2|minecraft:iron_ingot*2|||||minecraft:coal*2|
1|steam_tender_rw_type_2|minecraft:iron_ingot*2|traincraft:bogie_iron*2|traincraft:frame_iron*2|minecraft:iron_ingot*2|||||minecraft:coal*2|
1|locomotive_steam_c11||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_southern1102||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|
1|locomotive_steam_s100_us||traincraft:bogie_iron*3|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_iron*1|traincraft:firebox_iron*1|minecraft:coal*1|@dye*1
1|locomotive_steam_s100_uk||traincraft:bogie_iron*3|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_iron*1|traincraft:firebox_iron*1|minecraft:coal*1|@dye*1
1|steam_tender_southern1102|minecraft:iron_ingot*2|traincraft:bogie_iron*2|traincraft:frame_iron*2|minecraft:iron_ingot*2|||||minecraft:coal*2|
1|locomotive_steam_forney||traincraft:bogie_iron*3|traincraft:frame_iron*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1|minecraft:coal*1|@dye*1
1|locomotive_steam_snow_plow||traincraft:bogie_iron*2|traincraft:frame_wood*4||traincraft:chimney_iron*1|traincraft:cab_wood*1|traincraft:boiler_iron*1|traincraft:firebox_iron*1||
1|locomotive_steam_lssp7||traincraft:bogie_wood*2|traincraft:frame_wood*2|||traincraft:cab_iron*1|traincraft:boiler_iron*1|traincraft:firebox_iron*1||
1|locomotive_steam_adler|@planks*8|traincraft:bogie_wood*3|traincraft:frame_wood*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1||traincraft:boiler_iron*1|traincraft:firebox_iron*1|minecraft:gold_ingot*2|@dye*1
1|steam_tender_adler|minecraft:iron_ingot*2|traincraft:bogie_wood*2|traincraft:frame_wood*1|minecraft:iron_ingot*1|||||minecraft:coal*1|@dye*1
1|locomotive_steam_star_class|@planks*8|traincraft:bogie_wood*3|traincraft:frame_wood*2|minecraft:iron_ingot*2|traincraft:chimney_iron*1||traincraft:boiler_iron*1|traincraft:firebox_iron*1|minecraft:gold_ingot*2|@dye*1
1|steam_tender_star_class|minecraft:iron_ingot*2|traincraft:bogie_wood*2|traincraft:frame_wood*1|minecraft:iron_ingot*1|||||minecraft:coal*1|@dye*1
1|locomotive_steam_vb_shay|@planks*2|traincraft:bogie_iron*2|traincraft:frame_wood*2||traincraft:chimney_iron*1|traincraft:cab_wood*1|traincraft:boiler_steel*1|traincraft:steel_firebox*1||@planks*4
1|locomotive_steam_alice_0_4_0|@planks*2|traincraft:bogie_iron*2|traincraft:frame_wood*1||traincraft:chimney_iron*1||traincraft:boiler_steel*1|traincraft:steel_firebox*1||
1|locomotive_steam_vb_0_4_0|@planks*4|traincraft:bogie_iron*2|traincraft:frame_wood*1||traincraft:chimney_iron*1||traincraft:boiler_steel*1|traincraft:steel_firebox*1||
1|locomotive_steam_glyn_042t||traincraft:bogie_iron*3|traincraft:frame_iron*1||traincraft:chimney_iron*1|traincraft:cab_wood*1|traincraft:boiler_steel*1|traincraft:steel_firebox*1||
1|locomotive_steam_262t||traincraft:bogie_iron*4|traincraft:frame_wood*1||traincraft:chimney_iron*1|traincraft:cab_iron*1|traincraft:boiler_steel*1|traincraft:steel_firebox*1||
1|locomotive_steam_climax||traincraft:bogie_iron*3|traincraft:frame_iron*1||traincraft:chimney_iron*1|traincraft:cab_wood*1|traincraft:boiler_steel*1|traincraft:steel_firebox*1||
2|locomotive_steam_skookum|minecraft:torch*1|traincraft:steel_bogie*6|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*2|traincraft:boiler_steel*4|traincraft:steel_firebox*2|minecraft:iron_ingot*2|minecraft:iron_ingot*1
2|locomotive_steam_hall_class||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*1|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*2|traincraft:steel_firebox*2||@dye*1
2|locomotive_steam_king_class||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*2||@dye*1
2|locomotive_steam_pannier||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*2|traincraft:steel_firebox*1||@dye*1
2|locomotive_steam_mogul||traincraft:steel_bogie*3|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*2|traincraft:steel_firebox*1||@dye*1
2|steam_tender_br01|traincraft:steel_ingot*4|traincraft:steel_bogie*2|traincraft:steel_frame*2|traincraft:steel_ingot*2|||||minecraft:coal*1|@dye*1
2|locomotive_steam_coronation_class||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*2||@dye*1
2|locomotive_steam_berkshire_1225||traincraft:steel_bogie*4|traincraft:steel_frame*4|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*3||@dye*1
2|locomotive_steam_berkshire_765||traincraft:steel_bogie*4|traincraft:steel_frame*4|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*3||@dye*1
2|locomotive_steam_fowler||traincraft:steel_bogie*4|traincraft:steel_frame*4|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*3||@dye*1
2|locomotive_steam_midland_compound||traincraft:steel_bogie*8|traincraft:steel_frame*5|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*4|traincraft:steel_firebox*4||@dye*1
2|locomotive_steam_pe||traincraft:steel_bogie*4|traincraft:steel_frame*4|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*3||@dye*1
2|steam_tender_berkshire||traincraft:steel_bogie*4|traincraft:steel_frame*4|traincraft:steel_ingot*2|||||minecraft:coal*2|@dye*1
2|steam_tender_fowler||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|||||minecraft:coal*2|@dye*1
2|steam_tender_midland_compound||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|||||minecraft:coal*2|@dye*1
2|steam_tender_pe||traincraft:steel_bogie*4|traincraft:steel_frame*4|traincraft:steel_ingot*2|||||minecraft:coal*2|@dye*1
2|locomotive_steam_br01||traincraft:steel_bogie*3|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*2|traincraft:steel_firebox*1||
2|locomotive_steam_c62|traincraft:steel_ingot*2|traincraft:steel_bogie*5|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*1||
2|locomotive_steam_d51|traincraft:steel_ingot*2|traincraft:steel_bogie*5|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*2|traincraft:steel_firebox*1||
2|locomotive_steam_d51_long|traincraft:steel_ingot*2|traincraft:steel_bogie*5|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*2|traincraft:steel_firebox*1||
2|steam_tender_d51|traincraft:steel_ingot*2|traincraft:steel_bogie*2|traincraft:steel_frame*1|traincraft:steel_ingot*2|||||minecraft:coal*1|
2|locomotive_steam_4_4_0||traincraft:steel_bogie*3|traincraft:steel_frame*4|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*4|traincraft:steel_firebox*2||
2|locomotive_steam_heavy||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*2||
2|steam_tender_c62|traincraft:steel_ingot*6|traincraft:steel_bogie*2|traincraft:steel_frame*2|traincraft:steel_ingot*2|||||minecraft:coal*2|
2|locomotive_steam_ussr||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*2||
2|steam_tender_ussr|traincraft:steel_ingot*6|traincraft:steel_bogie*2|traincraft:steel_frame*3|traincraft:steel_ingot*2|||||minecraft:coal*1|
2|steam_tender_coronation_class|traincraft:steel_ingot*6|traincraft:steel_bogie*2|traincraft:steel_frame*3|traincraft:steel_ingot*2|||||minecraft:coal*1|
2|steam_tender_4000_gallon|traincraft:steel_ingot*6|traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|||||minecraft:coal*1|
2|steam_tender_heavy|traincraft:steel_ingot*4|traincraft:steel_bogie*2|traincraft:steel_frame*4|traincraft:steel_ingot*2|||||minecraft:coal*1|
2|steam_tender_skookum|traincraft:steel_ingot*4|traincraft:steel_bogie*2|traincraft:steel_frame*4|traincraft:steel_ingot*2|||||minecraft:coal*1|
2|locomotive_steam_a4_mallard|minecraft:iron_ingot*2|traincraft:bogie_iron*3|traincraft:steel_frame*2||traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_iron*2|traincraft:firebox_iron*1||
2|steam_tender_a4_mallard||traincraft:bogie_iron*2|traincraft:steel_frame*2|minecraft:iron_ingot*1|||||minecraft:coal*1|
3|gs4_passenger_car||traincraft:steel_bogie*2|traincraft:steel_frame*1|||traincraft:steel_cabin*1|||traincraft:seats*1|@dye*1
3|gs4_tail_car||traincraft:steel_bogie*2|traincraft:steel_frame*1|||traincraft:steel_cabin*1|||traincraft:seats*1|@dye*1
3|gs4_observatory_car|minecraft:glass*1|traincraft:steel_bogie*2|traincraft:steel_frame*1|||traincraft:steel_cabin*1|||traincraft:seats*1|@dye*1
3|steam_tender_gs4|minecraft:coal*2|traincraft:steel_bogie*2|traincraft:steel_frame*1|||||||@dye*1
3|gs4_baggage_car||traincraft:steel_bogie*2|traincraft:steel_frame*1||||||minecraft:chest*2|@dye*1
3|locomotive_steam_gs4||traincraft:steel_bogie*4|traincraft:steel_frame*1||traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*4|traincraft:steel_firebox*1||@dye*1
3|milw_passenger_car||traincraft:steel_bogie*2|traincraft:steel_frame*1|||traincraft:steel_cabin*1|||traincraft:seats*1|@dye*1
3|milw_tail_car||traincraft:steel_bogie*2|traincraft:steel_frame*1|||traincraft:steel_cabin*1|||traincraft:seats*1|@dye*1
3|steam_tender_milw|minecraft:coal*2|traincraft:steel_bogie*2|traincraft:steel_frame*1|||||||@dye*1
3|milw_baggage_car||traincraft:steel_bogie*2|traincraft:steel_frame*1||||||minecraft:chest*2|@dye*1
3|locomotive_steam_milw_class_a||traincraft:steel_bogie*4|traincraft:steel_frame*1||traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*1||@dye*1
2|locomotive_steam_u57||traincraft:steel_bogie*3|traincraft:steel_frame*3|traincraft:steel_ingot*2|traincraft:steel_chimney*2|traincraft:steel_cabin*1|traincraft:boiler_steel*3|traincraft:steel_firebox*2||
2|steam_tender_u57|traincraft:steel_ingot*6|traincraft:steel_bogie*2|traincraft:steel_frame*3|traincraft:steel_ingot*2|||||minecraft:coal*1|
2|steam_tender_4_4_0|traincraft:steel_ingot*4|traincraft:steel_bogie*2|traincraft:steel_frame*2|traincraft:steel_ingot*2|||||minecraft:coal*1|
1|locomotive_steam_small||traincraft:bogie_wood*2|traincraft:frame_wood*2|minecraft:stick*2|traincraft:chimney_iron*1|traincraft:cab_wood*1|traincraft:boiler_iron*1|traincraft:firebox_iron*1|minecraft:torch*1|@dye*1
1|steam_tender||traincraft:bogie_wood*2|traincraft:frame_wood*1|minecraft:stick*2|||||minecraft:coal*1|
1|freight_cart|@planks*6|traincraft:bogie_iron*2|traincraft:frame_iron*2|minecraft:iron_ingot*2|||||minecraft:chest*2|
3|passenger_coach_blue|traincraft:steel_ingot*2|traincraft:steel_bogie*2|traincraft:steel_frame*2|traincraft:steel_ingot*2||traincraft:steel_cabin*1||traincraft:seats*1||@dye*1
1|caboose|@planks*6|traincraft:bogie_wood*2|traincraft:frame_wood*2|minecraft:stick*2||traincraft:cab_wood*1|||traincraft:seats*1|
2|livestock_car|@planks*6|traincraft:steel_bogie*2|traincraft:steel_frame*2|traincraft:steel_ingot*2|||||minecraft:leather*1|
2|mob_transport_car|minecraft:iron_bars*4|traincraft:steel_bogie*2|traincraft:steel_frame*2|traincraft:steel_ingot*2|||||minecraft:leather*1|
3|locomotive_diesel_kof|traincraft:controls*1|traincraft:steel_bogie*2|traincraft:steel_frame*1|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:transmission*1|traincraft:engine_diesel*2||minecraft:red_dye*1
2|locomotive_diesel_chme3|traincraft:controls*1|traincraft:steel_bogie*3|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:engine_electric*2|traincraft:engine_diesel*2|traincraft:circuit*2|minecraft:green_dye*1
2|locomotive_diesel_shunter|traincraft:controls*1|traincraft:steel_bogie*4|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:transmission*1|traincraft:engine_diesel*3||minecraft:blue_dye*1
3|locomotive_diesel_v60|traincraft:controls*1|traincraft:steel_bogie*2|traincraft:steel_frame*2|traincraft:steel_ingot*2||traincraft:steel_cabin*1|traincraft:transmission*1|traincraft:engine_diesel*3||minecraft:red_dye*1
2|locomotive_diesel_gp7|traincraft:controls*3|traincraft:steel_bogie*3|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:engine_electric*4|traincraft:engine_diesel*4|traincraft:circuit*3|minecraft:yellow_dye*1
2|locomotive_diesel_cd742|traincraft:controls*2|traincraft:steel_bogie*3|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:engine_electric*4|traincraft:engine_diesel*4|traincraft:circuit*2|minecraft:red_dye*1
3|locomotive_diesel_sd40|traincraft:controls*2|traincraft:steel_bogie*6|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:engine_electric*6|traincraft:engine_diesel*6|traincraft:circuit*4|minecraft:red_dye*1
3|locomotive_diesel_sd70|traincraft:controls*2|traincraft:steel_bogie*6|traincraft:steel_frame*2|traincraft:steel_ingot*2|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:engine_electric*4|traincraft:engine_diesel*4|traincraft:circuit*4|minecraft:orange_dye*1
3|locomotive_electric_br_e69|traincraft:controls*2|traincraft:steel_bogie*3|traincraft:steel_frame*2|traincraft:steel_ingot*1|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:transformer*2|traincraft:engine_electric*2|minecraft:redstone*2|minecraft:green_dye*1
1|locomotive_electric_minetrain|minecraft:iron_ingot*2|traincraft:bogie_iron*2|traincraft:frame_iron*1|minecraft:iron_ingot*1||traincraft:controls*1|traincraft:transformer*1|traincraft:engine_electric*2|minecraft:redstone*1|minecraft:yellow_dye*1
1|locomotive_electric_tram|@planks*4|traincraft:bogie_iron*2|traincraft:frame_wood*1|minecraft:iron_ingot*1||traincraft:cab_wood*1|traincraft:transformer*1|traincraft:engine_electric*2|minecraft:redstone*4|
2|locomotive_electric_tram_ny|traincraft:controls*1|traincraft:steel_bogie*2|traincraft:steel_frame*1|traincraft:steel_ingot*1||traincraft:steel_cabin*1|traincraft:transformer*2|traincraft:engine_electric*2|minecraft:redstone*4|
3|locomotive_electric_vl10|traincraft:controls*2|traincraft:steel_bogie*3|traincraft:steel_frame*2|traincraft:steel_ingot*1|traincraft:steel_chimney*1|traincraft:steel_cabin*1|traincraft:transformer*2|traincraft:engine_electric*2|minecraft:redstone*2|minecraft:green_dye*1
3|locomotive_electric_high_speed_zeroed|traincraft:controls*2|traincraft:steel_bogie*2|traincraft:steel_frame*1|traincraft:steel_ingot*1||traincraft:steel_cabin*1|traincraft:transformer*2|traincraft:engine_electric*2|minecraft:redstone*4|
2|tracks_builder|traincraft:steel_ingot*6|traincraft:steel_bogie*2|traincraft:steel_frame*3|traincraft:steel_ingot*1|traincraft:steel_chimney*1||traincraft:boiler_steel*1|traincraft:steel_firebox*1|minecraft:rail*16|
2|tct_tanker_universal|traincraft:steel_ingot*3|traincraft:steel_bogie*2|traincraft:steel_frame*2|minecraft:glass*4|minecraft:bucket*2||||traincraft:reinforced_plate*2|minecraft:redstone*1
2|tct_tanker_water|traincraft:steel_ingot*2|traincraft:steel_bogie*2|traincraft:steel_frame*2|minecraft:glass*3|minecraft:water_bucket*1||||traincraft:reinforced_plate*1|minecraft:bucket*1
2|tct_tanker_lava|traincraft:steel_ingot*3|traincraft:steel_bogie*2|traincraft:steel_frame*2|minecraft:glass*3|minecraft:lava_bucket*1||||traincraft:reinforced_plate*2|minecraft:bucket*1
""";

    private static final List<Recipe> RECIPES = build();

    private AssemblyTableRecipeBook() {}

    private static List<Recipe> build() {
        Map<String, Recipe> byOutput = new LinkedHashMap<>();
        for (String line : RAW.split("\\R")) {
            if (line == null || line.isBlank()) continue;
            String[] p = line.split("\\|", -1);
            if (p.length != 12) {
                throw new IllegalStateException("Bad Assembly Table recipe row: " + line);
            }

            int tier = Integer.parseInt(p[0]);
            IngredientSpec[] ingredients = new IngredientSpec[10];
            for (int i = 0; i < 10; i++) {
                ingredients[i] = parseSpec(p[i + 2]);
            }

            byOutput.putIfAbsent(p[1], new Recipe(tier, p[1], ingredients));
        }
        if (byOutput.size() != EXPECTED_RECIPE_COUNT) {
            throw new IllegalStateException(
                    "Assembly Table recipe count mismatch: "
                            + byOutput.size()
                            + " expected "
                            + EXPECTED_RECIPE_COUNT);
        }
        return List.copyOf(byOutput.values());
    }

    private static IngredientSpec parseSpec(String raw) {
        if (raw == null || raw.isBlank()) return null;
        int star = raw.lastIndexOf('*');
        if (star < 0) return new IngredientSpec(raw, 1);
        return new IngredientSpec(raw.substring(0, star), Integer.parseInt(raw.substring(star + 1)));
    }

    // TC_DEV37_R1D7_MULTI_MATCH
    public static List<Recipe> findAll(int tableTier, ItemStackHandler inv) {
        ArrayList<Recipe> matches = new ArrayList<>();
        for (Recipe recipe : RECIPES) {
            if (recipe.matches(tableTier, inv)) matches.add(recipe);
        }
        return List.copyOf(matches);
    }

    public static Recipe find(int tableTier, ItemStackHandler inv) {
        List<Recipe> matches = findAll(tableTier, inv);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public static int count() {
        return RECIPES.size();
    }

    public static List<Recipe> all() {
        return RECIPES;
    }
}