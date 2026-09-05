package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IEnergyItem;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Real vanilla menu extraction and XP pickup, with ordinary sword positive controls. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class EnergyRepairGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Fixed membership: selecting only damageable items would silently skip the repaired implementation.
    private static final List<String> ITEMS = List.of("energy_unit", "developer_portable", "ray_twister",
            "energy_unit_group", "electricalibur", "avalon", "lasor_gun", "air_jet", "teleporter", "drop_item_magnet");

    private EnergyRepairGameTests() {}

    @GameTest(template = "empty")
    public static void craftingCannotCreateEnergy(GameTestHelper helper) { rejectRepair(helper, "crafting"); }

    @GameTest(template = "empty")
    public static void grindstoneCannotCreateEnergy(GameTestHelper helper) { rejectRepair(helper, "grindstone"); }

    @GameTest(template = "empty")
    public static void anvilCannotCreateEnergy(GameTestHelper helper) { rejectRepair(helper, "anvil"); }

    private static void rejectRepair(GameTestHelper helper, String kind) {
        var player = player(helper);
        var violations = new ArrayList<String>();
        for (String id : ITEMS) {
            ItemStack input = energy(id, 0);
            var menu = menu(helper, player, kind);
            try {
                menu.getSlot(first(kind)).set(input.copy());
                menu.getSlot(first(kind) + 1).set(input.copy());
                menu.clicked(result(kind), 0, ClickType.PICKUP, player);
                ItemStack taken = menu.getCarried();
                int produced = EnergyItemHelper.getEnergy(taken);
                LOGGER.info("ENERGY_REPAIR_OBSERVATION {} academy:{} input=0+0 outputCount={} outputIF={}",
                        kind, id, taken.getCount(), produced);
                if (!taken.isEmpty()) violations.add(id + " extracted " + produced + " IF from two empty devices");
                if (taken.isEmpty()) {
                    check(helper, menu.getSlot(first(kind)).getItem().getCount() == 1
                                    && menu.getSlot(first(kind) + 1).getItem().getCount() == 1,
                            kind + " consumed rejected energy ingredients: " + id);
                }
            } finally { close(player, menu); }
        }
        check(helper, violations.isEmpty(), kind + " energy repair: " + violations);
        LOGGER.info("ENERGY_REPAIR_PASS {} all ten devices rejected, inputs retained", kind);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaRepairMenusStillWork(GameTestHelper helper) {
        var player = player(helper);
        for (String kind : List.of("crafting", "grindstone", "anvil")) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.setDamageValue(sword.getMaxDamage() - 1);
            var menu = menu(helper, player, kind);
            try {
                menu.getSlot(first(kind)).set(sword.copy());
                menu.getSlot(first(kind) + 1).set(sword.copy());
                menu.clicked(result(kind), 0, ClickType.PICKUP, player);
                ItemStack taken = menu.getCarried();
                check(helper, taken.is(Items.IRON_SWORD) && taken.getDamageValue() < sword.getDamageValue(),
                        kind + " positive control could not extract a repaired iron sword");
                check(helper, menu.getSlot(first(kind)).getItem().isEmpty()
                        && menu.getSlot(first(kind) + 1).getItem().isEmpty(), kind + " did not debit both swords");
            } finally { close(player, menu); }
        }
        LOGGER.info("ENERGY_REPAIR_CONTROL_PASS vanilla sword crafting+grindstone+anvil extraction");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void mendingCannotChargeEnergy(GameTestHelper helper) {
        var player = player(helper);
        var mending = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.MENDING);
        var violations = new ArrayList<String>();
        for (String id : ITEMS) {
            ItemStack stack = energy(id, 0);
            // Simulate a previously enchanted saved stack, independent of enchanting-table eligibility.
            stack.enchant(mending, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            int xpBefore = player.totalExperience;
            player.takeXpDelay = 0;
            new ExperienceOrb(helper.getLevel(), player.getX(), player.getY(), player.getZ(), 5).playerTouch(player);
            int gained = EnergyItemHelper.getEnergy(stack);
            LOGGER.info("ENERGY_MENDING_OBSERVATION academy:{} inputIF=0 outputIF={} xpBefore={} xpAfter={}",
                    id, gained, xpBefore, player.totalExperience);
            if (gained != 0 || player.totalExperience != xpBefore + 5) violations.add(id + " energy=" + gained
                    + ", XP=" + (player.totalExperience - xpBefore));
        }
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.setDamageValue(100);
        sword.enchant(mending, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.takeXpDelay = 0;
        new ExperienceOrb(helper.getLevel(), player.getX(), player.getY(), player.getZ(), 5).playerTouch(player);
        check(helper, sword.getDamageValue() < 100, "ordinary mending sword positive control did not repair");
        player.getInventory().clearContent();
        check(helper, violations.isEmpty(), "Mending converted XP to IF or swallowed XP: " + violations);
        LOGGER.info("ENERGY_MENDING_PASS all ten unchanged IF, XP awarded, ordinary sword repaired");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyDamagePreservesEnergyAndDisplay(GameTestHelper helper) {
        var player = player(helper);
        for (String id : ITEMS) {
            ItemStack prototype = energy(id, 0);
            int capacity = ((IEnergyItem) prototype.getItem()).getMaxEnergyStored(prototype);
            // Pre-fix 0.0.17 item encoding had only an optional minecraft:damage patch.
            CompoundTag saved = new CompoundTag();
            saved.putString("id", "academy:" + id);
            saved.putInt("count", 1);
            CompoundTag components = new CompoundTag();
            components.putInt("minecraft:damage", capacity - 321);
            saved.put("components", components);
            ItemStack loaded = ItemStack.parseOptional(helper.getLevel().registryAccess(), saved);
            check(helper, loaded.is(prototype.getItem()) && EnergyItemHelper.getEnergy(loaded) == 321,
                    id + " lost legacy DAMAGE energy on decode");
            ItemStack roundTrip = ItemStack.parseOptional(helper.getLevel().registryAccess(),
                    (CompoundTag) loaded.save(helper.getLevel().registryAccess()));
            check(helper, EnergyItemHelper.getEnergy(roundTrip) == 321, id + " lost energy on save/load");
            var fe = loaded.getCapability(Capabilities.EnergyStorage.ITEM);
            check(helper, fe != null && fe.getEnergyStored() == 321 * 4 && fe.getMaxEnergyStored() == capacity * 4,
                    id + " FE capability disagrees with legacy IF");
            check(helper, fe.extractEnergy(28, true) == 28 && EnergyItemHelper.getEnergy(loaded) == 321,
                    id + " simulated FE extraction changed energy");
            check(helper, fe.extractEnergy(28, false) == 28 && EnergyItemHelper.getEnergy(loaded) == 314,
                    id + " FE extraction failed");
            check(helper, fe.receiveEnergy(44, true) == 44 && EnergyItemHelper.getEnergy(loaded) == 314,
                    id + " simulated FE receive changed energy");
            check(helper, fe.receiveEnergy(44, false) == 44 && EnergyItemHelper.getEnergy(loaded) == 325,
                    id + " FE receive failed");
            EnergyItemHelper.setEnergy(loaded, -1);
            check(helper, EnergyItemHelper.getEnergy(loaded) == 0 && loaded.getBarWidth() == 0 && loaded.isBarVisible(),
                    id + " empty energy clamp/bar failed");
            EnergyItemHelper.setEnergy(loaded, capacity / 2);
            check(helper, loaded.getBarWidth() == 7 && loaded.isBarVisible(), id + " half energy bar failed");
            String tooltip = loaded.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), player, TooltipFlag.ADVANCED)
                    .toString().toLowerCase(java.util.Locale.ROOT);
            check(helper, tooltip.contains((capacity / 2) + "/" + capacity + " if")
                    && !tooltip.contains("unbreakable"), id + " hidden durability isolation leaked or IF tooltip missing: " + tooltip);
            EnergyItemHelper.setEnergy(loaded, Integer.MAX_VALUE);
            check(helper, EnergyItemHelper.getEnergy(loaded) == capacity && loaded.getBarWidth() == 13
                    && !loaded.isBarVisible(), id + " full energy clamp/bar failed");
        }
        LOGGER.info("ENERGY_COMPATIBILITY_PASS ten old DAMAGE decodes+roundtrips, FE simulation/extract/receive, bars, IF tooltips");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anvilRenamePreservesEnergy(GameTestHelper helper) {
        var player = player(helper);
        for (String id : ITEMS) {
            var menu = (AnvilMenu) menu(helper, player, "anvil");
            try {
                menu.getSlot(0).set(energy(id, 321));
                menu.setItemName("Charged " + id);
                menu.clicked(2, 0, ClickType.PICKUP, player);
                ItemStack taken = menu.getCarried();
                check(helper, !taken.isEmpty() && EnergyItemHelper.getEnergy(taken) == 321
                        && taken.getHoverName().getString().equals("Charged " + id), id + " rename lost energy or output");
                check(helper, menu.getSlot(0).getItem().isEmpty(), id + " rename duplicated input");
            } finally { close(player, menu); }
        }
        LOGGER.info("ENERGY_RENAME_PASS all ten real anvil outputs retained 321 IF");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grindstoneDisenchantPreservesEnergy(GameTestHelper helper) {
        var player = player(helper);
        var mending = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.MENDING);
        for (String id : ITEMS) {
            var menu = menu(helper, player, "grindstone");
            try {
                ItemStack stack = energy(id, 321);
                stack.enchant(mending, 1);
                menu.getSlot(0).set(stack);
                menu.clicked(2, 0, ClickType.PICKUP, player);
                ItemStack taken = menu.getCarried();
                check(helper, !taken.isEmpty() && EnergyItemHelper.getEnergy(taken) == 321 && !taken.isEnchanted(),
                        id + " disenchant lost energy or output");
                check(helper, menu.getSlot(0).getItem().isEmpty(), id + " disenchant duplicated input");
            } finally { close(player, menu); }
        }
        LOGGER.info("ENERGY_DISENCHANT_PASS all ten real grindstone outputs retained 321 IF");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[EnergyRepair]"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static AbstractContainerMenu menu(GameTestHelper helper, ServerPlayer player, String kind) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        var level = helper.getLevel();
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        player.experienceLevel = 100;
        var access = ContainerLevelAccess.create(level, pos);
        AbstractContainerMenu menu;
        switch (kind) {
            case "crafting" -> { level.setBlock(pos, Blocks.CRAFTING_TABLE.defaultBlockState(), 3); menu = new CraftingMenu(61, player.getInventory(), access); }
            case "grindstone" -> { level.setBlock(pos, Blocks.GRINDSTONE.defaultBlockState(), 3); menu = new GrindstoneMenu(62, player.getInventory(), access); }
            case "anvil" -> { level.setBlock(pos, Blocks.ANVIL.defaultBlockState(), 3); menu = new AnvilMenu(63, player.getInventory(), access); }
            default -> throw new IllegalArgumentException(kind);
        }
        player.containerMenu = menu;
        return menu;
    }

    private static int first(String kind) { return kind.equals("crafting") ? 1 : 0; }
    private static int result(String kind) { return kind.equals("crafting") ? 0 : 2; }

    private static void close(ServerPlayer player, AbstractContainerMenu menu) {
        menu.setCarried(ItemStack.EMPTY);
        menu.removed(player);
        player.containerMenu = player.inventoryMenu;
        player.getInventory().clearContent();
    }

    private static ItemStack energy(String id, int amount) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy", id)));
        if (!(stack.getItem() instanceof IEnergyItem)) throw new IllegalArgumentException("Not an energy device: " + id);
        EnergyItemHelper.setEnergy(stack, amount);
        return stack;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
