package com.mohistmc.academy.regression;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.meltdowner.JetEngineRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoThrowingEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoNeedlingEffect;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.skill.ability.teleporter.ThreateningTeleportEffect;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real release entrypoints plus synchronous NeoForge cancellation/lifecycle callbacks. */
@GameTestHolder("academy_followup")
@PrefixGameTestTemplate(false)
public final class FollowupGameTests {
    static ServerPlayer player(GameTestHelper h, AbilityCategory category) {
        // The deprecated vanilla helper hardcodes isCreative() to true. Use its embedded
        // connection setup with an ordinary ServerPlayer so survival checks stay real.
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "followup-probe"), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        check(h, !p.isCreative() && !p.hasInfiniteMaterials(), "Fixture must use real survival semantics");
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 24, 3))));
        p.setYRot(0); p.setXRot(0);
        var d = data(p);
        d.setCurrentAbility(category); d.setPlayerLevel(5); d.setAbilityActive(true);
        if (category == AbilityCategory.TELEPORTER) d.learnSkill("threatening_teleport");
        d.setCurrentCp(d.getMaxCp()); d.setCurrentOverload(0);
        return p;
    }

    static PlayerAbilityData data(ServerPlayer p) { return p.getData(AcademyAttachments.PLAYER_ABILITY); }
    private static void check(GameTestHelper h, boolean condition, String message) { h.assertTrue(condition, message); }
    private static void close(GameTestHelper h, double a, double b, String message) { check(h, Math.abs(a - b) < .001, message + ": " + a + " / " + b); }
    private static Object invoke(Class<?> owner, String name, Class<?>[] types, Object... args) {
        try { Method m = owner.getDeclaredMethod(name, types); m.setAccessible(true); return m.invoke(null, args); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void jetStart(ServerPlayer p, Vec3 target) {
        invoke(JetEngineRuntime.class, "start", new Class<?>[]{ServerPlayer.class, Vec3.class, float.class}, p, target, 1F);
    }
    private static void jetTick(ServerPlayer p) { JetEngineRuntime.tick(new PlayerTickEvent.Post(p)); }

    private static void wallCrossing(GameTestHelper h, boolean diagonal) {
        var p = player(h, AbilityCategory.MELTDOWNER);
        if (diagonal) p.setPos(p.position().add(.4, 0, -.4));
        Vec3 start = p.position();
        Vec3 delta = diagonal ? new Vec3(Math.sqrt(72), 0, Math.sqrt(72)) : new Vec3(0, 0, 12);
        BlockPos wall = diagonal ? p.blockPosition().east() : p.blockPosition().south();
        h.getLevel().setBlock(wall, Blocks.GLASS_PANE.defaultBlockState(), 3);
        h.getLevel().setBlock(wall.above(), Blocks.GLASS_PANE.defaultBlockState(), 3);
        check(h, h.getLevel().noCollision(p, p.getBoundingBox().move(delta.scale(1D / 8))), "Fixture endpoint must be empty");
        jetStart(p, start.add(delta));
        try { jetTick(p); check(h, p.position().equals(start), "Jet Engine crossed a thin wall with a clear endpoint"); }
        finally { JetEngineRuntime.onConfirmedDeath(p); }
        h.succeed();
    }
    @GameTest(template = "empty") public static void jetThinWall(GameTestHelper h) { wallCrossing(h, false); }
    @GameTest(template = "empty") public static void jetDiagonalThinWall(GameTestHelper h) { wallCrossing(h, true); }

    @GameTest(template = "empty") public static void jetBuildHeight(GameTestHelper h) {
        var p = player(h, AbilityCategory.MELTDOWNER);
        p.setPos(p.getX(), h.getLevel().getMaxBuildHeight() - 2, p.getZ());
        Vec3 start = p.position(); jetStart(p, start.add(0, 12, 0));
        try { jetTick(p); check(h, p.position().equals(start), "Jet Engine moved the body outside build height"); }
        finally { JetEngineRuntime.onConfirmedDeath(p); }
        h.succeed();
    }

    @GameTest(template = "empty") public static void jetRepeatedStartRestoresOriginalSpeed(GameTestHelper h) {
        var p = player(h, AbilityCategory.MELTDOWNER); p.getAbilities().setWalkingSpeed(.13F);
        try { jetStart(p, p.position().add(0, 0, 12)); jetStart(p, p.position().add(12, 0, 0)); }
        finally { JetEngineRuntime.onConfirmedDeath(p); }
        close(h, p.getAbilities().getWalkingSpeed(), .13, "Repeated activation lost pre-skill walking speed"); h.succeed();
    }

    @GameTest(template = "empty") public static void jetLifecycleCallbackCannotResurrect(GameTestHelper h) {
        var p = player(h, AbilityCategory.MELTDOWNER);
        var victim = EntityType.CHICKEN.create(h.getLevel());
        check(h, victim != null, "Missing chicken"); victim.setPos(p.position().add(0, 0, .7)); victim.setNoAi(true);
        check(h, h.getLevel().addFreshEntity(victim), "Missing damage target"); int[] callbacks = {0};
        Consumer<LivingIncomingDamageEvent> stop = e -> {
            if (e.getEntity() == victim) { callbacks[0]++; JetEngineRuntime.onConfirmedDeath(p); }
        };
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, stop);
        try {
            jetStart(p, p.position().add(0, 0, 12)); jetTick(p);
            check(h, callbacks[0] == 1, "Fixture must invoke a real incoming damage callback");
            Vec3 stopped = p.position(); jetTick(p);
            check(h, p.position().equals(stopped), "Canceled Jet Engine context was reinserted after damage callback");
        } finally { NeoForge.EVENT_BUS.unregister(stop); JetEngineRuntime.onConfirmedDeath(p); victim.discard(); }
        h.succeed();
    }

    @GameTest(template = "empty") public static void jetOpenFlightPreservesLegacyExtrapolation(GameTestHelper h) {
        var p = player(h, AbilityCategory.MELTDOWNER); var start = p.position(); p.getAbilities().setWalkingSpeed(.12F);
        jetStart(p, start.add(0, 0, 12));
        try {
            for (int tick = 0; tick < 16; tick++) jetTick(p);
            close(h, p.position().distanceTo(start.add(0, 0, 24)), 0, "Legacy 16/8 trajectory changed");
            close(h, p.getAbilities().getWalkingSpeed(), .12, "Normal completion did not restore walking speed");
            Vec3 end = p.position(); jetTick(p); check(h, end.equals(p.position()), "Context survived its final tick");
        } finally { JetEngineRuntime.onConfirmedDeath(p); }
        h.succeed();
    }

    @GameTest(template = "empty") public static void jetFloorContactStillMoves(GameTestHelper h) {
        var p = player(h, AbilityCategory.MELTDOWNER); var start = p.position();
        for (int z = 0; z <= 3; z++) h.getLevel().setBlock(p.blockPosition().below().south(z), Blocks.STONE.defaultBlockState(), 3);
        jetStart(p, start.add(0, 0, 12));
        try { jetTick(p); close(h, p.getZ() - start.z, 1.5, "Floor contact blocked legal movement"); }
        finally { JetEngineRuntime.onConfirmedDeath(p); }
        h.succeed();
    }

    private static void rejectedDrop(GameTestHelper h, boolean creative, boolean fallbackOnly) {
        var p = player(h, AbilityCategory.TELEPORTER); if (creative) p.setGameMode(GameType.CREATIVE);
        var held = new ItemStack(Items.DIAMOND, 1); held.set(DataComponents.CUSTOM_NAME, Component.literal("audit-25"));
        p.setItemInHand(InteractionHand.MAIN_HAND, held); var d = data(p); var before = d.captureDynamicResources();
        int[] attempts = {0};
        Consumer<EntityJoinLevelEvent> deny = e -> {
            if (e.getEntity() instanceof ItemEntity item && item.getItem().is(Items.DIAMOND)
                    && item.position().distanceToSqr(p.position()) < 100) {
                attempts[0]++; if (!fallbackOnly || attempts[0] == 1) e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, deny);
        boolean released;
        try { released = new ThreateningTeleportEffect().tryRelease(p, d, 1); }
        finally { NeoForge.EVENT_BUS.unregister(deny); }
        check(h, attempts[0] == 2, "Drop rejection was not followed by one checked fallback: " + attempts[0]);
        check(h, released == fallbackOnly, "Wrong release outcome for rejected drop");
        if (!fallbackOnly) {
            check(h, before.equals(d.captureDynamicResources()), "Failed cast changed payment/growth/recovery delays");
            check(h, p.getMainHandItem().getCount() == 1 && Component.literal("audit-25").equals(p.getMainHandItem().get(DataComponents.CUSTOM_NAME)), "Failed cast lost the last item or its component");
            close(h, d.getProficiency("threatening_teleport"), 0, "Failed cast gained proficiency");
        } else {
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(10), i -> i.getItem().is(Items.DIAMOND));
            check(h, drops.size() == 1 && drops.getFirst().getItem().getCount() == 1 && p.getMainHandItem().isEmpty(), "Fallback violated item conservation");
            close(h, before.cp() - d.getCurrentCp(), DynamicSkillRules.cp("threatening_teleport", 35), "Fallback charged wrong CP");
            check(h, d.getProficiency("threatening_teleport") > 0, "Successful fallback did not gain proficiency");
        }
        h.succeed();
    }
    @GameTest(template = "empty") public static void threateningRejectedSurvival(GameTestHelper h) { rejectedDrop(h, false, false); }
    @GameTest(template = "empty") public static void threateningRejectedCreative(GameTestHelper h) { rejectedDrop(h, true, false); }
    @GameTest(template = "empty") public static void threateningFallback(GameTestHelper h) { rejectedDrop(h, false, true); }

    @GameTest(template = "empty") public static void threateningThroughWallRemainsIntentional(GameTestHelper h) {
        var p = player(h, AbilityCategory.TELEPORTER); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
        var victim = EntityType.COW.create(h.getLevel()); check(h, victim != null, "Missing cow");
        victim.setNoAi(true); victim.setPos(p.position().add(0, 0, 5)); h.getLevel().addFreshEntity(victim);
        h.getLevel().setBlock(p.blockPosition().south(2).above(), Blocks.STONE.defaultBlockState(), 3);
        float health = victim.getHealth();
        try { check(h, new ThreateningTeleportEffect().tryRelease(p, data(p), 1), "Release failed");
            check(h, victim.getHealth() < health && p.getMainHandItem().getCount() == 1, "Legacy through-wall attack changed");
        } finally { victim.discard(); }
        h.succeed();
    }

    private static ItemEntity startTransmission(GameTestHelper h, ServerPlayer p) {
        data(p).learnSkill("psycho_transmission");
        var at = p.getEyePosition().add(0, 0, 5);
        var item = new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND, 3));
        check(h, h.getLevel().addFreshEntity(item), "Missing transmission target");
        check(h, new PsychoTransmissionEffect().executeAndReport(p, data(p)), "Transmission did not start");
        return item;
    }
    private static void sameResources(GameTestHelper h, PlayerAbilityData actual, PlayerAbilityData expected) {
        var a = actual.captureDynamicResources(); var e = expected.captureDynamicResources();
        close(h, a.cp(), e.cp(), "CP rollback"); close(h, a.overload(), e.overload(), "Overload rollback");
        close(h, a.growthCp(), e.growthCp(), "CP growth rollback"); close(h, a.growthOverload(), e.growthOverload(), "Overload growth rollback");
        check(h, a.cpDelay() == e.cpDelay() && a.overloadDelay() == e.overloadDelay(), "Recovery delay rollback");
    }
    private static void pickupRejected(GameTestHelper h, boolean callbackPayment) {
        var p = player(h, AbilityCategory.TELEKINESIS); var item = startTransmission(h, p); var d = data(p);
        var expected = new PlayerAbilityData(); expected.setCurrentAbility(AbilityCategory.TELEKINESIS); expected.setPlayerLevel(5);
        expected.setCurrentCp(expected.getMaxCp()); expected.setCurrentOverload(0);
        DynamicSkillRules.tryPay(expected, "psycho_transmission", 0, 5);
        DynamicSkillRules.tryPay(expected, "psycho_transmission", .5F, 0);
        if (callbackPayment) DynamicSkillRules.tryPay(expected, "psycho_transmission", 3, 1);
        int[] callbacks = {0};
        Consumer<ItemEntityPickupEvent.Pre> deny = e -> {
            if (e.getPlayer() == p && e.getItemEntity() == item) {
                callbacks[0]++; if (callbackPayment) DynamicSkillRules.tryPay(d, "psycho_transmission", 3, 1);
                e.setCanPickup(TriState.FALSE);
            }
        };
        NeoForge.EVENT_BUS.addListener(ItemEntityPickupEvent.Pre.class, deny);
        try { PsychoTransmissionEffect.tick(new PlayerTickEvent.Post(p)); }
        finally { NeoForge.EVENT_BUS.unregister(deny); PsychoTransmissionEffect.onConfirmedDeath(p); }
        check(h, callbacks[0] == 1 && item.isAlive() && item.getItem().getCount() == 3 && p.getInventory().countItem(Items.DIAMOND) == 0, "Canceled pickup changed items or did not exercise Pre");
        sameResources(h, d, expected); close(h, d.getProficiency("psycho_transmission"), 0, "Rejected pickup gained proficiency");
        h.succeed();
    }
    @GameTest(template = "empty") public static void transmissionCancelRollsBackGrowth(GameTestHelper h) { pickupRejected(h, false); }
    @GameTest(template = "empty") public static void transmissionCancelPreservesCallbackPayment(GameTestHelper h) { pickupRejected(h, true); }

    private static void fullInventory(GameTestHelper h, boolean mergeOffhand) {
        var p = player(h, AbilityCategory.TELEKINESIS);
        for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
        if (mergeOffhand) p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 63));
        var item = startTransmission(h, p); int[] callbacks = {0};
        Consumer<ItemEntityPickupEvent.Pre> seen = e -> { if (e.getPlayer() == p && e.getItemEntity() == item) callbacks[0]++; };
        NeoForge.EVENT_BUS.addListener(ItemEntityPickupEvent.Pre.class, seen);
        try { PsychoTransmissionEffect.tick(new PlayerTickEvent.Post(p)); }
        finally { NeoForge.EVENT_BUS.unregister(seen); PsychoTransmissionEffect.onConfirmedDeath(p); }
        if (mergeOffhand) {
            check(h, callbacks[0] == 1 && p.getOffhandItem().getCount() == 64 && item.getItem().getCount() == 2, "Existing offhand stack must accept a partial pickup");
            check(h, data(p).getProficiency("psycho_transmission") > 0, "Partial successful pickup lost proficiency");
        } else {
            check(h, callbacks[0] == 0 && item.getItem().getCount() == 3, "Empty armor/offhand falsely passed inventory-space preflight");
        }
        h.succeed();
    }
    @GameTest(template = "empty") public static void transmissionFullMainIgnoresEmptyEquipment(GameTestHelper h) { fullInventory(h, false); }
    @GameTest(template = "empty") public static void transmissionPartialOffhandMerge(GameTestHelper h) { fullInventory(h, true); }

    private static void projectileReturn(GameTestHelper h, boolean needle, boolean denyDrop, boolean attack) {
        var p = player(h, AbilityCategory.TELEKINESIS);
        var itemType = needle ? AcademyItems.NEEDLE.get() : AcademyItems.ETCHED_COBBLESTONE.get();
        var ammo = new ItemStack(itemType, 1); ammo.set(DataComponents.CUSTOM_NAME, Component.literal("projectile-25"));
        p.setItemInHand(InteractionHand.MAIN_HAND, ammo); var d = data(p); d.learnSkill(needle ? "psycho_needling" : "psycho_throwing"); var before = d.captureDynamicResources();
        for (int y = -3; y <= 3; y++) h.getLevel().setBlock(p.blockPosition().south(6).above(y), Blocks.STONE.defaultBlockState(), 3);
        var victim = attack ? EntityType.COW.create(h.getLevel()) : null;
        if (victim != null) { victim.setNoAi(true); victim.setPos(p.position().add(0, .7, 3)); h.getLevel().addFreshEntity(victim); }
        float health = victim == null ? 0 : victim.getHealth(); int[] rejects = {0};
        Consumer<EntityJoinLevelEvent> deny = e -> {
            if (denyDrop && e.getEntity() instanceof ItemEntity item && item.getItem().is(itemType)
                    && item.position().distanceToSqr(p.position()) < 100) { rejects[0]++; e.setCanceled(true); }
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, deny);
        SkillEffect effect = needle ? new PsychoNeedlingEffect() : new PsychoThrowingEffect();
        try {
            boolean result = effect.executeAndReport(p, d);
            check(h, result, "Projectile launch rejected");
            var shots = h.getLevel().getEntitiesOfClass(com.mohistmc.academy.world.entity.PsychoProjectileEntity.class,
                    p.getBoundingBox().inflate(3), e -> e.getOwner() == p);
            check(h, shots.size() == 1, "Launch did not create exactly one projectile");
            var shot = shots.getFirst();
            for (int tick = 0; tick < 25 && !shot.isRemoved(); tick++) shot.tick();
            if (denyDrop) {
                check(h, rejects[0] == 1 && p.getMainHandItem().getCount() == 1
                        && Component.literal("projectile-25").equals(p.getMainHandItem().get(DataComponents.CUSTOM_NAME)), "Rejected projectile return lost ammo or components");
                if (attack) check(h, victim != null && victim.getHealth() < health && d.getCurrentCp() < before.cp(), "Completed projectile damage must remain paid");
                else check(h, d.getCurrentCp() < before.cp() && d.getProficiency(effect.getId()) > 0, "Accepted flight must remain paid despite a canceled item drop");
            } else {
                var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(8), i -> i.getItem().is(itemType));
                check(h, drops.size() == 1 && drops.getFirst().getItem().getCount() == 1 && p.getMainHandItem().isEmpty(), "Successful return violated ammo conservation");
                check(h, Component.literal("projectile-25").equals(drops.getFirst().getItem().get(DataComponents.CUSTOM_NAME)), "Projectile return erased stack components");
            }
        } finally { NeoForge.EVENT_BUS.unregister(deny); if (victim != null) victim.discard(); }
        h.succeed();
    }
    @GameTest(template = "empty") public static void throwingRejectedReturn(GameTestHelper h) { projectileReturn(h, false, true, false); }
    @GameTest(template = "empty") public static void needlingRejectedReturn(GameTestHelper h) { projectileReturn(h, true, true, false); }
    @GameTest(template = "empty") public static void throwingPreservesComponents(GameTestHelper h) { projectileReturn(h, false, false, false); }
    @GameTest(template = "empty") public static void needlingPreservesComponents(GameTestHelper h) { projectileReturn(h, true, false, false); }
    @GameTest(template = "empty") public static void needlingRejectedReturnDoesNotRefundDamage(GameTestHelper h) { projectileReturn(h, true, true, true); }
}
