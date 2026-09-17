package com.mohistmc.academy.regression;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoNeedlingEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoThrowingEffect;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.entity.PsychoProjectileEntity;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("academy_projectiles")
@PrefixGameTestTemplate(false)
public final class ProjectileGameTests {
    private static ServerPlayer player(GameTestHelper h, boolean needle) {
        var p = FollowupGameTests.player(h, AbilityCategory.TELEKINESIS);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(needle ? AcademyItems.NEEDLE.get() : Items.COBBLESTONE, 3));
        FollowupGameTests.data(p).learnSkill(needle ? "psycho_needling" : "psycho_throwing");
        return p;
    }
    private static SkillEffect effect(boolean needle) { return needle ? new PsychoNeedlingEffect() : new PsychoThrowingEffect(); }
    private static void near(GameTestHelper h, double actual, double expected) {
        h.assertTrue(Math.abs(actual - expected) < .0001, "Expected " + expected + ", got " + actual);
    }
    private static PsychoProjectileEntity launch(GameTestHelper h, ServerPlayer p, boolean needle) {
        h.assertTrue(effect(needle).executeAndReport(p, FollowupGameTests.data(p)), "Launch failed");
        var shots = h.getLevel().getEntitiesOfClass(PsychoProjectileEntity.class, p.getBoundingBox().inflate(3), e -> e.getOwner() == p);
        h.assertTrue(shots.size() == 1, "Expected exactly one live projectile");
        return shots.getFirst();
    }
    private static void priority(GameTestHelper h, boolean needle, boolean offhand) {
        var p = player(h, needle);
        var type = needle ? AcademyItems.NEEDLE.get() : AcademyItems.ETCHED_COBBLESTONE.get();
        p.getInventory().selected = 5;
        var preferred = new ItemStack(type, 3);
        preferred.set(DataComponents.CUSTOM_NAME, Component.literal("preferred"));
        p.setItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, preferred);
        var shot = launch(h, p, needle);
        h.assertTrue(preferred.getCount() == 2 && p.getInventory().getItem(0).getCount() == 3,
                "Earlier inventory slot won over hand priority");
        h.assertTrue(Component.literal("preferred").equals(shot.getItem().get(DataComponents.CUSTOM_NAME)), "Wrong projectile stack");
        if (!needle) near(h, shot.getDeltaMovement().length(), .15);
        shot.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void stoneOffhandFirst(GameTestHelper h) { priority(h, false, true); }
    @GameTest(template="empty") public static void stoneMainhandBeforeInventory(GameTestHelper h) { priority(h, false, false); }
    @GameTest(template="empty") public static void needleOffhandFirst(GameTestHelper h) { priority(h, true, true); }
    @GameTest(template="empty") public static void needleMainhandBeforeInventory(GameTestHelper h) { priority(h, true, false); }

    private static void rejected(GameTestHelper h, boolean needle, boolean replaceAmmo) {
        var p = player(h, needle); var d = FollowupGameTests.data(p); var before = d.captureDynamicResources();
        Consumer<EntityJoinLevelEvent> veto = e -> {
            if (e.getEntity() instanceof PsychoProjectileEntity shot && shot.getOwner() == p) {
                if (replaceAmmo) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
                else e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, veto);
        try {
            h.assertTrue(!effect(needle).executeAndReport(p, d), "Rejected/replaced launch succeeded");
            h.assertTrue(before.equals(d.captureDynamicResources()) && d.getProficiency(effect(needle).getId()) == 0, "Rejected launch charged resources/growth");
            h.assertTrue(p.getMainHandItem().getCount() == (replaceAmmo ? 1 : 3), "Failed launch consumed ammo");
        } finally { NeoForge.EVENT_BUS.unregister(veto); }
        h.succeed();
    }
    @GameTest(template="empty") public static void stoneAdmissionRefund(GameTestHelper h) { rejected(h, false, false); }
    @GameTest(template="empty") public static void needleAdmissionRefund(GameTestHelper h) { rejected(h, true, false); }
    @GameTest(template="empty") public static void callbackReplacedAmmoCannotFire(GameTestHelper h) { rejected(h, true, true); }

    private static void motion(GameTestHelper h, boolean needle, boolean etched) {
        var p = player(h, needle);
        if (etched) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AcademyItems.ETCHED_COBBLESTONE.get(), 3));
        var shot = launch(h, p, needle); Vec3 origin = shot.position();
        near(h, origin.distanceTo(p.getEyePosition()), 0);
        shot.tick();
        near(h, shot.getZ() - origin.z, etched ? .3 : .2);
        near(h, shot.getY() - origin.y, 0);
        near(h, shot.getDeltaMovement().y, needle ? 0 : -.03);
        if (needle) {
            for (int i = 1; i < 10; i++) shot.tick();
            near(h, shot.getZ() - origin.z, 6.5);
            near(h, shot.getDeltaMovement().z, 1.1);
            shot.tick();
            near(h, shot.getZ() - origin.z, 7.6);
            near(h, shot.getDeltaMovement().z, 1.1);
        } else {
            shot.tick();
            near(h, shot.getY() - origin.y, -.03);
            near(h, shot.getZ() - origin.z, etched ? .747 : .498);
        }
        shot.discard(); h.succeed();
    }
    @GameTest(template="empty") public static void needleTenTickAcceleration(GameTestHelper h) { motion(h, true, false); }
    @GameTest(template="empty") public static void stoneDragAndGravity(GameTestHelper h) { motion(h, false, false); }
    @GameTest(template="empty") public static void etchedStoneAcceleration(GameTestHelper h) { motion(h, false, true); }

    private static void delayed(GameTestHelper h, boolean moveTarget) {
        var p = player(h, true); var victim = EntityType.COW.create(h.getLevel());
        h.assertTrue(victim != null, "No victim");
        victim.setNoAi(true); victim.setNoGravity(true); victim.setPos(p.position().add(0, .7, 3));
        h.getLevel().addFreshEntity(victim); float health = victim.getHealth();
        var shot = launch(h, p, true);
        near(h, victim.getHealth(), health);
        if (moveTarget) victim.setPos(victim.position().add(4, 0, 0));
        h.runAfterDelay(12, () -> {
            try {
                h.assertTrue(moveTarget ? victim.getHealth() == health : victim.getHealth() < health,
                        "Real world flight failed delayed/moving-target assertion");
                h.assertTrue(moveTarget ? shot.isAlive() : shot.isRemoved(), "Wrong flight lifecycle");
                h.succeed();
            } finally { victim.discard(); shot.discard(); }
        });
    }
    @GameTest(template="empty",timeoutTicks=40) public static void delayedWorldTickHit(GameTestHelper h) { delayed(h, false); }
    @GameTest(template="empty",timeoutTicks=40) public static void movingTargetCanEvade(GameTestHelper h) { delayed(h, true); }

    private static void canceledImpact(GameTestHelper h, boolean needle) {
        var p = player(h, needle); var shot = launch(h, p, needle);
        var wall = p.blockPosition().south().above(); h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        int[] count = {0}; Consumer<ProjectileImpactEvent> veto = e -> { if (e.getProjectile() == shot) { count[0]++; e.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(ProjectileImpactEvent.class, veto);
        try {
            for (int i = 0; i < 5; i++) shot.tick();
            h.assertTrue(count[0] > 0 && shot.isAlive() && !shot.isReturning(), "Canceled impact stopped/returned the projectile");
            h.assertTrue(shot.getZ() > wall.getZ() + 1, "Canceled impact did not continue flight");
        } finally { NeoForge.EVENT_BUS.unregister(veto); shot.discard(); }
        h.succeed();
    }
    @GameTest(template="empty") public static void needleImpactCancellation(GameTestHelper h) { canceledImpact(h, true); }
    @GameTest(template="empty") public static void stoneImpactCancellation(GameTestHelper h) { canceledImpact(h, false); }

    @GameTest(template="empty") public static void wallShieldsTarget(GameTestHelper h) {
        var p = player(h, true); var wall = p.blockPosition().south(2).above();
        h.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        var victim = EntityType.COW.create(h.getLevel()); victim.setNoAi(true); victim.setPos(p.position().add(0,.7,3)); h.getLevel().addFreshEntity(victim);
        float health = victim.getHealth(); var shot = launch(h,p,true);
        shot.setDeltaMovement(0,0,6); // Sweeps over the wall and the target in one tick.
        shot.tick(); near(h,victim.getHealth(),health); h.assertTrue(shot.isRemoved(),"Wall did not settle projectile");
        victim.discard(); h.succeed();
    }

    private static void savedFlight(GameTestHelper h, boolean needle) {
        var p = player(h, needle); p.getMainHandItem().set(DataComponents.CUSTOM_NAME, Component.literal("saved"));
        var shot = launch(h,p,needle); for(int i=0;i<3;i++) shot.tick();
        var tag = new CompoundTag(); shot.saveWithoutId(tag);
        var loaded = (needle ? AcademyEntities.PSYCHO_NEEDLE : AcademyEntities.PSYCHO_STONE).get().create(h.getLevel());
        loaded.load(tag); near(h,loaded.position().distanceTo(shot.position()),0);
        h.assertTrue(ItemStack.matches(loaded.getItem(),shot.getItem()) && loaded.getOwner()==p,"Save lost owner/ammo/components");
        loaded.tick(); shot.tick(); near(h,loaded.position().distanceTo(shot.position()),0);
        near(h,loaded.getDeltaMovement().distanceTo(shot.getDeltaMovement()),0);
        loaded.discard();shot.discard();h.succeed();
    }
    @GameTest(template="empty") public static void savedNeedleKeepsFlight(GameTestHelper h) { savedFlight(h,true); }
    @GameTest(template="empty") public static void savedStoneKeepsFlight(GameTestHelper h) { savedFlight(h,false); }

    @GameTest(template="empty") public static void canceledDropFullInventorySurvivesSave(GameTestHelper h) {
        var p = player(h,true);p.getMainHandItem().set(DataComponents.CUSTOM_NAME,Component.literal("pending"));
        var shot=launch(h,p,true);
        for(int i=0;i<p.getInventory().items.size();i++) p.getInventory().items.set(i,new ItemStack(Items.STONE,64));
        h.getLevel().setBlock(p.blockPosition().south().above(),Blocks.STONE.defaultBlockState(),3);
        int[] attempts={0};Consumer<EntityJoinLevelEvent> veto=e->{if(e.getEntity() instanceof ItemEntity item && item.getItem().is(AcademyItems.NEEDLE.get()) && item.distanceToSqr(p)<100){attempts[0]++;e.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class,veto);
        PsychoProjectileEntity loaded=null;
        try {
            for(int i=0;i<7;i++) shot.tick();
            h.assertTrue(shot.isAlive()&&shot.isReturning()&&shot.getItem().getCount()==1&&attempts[0]==1,"Canceled return lost/duplicated item or retried each tick");
            var tag=new CompoundTag();shot.saveWithoutId(tag);shot.discard();
            loaded=AcademyEntities.PSYCHO_NEEDLE.get().create(h.getLevel());loaded.load(tag);
            h.assertTrue(loaded.isReturning()&&Component.literal("pending").equals(loaded.getItem().get(DataComponents.CUSTOM_NAME)),"Pending save lost components/state");
        } finally {NeoForge.EVENT_BUS.unregister(veto);}
        h.assertTrue(loaded!=null,"No loaded return");loaded.tick();h.assertTrue(loaded.isRemoved(),"Saved return did not retry successfully");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(4),e->e.getItem().is(AcademyItems.NEEDLE.get()));
        h.assertTrue(drops.size()==1&&drops.getFirst().getItem().getCount()==1,"Retried return violated conservation");h.succeed();
    }

    @GameTest(template="empty") public static void missingOwnerReturnsAmmo(GameTestHelper h) {
        var p=player(h,true);var shot=launch(h,p,true);var tag=new CompoundTag();shot.saveWithoutId(tag);shot.discard();
        tag.putUUID("Owner",java.util.UUID.randomUUID());var loaded=AcademyEntities.PSYCHO_NEEDLE.get().create(h.getLevel());loaded.load(tag);loaded.tick();
        h.assertTrue(loaded.isRemoved(),"Ownerless projectile kept flying");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(3),e->e.getItem().is(AcademyItems.NEEDLE.get())).size()==1,"Missing owner erased ammo");h.succeed();
    }

    @GameTest(template="empty") public static void needleDamageAttributionAndArmorTags(GameTestHelper h) {
        var p=player(h,true);var shot=launch(h,p,true);var victim=EntityType.COW.create(h.getLevel());victim.setNoAi(true);victim.setPos(p.position().add(0,.7,2));h.getLevel().addFreshEntity(victim);
        boolean[] checked={false};Consumer<LivingIncomingDamageEvent> seen=e->{if(e.getEntity()==victim){checked[0]=e.getSource().getDirectEntity()==shot&&e.getSource().getEntity()==p&&e.getSource().is(DamageTypeTags.IS_PROJECTILE)&&e.getSource().is(DamageTypeTags.BYPASSES_ARMOR);}};
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class,seen);
        try {for(int i=0;i<12&&!shot.isRemoved();i++)shot.tick();h.assertTrue(checked[0],"Needle lost caster/projectile/bypass armor source");}
        finally{NeoForge.EVENT_BUS.unregister(seen);victim.discard();shot.discard();}h.succeed();
    }

    private static void fragile(GameTestHelper h, int action) {
        var p = player(h, true); var shot = launch(h, p, true);
        var front = p.blockPosition().south().above();
        h.getLevel().setBlock(front, Blocks.SLIME_BLOCK.defaultBlockState(), 3);
        if (action == 3) h.getLevel().setBlock(front.south(), Blocks.STONE.defaultBlockState(), 3);
        int[] calls = {0};
        Consumer<net.neoforged.neoforge.event.level.BlockEvent.BreakEvent> event = e -> {
            if (e.getPlayer() == p && e.getPos().equals(front)) {
                calls[0]++;
                if (action == 1) e.setCanceled(true);
                if (action == 2) h.getLevel().setBlock(front, Blocks.OBSIDIAN.defaultBlockState(), 3);
            }
        };
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent.class, event);
        try {
            shot.setDeltaMovement(0, 0, 4); shot.tick();
            h.assertTrue(calls[0] == 1, "Did not exercise fragile-block permission callback");
            if (action == 0) h.assertTrue(h.getLevel().getBlockState(front).isAir() && shot.isAlive(), "Unprotected fragile block stopped flight");
            if (action == 1) h.assertTrue(h.getLevel().getBlockState(front).is(Blocks.SLIME_BLOCK) && shot.isRemoved(), "Canceled break modified block or continued flight");
            if (action == 2) h.assertTrue(h.getLevel().getBlockState(front).is(Blocks.OBSIDIAN) && shot.isRemoved(), "Break callback replacement was destroyed");
            if (action == 3) h.assertTrue(h.getLevel().getBlockState(front).isAir()
                    && h.getLevel().getBlockState(front.south()).is(Blocks.STONE) && shot.isRemoved()
                    && shot.getZ() <= front.getZ() + 1, "Fragile-block penetration skipped a second wall");
        } finally { NeoForge.EVENT_BUS.unregister(event); shot.discard(); }
        h.succeed();
    }
    @GameTest(template="empty") public static void fragileBlockCanBreak(GameTestHelper h) { fragile(h, 0); }
    @GameTest(template="empty") public static void canceledBreakStopsFlight(GameTestHelper h) { fragile(h, 1); }
    @GameTest(template="empty") public static void replacementBlockSurvives(GameTestHelper h) { fragile(h, 2); }
    @GameTest(template="empty") public static void fragileThenSolidWall(GameTestHelper h) { fragile(h, 3); }

    @GameTest(template="empty") public static void nonSolidTorchIsNotSkipped(GameTestHelper h) {
        var p = player(h, true); var shot = launch(h, p, true);
        var torch = p.blockPosition().south().above();
        h.getLevel().setBlock(torch.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(torch, Blocks.TORCH.defaultBlockState(), 3);
        shot.setPos(p.getX(), torch.getY() + .3, p.getZ()); shot.setDeltaMovement(0, 0, 2);
        shot.tick();
        h.assertTrue(h.getLevel().getBlockState(torch).isAir() && shot.isAlive(), "Legacy non-solid outline was skipped or stopped the shot");
        shot.discard(); h.succeed();
    }

    @GameTest(template="empty") public static void portalOutlineDoesNotConsumeAmmo(GameTestHelper h) {
        var p = player(h, true); var shot = launch(h, p, true);
        var portal = p.blockPosition().south().above();
        h.getLevel().setBlock(portal, Blocks.NETHER_PORTAL.defaultBlockState(), 2);
        shot.setDeltaMovement(0, 0, 2); shot.tick();
        h.assertTrue(shot.isAlive() && !shot.isReturning() && shot.getItem().getCount() == 1,
                "Nether portal outline incorrectly consumed the projectile");
        shot.discard(); h.succeed();
    }
}
