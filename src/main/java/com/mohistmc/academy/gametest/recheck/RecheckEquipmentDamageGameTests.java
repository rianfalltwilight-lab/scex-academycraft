package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.item.ImagEnergyArmorItem;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real hurt/IF regressions using a negotiated EmbeddedChannel; not socket or full-pack acceptance. */
@EventBusSubscriber(modid = AcademyCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public final class RecheckEquipmentDamageGameTests {
    private static final String NAMESPACE = "academy_recheck_compat";
    private RecheckEquipmentDamageGameTests() {}

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.recheckCompatGate")) return;
        var missing = List.of("botania", "patchouli", "curios", "thaumcraft", "thaumicbases", "dummmmmmy", "modularrouters")
                .stream().filter(id -> !ModList.get().isLoaded(id)).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("IF compatibility regression dependencies missing: " + missing);
        event.register(RecheckEquipmentDamageGameTests.class);
        LogUtils.getLogger().info("RECHECK_EQUIPMENT_REGISTERED cases=4 namespace={} negotiatedMock=true", NAMESPACE);
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 180)
    public static void imaginaryIfDoesNotPayForLateIncomingCancel(GameTestHelper h) { rejected(h, "cancel"); }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 180)
    public static void imaginaryIfDoesNotPayForActualHurtCooldown(GameTestHelper h) { rejected(h, "iframe"); }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 180)
    public static void imaginaryIfDoesNotPayForActualShieldFullBlock(GameTestHelper h) { rejected(h, "shield"); }

    private static void rejected(GameTestHelper h, String mode) {
        var defender = RecheckCompatPlayers.connect(h);
        var attacker = RecheckCompatPlayers.connect(h);
        ServerPlayer player = defender.player();
        if (mode.equals("shield")) {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            player.startUsingItem(InteractionHand.OFF_HAND);
        }
        h.runAfterDelay(65, () -> {
            var observer = new ObserveIncoming(player, mode.equals("cancel"));
            boolean oldPvp = h.getLevel().getServer().isPvpAllowed();
            NeoForge.EVENT_BUS.register(observer);
            try {
                h.getLevel().getServer().setPvpAllowed(true);
                ItemStack chest = chargedChest(player);
                ImagEnergyArmorItem armor = (ImagEnergyArmorItem) chest.getItem();
                player.setHealth(20); player.invulnerableTime = 0;
                player.setYRot(0); player.setYHeadRot(0); player.setXRot(0);
                attacker.player().setPos(player.getX(), player.getY(), player.getZ() + 3);
                DamageSource source = player.damageSources().playerAttack(attacker.player());
                if (mode.equals("iframe")) {
                    check(player.hurt(source, 8) && player.getHealth() < 20 && player.invulnerableTime > 10
                                    && armor.getEnergyStored(chest) < ImagEnergyArmorItem.MAX_ENERGY,
                            "accepted ordinary control did not spend IF and establish real hurt cooldown");
                }
                if (mode.equals("shield")) check(player.isBlocking() && player.isDamageSourceBlocked(source),
                        "real shield was not naturally blocking/facing the source");
                int before = armor.getEnergyStored(chest);
                float health = player.getHealth();
                int shieldBefore = player.getOffhandItem().getDamageValue();
                int seenBefore = observer.seen;
                boolean accepted = player.hurt(source, mode.equals("iframe") ? 1 : 20);
                int after = armor.getEnergyStored(chest);
                int shieldAfter = player.getOffhandItem().getDamageValue();
                LogUtils.getLogger().info("RECHECK_IMAGINARY_REJECTION mode={} accepted={} events={}=>{} hp={}=>{} IF={}=>{} shield={}=>{}",
                        mode, accepted, seenBefore, observer.seen, health, player.getHealth(), before, after, shieldBefore, shieldAfter);
                check(!accepted && observer.seen == seenBefore + 1 && near(player.getHealth(), health),
                        "real rejected-hurt control failed: mode=" + mode + " accepted=" + accepted + " incoming=" + observer.seen);
                if (mode.equals("shield")) check(shieldAfter > shieldBefore, "shield control did not consume actual durability");
                check(before == after, "rejected " + mode + " hurt spent imaginary armour IF: " + before + "=>" + after);
                h.succeed();
            } finally {
                NeoForge.EVENT_BUS.unregister(observer); h.getLevel().getServer().setPvpAllowed(oldPvp);
                attacker.close(); defender.close();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = NAMESPACE, timeoutTicks = 180)
    public static void ordinaryNestedHurtKeepsOrdinaryImaginaryIfRate(GameTestHelper h) {
        var defender = RecheckCompatPlayers.connect(h);
        var attacker = RecheckCompatPlayers.connect(h);
        h.runAfterDelay(65, () -> {
            ServerPlayer player = defender.player();
            boolean oldPvp = h.getLevel().getServer().isPvpAllowed();
            boolean oldAcademyPvp = ACConfig.Server.PVP_ENABLED.get();
            var outer = EntityType.PIG.create(h.getLevel());
            check(outer != null, "outer ability target creation failed");
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            outer.setPos(origin.getX() + 4, origin.getY() + 2, origin.getZ() + 4);
            outer.setNoAi(true); outer.setNoGravity(true); h.getLevel().addFreshEntity(outer);
            NestedOrdinary listener = null;
            try {
                h.getLevel().getServer().setPvpAllowed(true); ACConfig.Server.PVP_ENABLED.set(true);
                ItemStack chest = chargedChest(player);
                ImagEnergyArmorItem armor = (ImagEnergyArmorItem) chest.getItem();
                DamageSource normalSource = player.damageSources().playerAttack(attacker.player());
                player.setHealth(20); player.invulnerableTime = 0;
                boolean controlAccepted = player.hurt(normalSource, 8);
                int normalCost = ImagEnergyArmorItem.MAX_ENERGY - armor.getEnergyStored(chest);
                float normalHealth = player.getHealth();
                check(controlAccepted && normalCost > 0 && normalHealth < 20,
                        "ordinary standalone hurt control did not execute/spend IF");
                armor.setEnergy(chest, ImagEnergyArmorItem.MAX_ENERGY);
                player.setHealth(20); player.invulnerableTime = 0;
                listener = new NestedOrdinary(outer, player, normalSource);
                NeoForge.EVENT_BUS.register(listener);
                boolean outerAccepted = AcademyDamageHelper.hurt(attacker.player(), outer, outer.damageSources().generic(), 4);
                int nestedCost = ImagEnergyArmorItem.MAX_ENERGY - armor.getEnergyStored(chest);
                LogUtils.getLogger().info("RECHECK_IMAGINARY_NESTED calls={} outerAccepted={} nestedAccepted={} standaloneIF={} nestedIF={} standaloneHP={} nestedHP={} abilityContextAfter={}",
                        listener.seen, outerAccepted, listener.accepted, normalCost, nestedCost, normalHealth, player.getHealth(), AcademyDamageHelper.isAbilityDamageInProgress());
                check(outerAccepted && listener.seen == 1 && listener.accepted && outer.getHealth() < outer.getMaxHealth(),
                        "single finite nested ordinary hurt did not execute inside real Academy hurt");
                check(!AcademyDamageHelper.isAbilityDamageInProgress(), "outer ability context leaked after return");
                check(nestedCost == normalCost && near(player.getHealth(), normalHealth),
                        "ordinary nested damage inherited ability armour rate: ordinaryIF=" + normalCost + " nestedIF=" + nestedCost
                                + " ordinaryHP=" + normalHealth + " nestedHP=" + player.getHealth());
                h.succeed();
            } finally {
                if (listener != null) NeoForge.EVENT_BUS.unregister(listener);
                outer.discard(); ACConfig.Server.PVP_ENABLED.set(oldAcademyPvp); h.getLevel().getServer().setPvpAllowed(oldPvp);
                attacker.close(); defender.close();
            }
        });
    }

    private static ItemStack chargedChest(ServerPlayer player) {
        ItemStack stack = new ItemStack(AcademyItems.IMAG_CHESTPLATE.get());
        check(stack.getItem() instanceof ImagEnergyArmorItem, "real imaginary chest item type missing");
        ((ImagEnergyArmorItem) stack.getItem()).setEnergy(stack, ImagEnergyArmorItem.MAX_ENERGY);
        player.setItemSlot(EquipmentSlot.CHEST, stack);
        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    public static final class ObserveIncoming {
        final UUID target; final boolean deny; int seen;
        ObserveIncoming(LivingEntity target, boolean deny) { this.target = target.getUUID(); this.deny = deny; }
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void incoming(LivingIncomingDamageEvent event) {
            if (!target.equals(event.getEntity().getUUID())) return;
            seen++; if (deny) event.setCanceled(true);
        }
    }

    public static final class NestedOrdinary {
        final UUID outer; final ServerPlayer target; final DamageSource source;
        int seen; boolean accepted;
        NestedOrdinary(LivingEntity outer, ServerPlayer target, DamageSource source) {
            this.outer = outer.getUUID(); this.target = target; this.source = source;
        }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void incoming(LivingIncomingDamageEvent event) {
            if (!outer.equals(event.getEntity().getUUID())) return;
            check(++seen == 1, "unexpected nested callback recursion");
            // Deliberately ordinary damage, without any Academy helper or synthetic event.
            accepted = target.hurt(source, 8);
        }
    }

    private static boolean near(float a, float b) { return Math.abs(a - b) < .01F; }
    private static void check(boolean value, String message) { if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message); }
}
