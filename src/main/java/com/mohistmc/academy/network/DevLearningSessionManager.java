package com.mohistmc.academy.network;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.item.BaseFactor;
import com.mohistmc.academy.world.item.DeveloperPortable;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One-shot, server-owned authorization for a skill-tree mutation. */
public final class DevLearningSessionManager {
    // Selecting a category, reading prerequisites and comparing five skill
    // columns routinely takes longer than thirty seconds.  Keep the one-shot
    // and exact-machine checks, but allow a normal five-minute UI session so
    // legitimate actions do not silently expire while the screen is open.
    private static final long LIFETIME_TICKS = 20L * 60L * 5L;
    /** AcademyCraft 1.0.7 DevelopActionLevel used five stimulations at level zero. */
    public static final int INDUCTION_STIMULATIONS = 5;
    private static final OneShotSessionLedger<SessionContext> SESSIONS = new OneShotSessionLedger<>();
    private static final Map<UUID, Development> ACTIVE = new ConcurrentHashMap<>();

    private DevLearningSessionManager() {}

    private record SessionContext(DevMachineType type, Optional<BlockPos> pos,
                                  ResourceKey<Level> dimension) {}
    private static final class Development {
        final UUID nonce; final DevMachineType type; final Optional<BlockPos> pos; final String skillId;
        final AbilityCategory skillCategory;
        final InductionSelection induction;
        final ResetSelection reset;
        final int expectedLevel;
        final int maxStimulations; int ticks; int stimulations;
        Development(UUID nonce, DevMachineType type, Optional<BlockPos> pos, String skillId,
                    AbilityCategory skillCategory, int maxStimulations) {
            this(nonce, type, pos, skillId, skillCategory, maxStimulations, -1, null, null);
        }
        Development(UUID nonce, DevMachineType type, Optional<BlockPos> pos, String skillId,
                    AbilityCategory skillCategory, int maxStimulations, InductionSelection induction) {
            this(nonce, type, pos, skillId, skillCategory, maxStimulations, -1, induction, null);
        }
        Development(UUID nonce, DevMachineType type, Optional<BlockPos> pos, String skillId,
                    AbilityCategory skillCategory, int maxStimulations, int expectedLevel) {
            this(nonce, type, pos, skillId, skillCategory, maxStimulations, expectedLevel, null, null);
        }
        Development(UUID nonce, DevMachineType type, Optional<BlockPos> pos, String skillId,
                    AbilityCategory skillCategory, int maxStimulations, ResetSelection reset) {
            this(nonce, type, pos, skillId, skillCategory, maxStimulations,
                    reset.expectedLevel(), null, reset);
        }
        private Development(UUID nonce, DevMachineType type, Optional<BlockPos> pos, String skillId,
                    AbilityCategory skillCategory, int maxStimulations, int expectedLevel,
                    InductionSelection induction, ResetSelection reset) {
            this.nonce=nonce; this.type=type; this.pos=pos.map(BlockPos::immutable);
            this.skillId=skillId; this.skillCategory=skillCategory;
            this.maxStimulations=maxStimulations; this.expectedLevel=expectedLevel; this.induction=induction;
            this.reset=reset;
        }
    }

    /**
     * Immutable server-side lock for the exact factor selected when induction
     * starts.  Identity and count checks deliberately reject remove/replace
     * races instead of silently consuming a different item on completion.
     */
    static final class InductionSelection {
        private enum Source { NONE, PLAYER_INVENTORY }
        private final AbilityCategory category;
        private final Source source;
        private final int inventorySlot;
        private final ItemStack expectedStack;
        private final int expectedCount;

        private InductionSelection(AbilityCategory category, Source source, int inventorySlot,
                                   ItemStack expectedStack) {
            this.category = category;
            this.source = source;
            this.inventorySlot = inventorySlot;
            this.expectedStack = expectedStack;
            this.expectedCount = expectedStack.isEmpty() ? 0 : expectedStack.getCount();
        }

        AbilityCategory category() { return category; }
        boolean factorBacked() { return source != Source.NONE; }

        private ItemStack current(ServerPlayer player, DevMachineType type, Optional<BlockPos> pos) {
            if (source == Source.NONE) return ItemStack.EMPTY;
            if (inventorySlot < 0 || inventorySlot >= player.getInventory().items.size()) return ItemStack.EMPTY;
            return player.getInventory().items.get(inventorySlot);
        }

        boolean stillPresent(ServerPlayer player, DevMachineType type, Optional<BlockPos> pos) {
            if (source == Source.NONE) return true;
            ItemStack current = current(player, type, pos);
            return current == expectedStack && !current.isEmpty() && current.getCount() == expectedCount
                    && current.getItem() instanceof BaseFactor factor
                    && factor.getCategory().equals(category);
        }

        boolean consume(ServerPlayer player, DevMachineType type, Optional<BlockPos> pos) {
            if (source == Source.NONE) return true;
            if (!stillPresent(player, type, pos)) return false;
            expectedStack.shrink(1);
            player.getInventory().setChanged();
            return true;
        }
    }

    /** Exact hand/inventory selection used by 1.0.7 DevelopActionReset. */
    static final class ResetSelection {
        private final AbilityCategory oldCategory;
        private final AbilityCategory newCategory;
        private final int expectedLevel;
        private final int factorSlot;
        private final ItemStack expectedCoil;
        private final ItemStack expectedFactor;

        private ResetSelection(AbilityCategory oldCategory, AbilityCategory newCategory,
                               int expectedLevel, int factorSlot,
                               ItemStack expectedCoil, ItemStack expectedFactor) {
            this.oldCategory = oldCategory;
            this.newCategory = newCategory;
            this.expectedLevel = expectedLevel;
            this.factorSlot = factorSlot;
            this.expectedCoil = expectedCoil;
            this.expectedFactor = expectedFactor;
        }

        AbilityCategory oldCategory() { return oldCategory; }
        AbilityCategory newCategory() { return newCategory; }
        int expectedLevel() { return expectedLevel; }

        boolean stillPresent(ServerPlayer player) {
            if (factorSlot < 0 || factorSlot >= player.getInventory().items.size()) return false;
            ItemStack coil = player.getMainHandItem();
            ItemStack factorStack = player.getInventory().items.get(factorSlot);
            return coil == expectedCoil && coil.getCount() == 1
                    && coil.is(AcademyItems.MAGNETIC_COIL.get())
                    && factorStack == expectedFactor && factorStack.getCount() == 1
                    && factorStack.getItem() instanceof BaseFactor factor
                    && factor.getCategory() == newCategory;
        }

        boolean consume(ServerPlayer player) {
            if (!stillPresent(player)) return false;
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.getInventory().items.set(factorSlot, ItemStack.EMPTY);
            player.getInventory().setChanged();
            return true;
        }
    }

    private static SessionContext context(ServerPlayer player, DevMachineType type, Optional<BlockPos> pos) {
        return new SessionContext(type, pos.map(BlockPos::immutable), player.level().dimension());
    }

    public static UUID issue(ServerPlayer player, DevMachineType type, Optional<BlockPos> pos) {
        return SESSIONS.issue(player.getUUID(), context(player, type, pos),
                player.serverLevel().getGameTime() + LIFETIME_TICKS);
    }

    /** Read-only authorization check. Semantic failures must not burn the open UI session. */
    public static boolean validate(ServerPlayer player, UUID nonce, DevMachineType type, Optional<BlockPos> pos) {
        return SESSIONS.validate(player.getUUID(), nonce, context(player, type, pos),
                player.serverLevel().getGameTime()) && (type != DevMachineType.PORTABLE
                || player.getMainHandItem().getItem() instanceof DeveloperPortable);
    }

    /** One-shot compare-and-remove at the mutation commit boundary. */
    public static boolean commit(ServerPlayer player, UUID nonce, DevMachineType type, Optional<BlockPos> pos) {
        return validate(player, nonce, type, pos) && SESSIONS.commit(player.getUUID(), nonce,
                context(player, type, pos), player.serverLevel().getGameTime());
    }

    /** Starts the legacy 1.0.7 stimulation process after consuming the UI nonce exactly once. */
    public static boolean start(ServerPlayer player, UUID nonce, DevMachineType type,
                                Optional<BlockPos> pos, String skillId, AbilityCategory skillCategory, int level) {
        if (ACTIVE.containsKey(player.getUUID()) || !commit(player, nonce, type, pos)) return false;
        int stimulations = (int) (3 + level * level * .5f);
        ACTIVE.put(player.getUUID(), new Development(nonce, type, pos, skillId, skillCategory, stimulations));
        LearnSkillPacket.progress(player, nonce, type, pos, 0, stimulations, "开发中 0/" + stimulations);
        return true;
    }

    /** Starts a genuine level-zero induction run and locks its category/source. */
    static InductionSelection startInduction(ServerPlayer player, UUID nonce, DevMachineType type,
                                             Optional<BlockPos> pos) {
        if (ACTIVE.containsKey(player.getUUID())) return null;
        InductionSelection selection = captureInductionSelection(player, type, pos);
        if (selection == null || !commit(player, nonce, type, pos)) return null;
        ACTIVE.put(player.getUUID(), new Development(nonce, type, pos,
                LearnSkillPacket.INDUCTION_ACTION, selection.category(), INDUCTION_STIMULATIONS, selection));
        LearnSkillPacket.progress(player, nonce, type, pos, 0, INDUCTION_STIMULATIONS,
                "开发中 能力诱导 0/" + INDUCTION_STIMULATIONS);
        return selection;
    }

    /** Starts the distinct 1.0.7 DevelopActionLevel process after the gauge is full. */
    static boolean startLevelUp(ServerPlayer player, UUID nonce, DevMachineType type,
                                Optional<BlockPos> pos, AbilityCategory category, int expectedLevel) {
        if (ACTIVE.containsKey(player.getUUID()) || !commit(player, nonce, type, pos)) return false;
        int stimulations = 5 * (expectedLevel + 1);
        ACTIVE.put(player.getUUID(), new Development(nonce, type, pos,
                LearnSkillPacket.LEVEL_UP_ACTION, category, stimulations, expectedLevel));
        LearnSkillPacket.progress(player, nonce, type, pos, 0, stimulations,
                "开发中 等级提升 0/" + stimulations);
        return true;
    }

    /** Starts the asynchronous 1.0.7 advanced-developer ability reset. */
    static ResetSelection startReset(ServerPlayer player, UUID nonce, DevMachineType type,
                                     Optional<BlockPos> pos) {
        if (type != DevMachineType.ADVANCED || ACTIVE.containsKey(player.getUUID())) return null;
        ResetSelection selection = captureResetSelection(player);
        if (selection == null || !commit(player, nonce, type, pos)) return null;
        int stimulations = selection.expectedLevel() * 10;
        ACTIVE.put(player.getUUID(), new Development(nonce, type, pos,
                LearnSkillPacket.RESET_ACTION, selection.newCategory(), stimulations, selection));
        LearnSkillPacket.progress(player, nonce, type, pos, 0, stimulations,
                "开发中 能力重置 0/" + stimulations);
        return selection;
    }

    private static InductionSelection captureInductionSelection(ServerPlayer player, DevMachineType type,
                                                                 Optional<BlockPos> pos) {
        // 1.0.7 searches only the player's main inventory.  Developer blocks
        // never owned factor slots.
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BaseFactor factor) {
                return new InductionSelection(factor.getCategory(), InductionSelection.Source.PLAYER_INVENTORY,
                        i, stack);
            }
        }
        ArrayList<AbilityCategory> categories = new ArrayList<>(AbilityCategory.all());
        if (categories.isEmpty()) return null;
        AbilityCategory random = categories.get(player.getRandom().nextInt(categories.size()));
        return new InductionSelection(random, InductionSelection.Source.NONE, -1, ItemStack.EMPTY);
    }

    private static ResetSelection captureResetSelection(ServerPlayer player) {
        var data = player.getData(com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility() || data.getPlayerLevel() < 3
                || !player.getMainHandItem().is(AcademyItems.MAGNETIC_COIL.get())) return null;
        AbilityCategory oldCategory = data.getCurrentAbility();
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BaseFactor factor
                    && factor.getCategory() != oldCategory) {
                return new ResetSelection(oldCategory, factor.getCategory(), data.getPlayerLevel(),
                        i, player.getMainHandItem(), stack);
            }
        }
        return null;
    }

    /** Server-authoritative legacy cadence: CPS energy is spread over TPS ticks per stimulation. */
    public static void tick(ServerPlayer player) {
        Development d = ACTIVE.get(player.getUUID());
        if (d == null) return;
        if (d.induction != null && !d.induction.stillPresent(player, d.type, d.pos)) {
            ACTIVE.remove(player.getUUID());
            LearnSkillPacket.progress(player,d.nonce,d.type,d.pos,d.stimulations,d.maxStimulations,
                    "开发失败：锁定的能力诱导因子已被移走或替换");
            return;
        }
        if (d.reset != null && !d.reset.stillPresent(player)) {
            ACTIVE.remove(player.getUUID());
            LearnSkillPacket.progress(player,d.nonce,d.type,d.pos,d.stimulations,d.maxStimulations,
                    "开发失败：重置线圈或诱导因子已被移走或替换");
            return;
        }
        int debit = d.type.energyPerTick();
        if (!LearnSkillPacket.validateEnergySource(player, d.type, debit, d.pos.orElse(null))
                || !LearnSkillPacket.consumeEnergy(player, d.type, debit, d.pos.orElse(null))) {
            ACTIVE.remove(player.getUUID());
            LearnSkillPacket.progress(player,d.nonce,d.type,d.pos,d.stimulations,d.maxStimulations,"开发失败：能量不足或开发机失效");
            return;
        }
        d.ticks++;
        if (d.ticks % d.type.developmentTicksPerStimulation() == 0) d.stimulations++;
        if (d.stimulations >= d.maxStimulations) {
            ACTIVE.remove(player.getUUID());
            if (d.induction != null) {
                LearnSkillPacket.completeInduction(player,d.nonce,d.type,d.pos,d.induction);
            } else if (d.reset != null) {
                LearnSkillPacket.completeReset(player,d.nonce,d.type,d.pos,d.reset);
            } else if (LearnSkillPacket.LEVEL_UP_ACTION.equals(d.skillId)) {
                LearnSkillPacket.completeLevelUp(player,d.nonce,d.type,d.pos,d.skillCategory,d.expectedLevel);
            } else {
                LearnSkillPacket.completeDevelopment(player,d.nonce,d.type,d.pos,d.skillId,d.skillCategory);
            }
        } else if (d.ticks % 5 == 0) {
            String action = d.induction != null ? "能力诱导 " : d.reset != null ? "能力重置 "
                    : LearnSkillPacket.LEVEL_UP_ACTION.equals(d.skillId) ? "等级提升 " : "";
            LearnSkillPacket.progress(player,d.nonce,d.type,d.pos,d.stimulations,d.maxStimulations,
                    "开发中 " + action + d.stimulations + "/" + d.maxStimulations);
        }
    }

    /** A stale close packet may only revoke the exact screen that emitted it. */
    public static void clear(UUID playerId, UUID nonce) {
        SESSIONS.clear(playerId, nonce);
    }

    public static void clear(UUID playerId) {
        SESSIONS.clear(playerId);
        ACTIVE.remove(playerId);
    }

    /** Integrated-server restart safety for process-static authorization/development state. */
    public static void clearAll() {
        SESSIONS.clearAll();
        ACTIVE.clear();
    }

    public static void clearExpired(ServerPlayer player) {
        SESSIONS.clearExpired(player.getUUID(), player.serverLevel().getGameTime());
    }
}
