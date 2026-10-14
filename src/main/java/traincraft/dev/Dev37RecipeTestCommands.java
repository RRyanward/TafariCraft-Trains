package traincraft.dev;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.items.ItemStackHandler;
import traincraft.Traincraft;
import traincraft.block.machine.AssemblyTableBlock;
import traincraft.block.machine.AssemblyTableBlockEntity;
import traincraft.block.machine.TraincraftCraftingBlock;
import traincraft.entity.train.power.LegacyPoweredLocomotive;
import traincraft.recipe.AssemblyTableRecipeBook;
import traincraft.registry.TCBlocks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Traincraft.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Dev37RecipeTestCommands {
    private Dev37RecipeTestCommands() {}

    private record TestArea(BlockPos workbench, BlockPos tier1, BlockPos tier2, BlockPos tier3) {}
    private record OneResult(boolean pass, String detail) {}

    // TC_DEV38_R1F0_COMMAND_DISABLED_RELEASE
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tcdev")
                        .then(Commands.literal("recipes")
                                .then(Commands.literal("setup")
                                        .executes(ctx -> setup(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("all")
                                        .executes(ctx -> runSuite(ctx.getSource().getPlayerOrException(), 0)))
                                .then(Commands.literal("tier")
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                                .executes(ctx -> runSuite(
                                                        ctx.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(ctx, "tier")))))
                                .then(Commands.literal("sample")
                                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 3))
                                                .executes(ctx -> loadSample(
                                                        ctx.getSource().getPlayerOrException(),
                                                        IntegerArgumentType.getInteger(ctx, "tier"))))))
                        .then(Commands.literal("powered")
                                .executes(ctx -> reportPowered(ctx.getSource().getPlayerOrException()))));
    }

    private static TestArea area(ServerPlayer player) {
        Direction look = player.getDirection();
        Direction right = look.getClockWise();
        BlockPos center = player.blockPosition().relative(look, 4);
        return new TestArea(
                center.relative(right, -3),
                center.relative(right, -1),
                center.relative(right, 1),
                center.relative(right, 3));
    }

    private static int setup(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Direction facing = player.getDirection();
        TestArea a = area(player);

        level.setBlock(
                a.workbench(),
                TCBlocks.TRAIN_WORKBENCH.get().defaultBlockState()
                        .setValue(TraincraftCraftingBlock.FACING, facing),
                3);
        level.setBlock(
                a.tier1(),
                TCBlocks.ASSEMBLY_TABLE_I.get().defaultBlockState()
                        .setValue(AssemblyTableBlock.FACING, facing),
                3);
        level.setBlock(
                a.tier2(),
                TCBlocks.ASSEMBLY_TABLE_II.get().defaultBlockState()
                        .setValue(AssemblyTableBlock.FACING, facing),
                3);
        level.setBlock(
                a.tier3(),
                TCBlocks.ASSEMBLY_TABLE_III.get().defaultBlockState()
                        .setValue(AssemblyTableBlock.FACING, facing),
                3);

        player.sendSystemMessage(Component.literal(
                "[TC DEV38] Test row placed: Workbench | Tier I | Tier II | Tier III"));
        player.sendSystemMessage(Component.literal(
                "[TC DEV38] Duplicate BOMs now appear as multiple Output choices; use < > for another page."));
        return 1;
    }

    private static AssemblyTableBlockEntity tableAt(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof AssemblyTableBlockEntity table) {
            return table;
        }
        return null;
    }

    private static AssemblyTableBlockEntity tableForTier(ServerPlayer player, int tier) {
        TestArea a = area(player);
        BlockPos pos = tier == 1 ? a.tier1() : tier == 2 ? a.tier2() : a.tier3();
        return tableAt(player, pos);
    }

    private static int runSuite(ServerPlayer player, int exactTier) {
        setup(player);
        List<AssemblyTableRecipeBook.Recipe> recipes = AssemblyTableRecipeBook.all();
        if (recipes.size() != AssemblyTableRecipeBook.EXPECTED_RECIPE_COUNT) {
            player.sendSystemMessage(Component.literal(
                    "[TC DEV38] Recipe count mismatch: "
                            + recipes.size()
                            + " expected "
                            + AssemblyTableRecipeBook.EXPECTED_RECIPE_COUNT));
            return 0;
        }
        List<String> lines = new ArrayList<>();
        int[] passed = new int[4];
        int[] failed = new int[4];

        lines.add("TAFARICRAFT - TRAINS DEV38 r1e3 ASSEMBLY RECIPE TEST");
        lines.add("Recipes registered=" + recipes.size());
        lines.add("Mode=" + (exactTier == 0 ? "ALL" : "TIER " + exactTier));
        lines.add("");

        for (AssemblyTableRecipeBook.Recipe recipe : recipes) {
            if (exactTier != 0 && recipe.tier() != exactTier) continue;
            AssemblyTableBlockEntity table = tableForTier(player, recipe.tier());
            OneResult result = table == null
                    ? new OneResult(false, "table block entity missing")
                    : testOne(recipe, table);

            if (result.pass()) passed[recipe.tier()]++;
            else failed[recipe.tier()]++;

            lines.add((result.pass() ? "PASS" : "FAIL")
                    + " | T" + recipe.tier()
                    + " | " + recipe.outputId()
                    + " | " + result.detail());
        }

        int passTotal = passed[1] + passed[2] + passed[3];
        int failTotal = failed[1] + failed[2] + failed[3];
        lines.add("");
        lines.add("Tier I   PASS=" + passed[1] + " FAIL=" + failed[1]);
        lines.add("Tier II  PASS=" + passed[2] + " FAIL=" + failed[2]);
        lines.add("Tier III PASS=" + passed[3] + " FAIL=" + failed[3]);
        lines.add("TOTAL    PASS=" + passTotal + " FAIL=" + failTotal);

        Path report = FMLPaths.GAMEDIR.get()
                .resolve("logs")
                .resolve("traincraft-dev38-r1d-recipe-test.txt");
        try {
            Files.createDirectories(report.getParent());
            Files.write(report, lines, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            player.sendSystemMessage(Component.literal(
                    "[TC DEV38] Could not write report: " + ex.getMessage()));
        }

        player.sendSystemMessage(Component.literal(
                "[TC DEV38] Recipe suite complete: PASS " + passTotal + " / FAIL " + failTotal));
        player.sendSystemMessage(Component.literal(
                "[TC DEV38] T1 " + passed[1] + "/" + failed[1]
                        + "  T2 " + passed[2] + "/" + failed[2]
                        + "  T3 " + passed[3] + "/" + failed[3]));
        player.sendSystemMessage(Component.literal(
                "[TC DEV38] Report: run/logs/traincraft-dev38-r1d-recipe-test.txt"));

        return failTotal == 0 ? 1 : 0;
    }

    private static OneResult testOne(
            AssemblyTableRecipeBook.Recipe recipe,
            AssemblyTableBlockEntity table) {
        ItemStackHandler inv = table.getItems();
        clear(inv);

        for (int i = 0; i < 10; i++) {
            AssemblyTableRecipeBook.IngredientSpec spec = recipe.ingredients()[i];
            if (spec == null) continue;
            ItemStack stack = sampleStack(spec);
            if (stack.isEmpty()) {
                clear(inv);
                return new OneResult(false, "unresolved ingredient slot " + i + " = " + spec.id());
            }
            inv.setStackInSlot(i, stack);
        }

        // Exact tier gate for each definition.
        for (int tier = 1; tier <= 3; tier++) {
            boolean expected = tier >= recipe.tier();
            boolean actual = recipe.matches(tier, inv);
            if (actual != expected) {
                clear(inv);
                return new OneResult(false,
                        "tier gate mismatch at T" + tier + " expected=" + expected + " actual=" + actual);
            }
        }

        ItemStack expectedOutput = recipe.output();
        if (expectedOutput.isEmpty()) {
            clear(inv);
            return new OneResult(false, "output id does not resolve");
        }

        List<AssemblyTableRecipeBook.Recipe> matches =
                AssemblyTableRecipeBook.findAll(recipe.tier(), inv);
        if (!matches.contains(recipe)) {
            clear(inv);
            return new OneResult(false, "exact recipe missing from multi-match result");
        }

        int visible = table.showRecipe(recipe);
        if (visible < 0 || visible >= AssemblyTableBlockEntity.OUTPUT_SLOT_COUNT) {
            clear(inv);
            return new OneResult(false, "could not page exact recipe into visible output bank");
        }

        ItemStack actualOutput =
                inv.getStackInSlot(AssemblyTableBlockEntity.OUTPUT_START + visible);
        if (actualOutput.isEmpty()) {
            clear(inv);
            return new OneResult(false, "paged output slot is empty");
        }
        if (!actualOutput.is(expectedOutput.getItem())
                || actualOutput.getCount() != expectedOutput.getCount()) {
            String got = BuiltInRegistries.ITEM.getKey(actualOutput.getItem())
                    + " x" + actualOutput.getCount();
            String want = BuiltInRegistries.ITEM.getKey(expectedOutput.getItem())
                    + " x" + expectedOutput.getCount();
            clear(inv);
            return new OneResult(false, "paged output mismatch got=" + got + " expected=" + want);
        }

        // Exercise the same specific output-slot consumption path as the GUI.
        table.takeCraft(visible);
        for (int i = 0; i < 10; i++) {
            if (!inv.getStackInSlot(i).isEmpty()) {
                String remain = BuiltInRegistries.ITEM.getKey(inv.getStackInSlot(i).getItem())
                        + " x" + inv.getStackInSlot(i).getCount();
                clear(inv);
                return new OneResult(false,
                        "ingredient was not fully consumed in slot " + i + ": " + remain);
            }
        }
        for (int i = AssemblyTableBlockEntity.OUTPUT_START;
             i <= AssemblyTableBlockEntity.OUTPUT_END;
             i++) {
            if (!inv.getStackInSlot(i).isEmpty()) {
                clear(inv);
                return new OneResult(false, "virtual output remained after exact-input craft");
            }
        }

        int matchCount = matches.size();
        clear(inv);
        return new OneResult(true,
                "multi-match/output-page/consume/tier-gate OK choices=" + matchCount);
    }

    private static void clear(ItemStackHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            inv.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    private static ItemStack sampleStack(AssemblyTableRecipeBook.IngredientSpec spec) {
        if ("@dye".equals(spec.id())) return new ItemStack(Items.RED_DYE, spec.count());
        if ("@planks".equals(spec.id())) return new ItemStack(Items.OAK_PLANKS, spec.count());
        if ("@logs".equals(spec.id())) return new ItemStack(Items.OAK_LOG, spec.count());

        ResourceLocation key;
        try {
            key = new ResourceLocation(spec.id());
        } catch (Exception ex) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, spec.count());
    }

    private static int loadSample(ServerPlayer player, int tier) {
        setup(player);
        AssemblyTableBlockEntity table = tableForTier(player, tier);
        if (table == null) {
            player.sendSystemMessage(Component.literal("[TC DEV38] Tier " + tier + " table missing."));
            return 0;
        }

        AssemblyTableRecipeBook.Recipe sample = pickSample(tier);
        if (sample == null) {
            player.sendSystemMessage(Component.literal("[TC DEV38] No Tier " + tier + " recipe found."));
            return 0;
        }

        ItemStackHandler inv = table.getItems();
        clear(inv);
        for (int i = 0; i < 10; i++) {
            AssemblyTableRecipeBook.IngredientSpec spec = sample.ingredients()[i];
            if (spec != null) inv.setStackInSlot(i, sampleStack(spec));
        }
        table.showRecipe(sample);

        player.sendSystemMessage(Component.literal(
                "[TC DEV38] Loaded Tier " + tier + " sample: traincraft:" + sample.outputId()));
        player.sendSystemMessage(Component.literal(
                "[TC DEV38] Open that table; duplicate BOMs appear as choices and < > pages them."));
        return 1;
    }

    private static AssemblyTableRecipeBook.Recipe pickSample(int tier) {
        String[] preferred = tier == 1
                ? new String[]{"br80", "black_5", "black5"}
                : tier == 2
                ? new String[]{"hall", "king"}
                : new String[]{"gs4", "milw"};

        for (String token : preferred) {
            for (AssemblyTableRecipeBook.Recipe recipe : AssemblyTableRecipeBook.all()) {
                if (recipe.tier() == tier && recipe.outputId().contains(token)) return recipe;
            }
        }
        for (AssemblyTableRecipeBook.Recipe recipe : AssemblyTableRecipeBook.all()) {
            if (recipe.tier() == tier) return recipe;
        }
        return null;
    }

    private static int reportPowered(ServerPlayer player) {
        if (!(player.getVehicle() instanceof LegacyPoweredLocomotive locomotive)) {
            player.sendSystemMessage(Component.literal(
                    "[TC DEV38] Ride a Diesel/Electric/Builder locomotive, then run /tcdev powered."));
            return 0;
        }
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(locomotive.getType());
        boolean registryElectric = key != null && key.getPath().startsWith("locomotive_electric_");
        player.sendSystemMessage(Component.literal(
                "[TC DEV38] entity=" + key
                        + " driveType=" + locomotive.driveType()
                        + " isDiesel=" + locomotive.isDiesel()
                        + " isElectric=" + locomotive.isElectric()
                        + " registryElectric=" + registryElectric
                        + " isBuilder=" + locomotive.isBuilder()));
        return 1;
    }
}
