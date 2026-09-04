package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.world.block.DevMachineType;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mohistmc.academy.world.menu.DevAdvancedMenu;
import com.mohistmc.academy.world.menu.DevNormalMenu;
import com.mohistmc.academy.world.item.DeveloperPortable;
import com.mohistmc.academy.world.item.BaseFactor;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.AbilityEvents;

public record LearnSkillPacket(String skillId, int typeOrdinal, Optional<BlockPos> devPos, UUID nonce) implements CustomPacketPayload {

    private static final int MAX_SKILL_ID_LENGTH = 64;
    /** Reserved action id: explicit level-zero developer induction, never a skill registry id. */
    public static final String INDUCTION_ACTION = "__induct__";
    public static final String LEVEL_UP_ACTION = "__level_up__";
    /** Reserved action id for 1.0.7 DevelopActionReset. */
    public static final String RESET_ACTION = "__reset__";
    private static final StreamCodec<ByteBuf, String> SKILL_ID_CODEC =
            ByteBufCodecs.stringUtf8(MAX_SKILL_ID_LENGTH);

    public static final Type<LearnSkillPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "learn_skill"));

    public static final StreamCodec<ByteBuf, LearnSkillPacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                SKILL_ID_CODEC.encode(buf, packet.skillId());
                buf.writeInt(packet.typeOrdinal());
                buf.writeBoolean(packet.devPos().isPresent());
                packet.devPos().ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buf, pos));
                buf.writeLong(packet.nonce().getMostSignificantBits());
                buf.writeLong(packet.nonce().getLeastSignificantBits());
            },
            buf -> new LearnSkillPacket(SKILL_ID_CODEC.decode(buf), buf.readInt(),
                    buf.readBoolean() ? Optional.of(BlockPos.STREAM_CODEC.decode(buf)) : Optional.empty(),
                    new UUID(buf.readLong(), buf.readLong())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LearnSkillPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (packet.typeOrdinal() < 0 || packet.typeOrdinal() >= DevMachineType.values().length) {
                return;
            }
            DevMachineType devType = DevMachineType.fromOrdinal(packet.typeOrdinal());
            if (!DevLearningSessionManager.validate(player, packet.nonce(), devType, packet.devPos())) {
                result(player, packet, devType, false, "开发会话已失效，请重新打开机器"); return;
            }

            if (INDUCTION_ACTION.equals(packet.skillId())) {
                if (data.hasAbility()) {
                    result(player, packet, devType, false, "能力诱导失败：你已经拥有能力");
                    return;
                }
                BlockPos inductionPos = packet.devPos().orElse(null);
                if (!validateEnergySource(player, devType, devType.energyPerTick(), inductionPos)) {
                    result(player, packet, devType, false, "无法开始：开发机当前不可用或能量不足");
                    return;
                }
                DevLearningSessionManager.InductionSelection selection =
                        DevLearningSessionManager.startInduction(player, packet.nonce(), devType, packet.devPos());
                if (selection == null) {
                    result(player, packet, devType, false, "已有开发正在进行，或请求已处理");
                    return;
                }
                int totalCost = devType.actualEnergyPerStimulation()
                        * DevLearningSessionManager.INDUCTION_STIMULATIONS;
                player.sendSystemMessage(Component.literal("§b开始能力诱导：")
                        .append(Component.translatable(selection.category().getTranslationKey()))
                        .append(Component.literal(selection.factorBacked()
                                ? "（已锁定诱导因子）" : "（无因子，随机结果已锁定）"))
                        .append(Component.literal("，预计 " + totalCost + " IF")));
                return;
            }

            if (LEVEL_UP_ACTION.equals(packet.skillId())) {
                if (!data.hasAbility() || !data.canLevelUp()) {
                    result(player, packet, devType, false, "等级进度尚未完成，或已达到 Lv.5");
                    return;
                }
                int level = data.getPlayerLevel();
                int stimulations = 5 * (level + 1);
                int totalCost = devType.actualEnergyPerStimulation() * stimulations;
                if (!validateEnergySource(player, devType, devType.energyPerTick(), packet.devPos().orElse(null))) {
                    result(player, packet, devType, false, "无法升级：开发机当前不可用或能量不足");
                    return;
                }
                if (!DevLearningSessionManager.startLevelUp(player, packet.nonce(), devType,
                        packet.devPos(), data.getCurrentAbility(), level)) {
                    result(player, packet, devType, false, "已有开发正在进行，或请求已处理");
                    return;
                }
                player.sendSystemMessage(Component.literal("§b开始能力等级提升：Lv." + level
                        + " → Lv." + (level + 1) + "，预计 " + totalCost + " IF"));
                return;
            }

            if (RESET_ACTION.equals(packet.skillId())) {
                if (devType != DevMachineType.ADVANCED || !data.hasAbility()
                        || data.getPlayerLevel() < 3) {
                    result(player, packet, devType, false,
                            "重置失败：需要高级开发机和至少 Level 3");
                    return;
                }
                if (!player.getMainHandItem().is(AcademyItems.MAGNETIC_COIL.get())) {
                    result(player, packet, devType, false, "重置失败：请主手持高压磁增幅线圈");
                    return;
                }
                boolean hasDifferentFactor = player.getInventory().items.stream().anyMatch(stack ->
                        stack.getItem() instanceof BaseFactor factor
                                && factor.getCategory() != data.getCurrentAbility());
                if (!hasDifferentFactor) {
                    result(player, packet, devType, false,
                            "重置失败：背包中需要与当前能力不同的诱导因子");
                    return;
                }
                if (!validateEnergySource(player, devType, devType.energyPerTick(),
                        packet.devPos().orElse(null))) {
                    result(player, packet, devType, false,
                            "无法重置：开发机当前不可用或能量不足");
                    return;
                }
                DevLearningSessionManager.ResetSelection selection =
                        DevLearningSessionManager.startReset(player, packet.nonce(), devType, packet.devPos());
                if (selection == null) {
                    result(player, packet, devType, false,
                            "重置条件已变化、已有开发正在进行，或请求已处理");
                    return;
                }
                int stimulations = selection.expectedLevel() * 10;
                int totalCost = devType.actualEnergyPerStimulation() * stimulations;
                player.sendSystemMessage(Component.literal("§b开始能力重置：")
                        .append(Component.translatable(selection.oldCategory().getTranslationKey()))
                        .append(Component.literal(" → "))
                        .append(Component.translatable(selection.newCategory().getTranslationKey()))
                        .append(Component.literal("，预计 " + totalCost + " IF")));
                return;
            }

            Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), packet.skillId());
            if (skill == null) {
                player.sendSystemMessage(Component.literal("§c未知技能: " + packet.skillId()));
                result(player, packet, devType, false, "未知技能");
                return;
            }

            if (data.hasLearnedSkill(skill.getId())) {
                player.sendSystemMessage(Component.literal("§c已学习: ").append(Component.translatable(skill.getTranslationKey())));
                result(player, packet, devType, false, "该技能已经学习");
                return;
            }

            if (!data.canLearnSkill(skill)) {
                player.sendSystemMessage(Component.literal("§c前置条件未满足: ").append(Component.translatable(skill.getTranslationKey())));
                result(player, packet, devType, false, "前置技能、等级或熟练度不足");
                return;
            }

            if (skill.getLevel() > devType.maxLevel) {
                player.sendSystemMessage(Component.literal("§c同步率不足，该开发机无法支持 Lv." + skill.getLevel() + " 技能"));
                result(player, packet, devType, false, "开发机同步率不足，无法学习该等级技能");
                return;
            }

            BlockPos devPos = packet.devPos().orElse(null);
            int stimulations = (int) (3 + skill.getLevel() * skill.getLevel() * .5f);
            int totalCost = devType.energyPerStimulation * stimulations;
            if (!validateEnergySource(player, devType, devType.energyPerTick(), devPos)) {
                player.sendSystemMessage(Component.literal("§c能量不足或开发会话已失效"));
                result(player, packet, devType, false, "无法开始：开发机当前不可用或能量不足");
                return;
            }
            if (!DevLearningSessionManager.start(player,packet.nonce(),devType,packet.devPos(),
                    skill.getId(), skill.getCategory(), skill.getLevel())) {
                result(player, packet, devType, false, "已有开发正在进行，或请求已处理"); return;
            }
            player.sendSystemMessage(Component.literal("§b开始开发：").append(Component.translatable(skill.getTranslationKey()))
                    .append(Component.literal("，预计 " + totalCost + " IF")));
        });
    }

    private static void result(ServerPlayer player, LearnSkillPacket packet, DevMachineType type,
                               boolean success, String reason) {
        int energy = 0, maximum = 0;
        if (type == DevMachineType.PORTABLE) {
            ItemStack stack = player.getMainHandItem();
            if (stack.getItem() instanceof DeveloperPortable) {
                energy = EnergyItemHelper.getEnergy(stack); maximum = DeveloperPortable.MAX_ENERGY;
            }
        } else if (packet.devPos().isPresent()) {
            BlockEntity be = player.level().getBlockEntity(packet.devPos().get());
            if (be instanceof IFEnergyStorage storage) {
                energy = storage.getEnergyStored(); maximum = storage.getMaxEnergyStored();
            }
        }
        SafePayloadSender.send(player, new DevLearningResultPacket(packet.nonce(), success,
                Math.max(0, energy), Math.max(0, maximum), reason));
    }

    static boolean consumeEnergy(ServerPlayer player, DevMachineType devType, int cost, BlockPos devPos) {
        if (devType == DevMachineType.PORTABLE) {
            ItemStack mainHand = player.getMainHandItem();
            if (mainHand.getItem() instanceof DeveloperPortable) {
                int energy = EnergyItemHelper.getEnergy(mainHand);
                if (energy < cost) {
                    return false;
                }
                return EnergyItemHelper.extractEnergy(mainHand, cost, false) == cost;
            }
            return false;
        } else {
            if (devPos == null) return false;
            Level level = player.level();
            if (!level.isLoaded(devPos)
                    || player.distanceToSqr(devPos.getX() + 0.5, devPos.getY() + 0.5, devPos.getZ() + 0.5) > 64.0
                    || !level.mayInteract(player, devPos)) {
                return false;
            }

            boolean validMenu = switch (devType) {
                case NORMAL -> player.containerMenu instanceof DevNormalMenu menu && devPos.equals(menu.pos);
                case ADVANCED -> player.containerMenu instanceof DevAdvancedMenu menu && devPos.equals(menu.pos);
                case PORTABLE -> false;
            };
            // Existing normal/advanced skill-tree screens are not container screens. A
            // matching live menu is accepted as defense-in-depth but the nonce is the authority.
            if (validMenu && !player.containerMenu.stillValid(player)) return false;
            BlockEntity be = level.getBlockEntity(devPos);
            boolean expectedMachine = switch (devType) {
                case NORMAL -> be instanceof DevNormalBlockEntity;
                case ADVANCED -> be instanceof DevAdvancedBlockEntity;
                case PORTABLE -> false;
            };
            if (!expectedMachine || !(be instanceof IFEnergyStorage storage)) {
                return false;
            }
            // All payload handlers run on the server thread. Checking the exact return
            // value makes the debit atomic with respect to other packet handlers/ticks.
            return storage.extractEnergy(cost, false) == cost;
        }
    }

    static boolean validateEnergySource(ServerPlayer player, DevMachineType devType, int cost, BlockPos devPos) {
        if (devType == DevMachineType.PORTABLE) {
            ItemStack mainHand = player.getMainHandItem();
            return mainHand.getItem() instanceof DeveloperPortable
                    && EnergyItemHelper.getEnergy(mainHand) >= cost;
        }
        if (devPos == null) return false;
        Level level = player.level();
        if (!level.isLoaded(devPos)
                || player.distanceToSqr(devPos.getX() + 0.5, devPos.getY() + 0.5, devPos.getZ() + 0.5) > 64.0
                || !level.mayInteract(player, devPos)) return false;
        BlockEntity be = level.getBlockEntity(devPos);
        return switch (devType) {
            case NORMAL -> be instanceof DevNormalBlockEntity normal && normal.getEnergyStored() >= cost;
            case ADVANCED -> be instanceof DevAdvancedBlockEntity advanced && advanced.getEnergyStored() >= cost;
            case PORTABLE -> false;
        };
    }

    static void progress(ServerPlayer player, UUID nonce, DevMachineType type, Optional<BlockPos> pos,
                         int stimulation, int maximum, String reason) {
        result(player, new LearnSkillPacket("", type.ordinal(), pos, nonce), type, false, reason);
    }

    static void completeDevelopment(ServerPlayer player, UUID nonce, DevMachineType type,
                                    Optional<BlockPos> pos, String skillId, AbilityCategory skillCategory) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        Skill skill = SkillRegistry.getSkill(skillCategory, skillId);
        LearnSkillPacket packet = new LearnSkillPacket(skillId,type.ordinal(),pos,nonce);
        if (data.getCurrentAbility() != skillCategory || skill == null
                || data.hasLearnedSkill(skillId) || !data.canLearnSkill(skill)) {
            result(player,packet,type,false,"开发失败：完成时条件已经变化"); return;
        }
        data.learnSkill(skillId);
        NeoForge.EVENT_BUS.post(new AbilityEvents.SkillLearned(player, skill));
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.learned(player,data,skill);
        data.syncTo(player);
        player.sendSystemMessage(Component.literal("§a开发完成：").append(Component.translatable(skill.getTranslationKey())));
        result(player,packet,type,true,"开发完成：学习成功");
    }

    static void completeLevelUp(ServerPlayer player, UUID nonce, DevMachineType type,
                                Optional<BlockPos> pos, AbilityCategory category, int expectedLevel) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        LearnSkillPacket packet = new LearnSkillPacket(LEVEL_UP_ACTION, type.ordinal(), pos, nonce);
        if (!data.hasAbility() || data.getCurrentAbility() != category
                || data.getPlayerLevel() != expectedLevel || !data.canLevelUp()) {
            result(player, packet, type, false, "升级失败：完成时等级或进度已经变化");
            return;
        }
        int next = expectedLevel + 1;
        data.setPlayerLevel(next);
        NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, expectedLevel, next));
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.levels(player, data);
        data.syncTo(player);
        player.sendSystemMessage(Component.literal("§a能力等级提升完成：Lv." + next));
        result(player, packet, type, true, "等级提升完成：Lv." + next);
    }

    /** Commit the old reset only after level*10 stimulations complete. */
    static void completeReset(ServerPlayer player, UUID nonce, DevMachineType type,
                              Optional<BlockPos> pos,
                              DevLearningSessionManager.ResetSelection selection) {
        LearnSkillPacket packet = new LearnSkillPacket(RESET_ACTION, type.ordinal(), pos, nonce);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (type != DevMachineType.ADVANCED || !data.hasAbility()
                || data.getCurrentAbility() != selection.oldCategory()
                || data.getPlayerLevel() != selection.expectedLevel()
                || !selection.stillPresent(player) || !selection.consume(player)) {
            result(player, packet, type, false, "能力重置失败：完成时材料、能力或等级已经变化");
            return;
        }

        AbilityCategory oldCategory = selection.oldCategory();
        int oldLevel = selection.expectedLevel();
        int newLevel = oldLevel - 1;
        data.reset();
        data.setCurrentAbility(selection.newCategory());
        data.setPlayerLevel(newLevel);
        NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory,
                selection.newCategory()));
        NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, newLevel));
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.levels(player, data);
        data.syncTo(player);
        player.sendSystemMessage(Component.literal("§a能力重置完成：")
                .append(Component.translatable(selection.newCategory().getTranslationKey()))
                .append(Component.literal("，当前 Level " + newLevel)));
        result(player, packet, type, true, "能力重置完成");
    }

    /** Commit a factor/random category only after all five server ticks groups complete. */
    static void completeInduction(ServerPlayer player, UUID nonce, DevMachineType type,
                                  Optional<BlockPos> pos,
                                  DevLearningSessionManager.InductionSelection selection) {
        LearnSkillPacket packet = new LearnSkillPacket(INDUCTION_ACTION, type.ordinal(), pos, nonce);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (data.hasAbility()) {
            result(player, packet, type, false, "能力诱导失败：完成时能力状态已经变化");
            return;
        }
        if (!selection.stillPresent(player, type, pos) || !selection.consume(player, type, pos)) {
            result(player, packet, type, false, "能力诱导失败：锁定的诱导因子已被移走或替换");
            return;
        }

        AbilityCategory oldCategory = data.getCurrentAbility();
        int oldLevel = data.getPlayerLevel();
        data.reset();
        data.setCurrentAbility(selection.category());
        data.setPlayerLevel(1);
        NeoForge.EVENT_BUS.post(new AbilityEvents.CategoryChanged(player, oldCategory, selection.category()));
        NeoForge.EVENT_BUS.post(new AbilityEvents.LevelChanged(player, oldLevel, 1));
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.levels(player, data);
        data.syncTo(player);
        player.sendSystemMessage(Component.literal("§a能力诱导完成：")
                .append(Component.translatable(selection.category().getTranslationKey())));
        result(player, packet, type, true, "能力诱导完成");
    }

    private static int getSkillCost(Skill skill) {
        return 100 + skill.getLevel() * 50;
    }

    public static void syncToClient(ServerPlayer player) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        syncToClient(player, data);
    }

    public static void syncToClient(ServerPlayer player, PlayerAbilityData data) {
        SafePayloadSender.send(player, new SyncAbilityDataPacket(data.toSyncTag()));
    }
}
