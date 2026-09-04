package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.telekinesis.PaperDrillEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mohistmc.academy.config.ACConfig;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TelekinesisGameTests {
    private static final String EMPTY = "empty";
    private TelekinesisGameTests() {}

    private static PlayerAbilityData telekinesis(ServerPlayer player, String... learned) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.TELEKINESIS);
        data.setAbilityActive(true);
        data.setDevMode(true);
        for (String id : learned) data.learnSkill(id);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return data;
    }

    @GameTest(template = EMPTY)
    public static void psychoTransmissionPicksTheVisibleDroppedItem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = telekinesis(player, "psycho_transmission");
        Vec3 at = player.getEyePosition().add(0, 0, 5);
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z,
                new ItemStack(Items.DIAMOND));
        helper.getLevel().addFreshEntity(item);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, item.position());
        PsychoTransmissionEffect effect = new PsychoTransmissionEffect();
        if (!effect.canActivate(player, data)) {
            helper.fail("visible collectible item was not a valid transmission target"); return;
        }
        effect.execute(player, data);
        PsychoTransmissionEffect.tick(new PlayerTickEvent.Post(player));
        if (player.getInventory().countItem(Items.DIAMOND) != 1 || item.isAlive()) {
            helper.fail("transmission did not move the targeted item into inventory"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void paperDrillConsumesOnlyAfterAcknowledgedChargingTick(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = telekinesis(player, "perfect_paper", "paper_drill");
        player.getInventory().add(new ItemStack(Items.PAPER, 64));
        PaperDrillEffect effect = new PaperDrillEffect();
        if (!effect.canStartCharging(player, data)) {
            helper.fail("paper drill rejected its exact 64-paper requirement"); return;
        }
        effect.onChargingStart(player, data);
        if (player.getInventory().countItem(Items.PAPER) != 64) {
            helper.fail("paper drill consumed material before charging acknowledgement"); return;
        }
        if (!effect.onChargingTick(player, data, 1)) {
            helper.fail("paper drill rejected its first acknowledged tick"); return;
        }
        if (player.getInventory().countItem(Items.PAPER) != 0) {
            helper.fail("paper drill did not consume exactly one full stack after acknowledgement"); return;
        }
        effect.onChargingAbort(player, data);
        if (player.getInventory().countItem(Items.PAPER) != 64) {
            helper.fail("paper drill did not return its exact paper stack on abort"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void insulationOnlyMitigatesAcademyAbilityDamage(GameTestHelper helper) {
        ServerPlayer attacker = helper.makeMockServerPlayerInLevel();
        ServerPlayer defender = helper.makeMockServerPlayerInLevel();
        attacker.setGameMode(GameType.SURVIVAL);
        defender.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData defense = telekinesis(defender, "insulation");
        defense.setProficiency("insulation", 1);
        PlayerAbilityData offense = attacker.getData(AcademyAttachments.PLAYER_ABILITY);
        offense.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        offense.setAbilityActive(true);
        attacker.setData(AcademyAttachments.PLAYER_ABILITY, offense);
        // Fresh ServerPlayers have 60 ticks of vanilla spawn invulnerability.
        // Waiting exercises the real playerAttack path instead of weakening the
        // assertion with a bypass-invulnerability test damage source.
        helper.runAfterDelay(61, () -> {
            boolean academyPvp = ACConfig.Server.PVP_ENABLED.get();
            boolean vanillaPvp = helper.getLevel().getServer().isPvpAllowed();
            try {
                ACConfig.Server.PVP_ENABLED.set(true);
                // Academy's server-authority switch is intentionally an
                // additional gate, not a bypass for the vanilla server's PvP
                // policy. GameTestServer defaults this policy to false, so
                // exercise the same fully-enabled path a live PvP server uses.
                helper.getLevel().getServer().setPvpAllowed(true);
                // Other GameTests run concurrently in the same level. A mob from
                // an adjacent fixture can hit this synthetic player during the
                // 61-tick spawn-protection wait and leave vanilla's lastHurt
                // differential-damage window active. Reset only the damage
                // baseline immediately before the synchronous assertion.
                defender.setHealth(defender.getMaxHealth());
                defender.invulnerableTime = 0;
                float before = defender.getHealth();
                if (!AcademyDamageHelper.hurt(attacker, defender,
                        attacker.damageSources().playerAttack(attacker), 10)) {
                    helper.fail("Academy ability damage unexpectedly failed after spawn protection"); return;
                }
                float taken = before - defender.getHealth();
                if (Math.abs(taken - 6.0f) > 0.05f) {
                    helper.fail("mastered Electromaster insulation expected 6.0 damage but took " + taken); return;
                }
            } finally {
                helper.getLevel().getServer().setPvpAllowed(vanillaPvp);
                ACConfig.Server.PVP_ENABLED.set(academyPvp);
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void liquidShadowConsumesWaterOnceAndTogglesWithoutDuplication(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        telekinesis(player, "liquid_shadow");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        if (!TelekinesisPassiveHandler.toggleLiquidShadow(player)
                || !player.getMainHandItem().is(Items.BUCKET)) {
            helper.fail("liquid shadow did not transactionally consume one water bucket"); return;
        }
        AABB nearby = player.getBoundingBox().inflate(8);
        var shadows = helper.getLevel().getEntitiesOfClass(Drowned.class, nearby,
                drowned -> drowned.getTags().contains("academy_liquid_shadow"));
        if (shadows.size() != 1) {
            helper.fail("liquid shadow expected one follower, found " + shadows.size()); return;
        }
        if (!TelekinesisPassiveHandler.toggleLiquidShadow(player)
                || shadows.getFirst().isAlive()) {
            helper.fail("second toggle did not terminate the existing shadow"); return;
        }
        helper.succeed();
    }
}
