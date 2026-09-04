package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative runtime for Telekinesis passives. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class TelekinesisPassiveHandler {
    private static final String SHADOW_TAG = "academy_liquid_shadow";
    private static final String OWNER_PREFIX = "academy_liquid_shadow_owner_";
    private static final Map<UUID, Anchor> HARDENED = new HashMap<>();
    private static final Map<UUID, UUID> SHADOWS = new HashMap<>();

    private record Anchor(ResourceKey<Level> dimension, Vec3 position) {}
    private TelekinesisPassiveHandler() {}

    private static boolean passive(ServerPlayer player, PlayerAbilityData data, String id) {
        return player.isAlive() && !AbilityInterferenceService.isInterfered(player)
                && data.isAbilityActive()
                && data.getCurrentAbility() == AbilityCategory.TELEKINESIS
                && data.hasLearnedSkill(id);
    }

    /** Called exclusively by AcademyDamageHelper, so ordinary melee/environment damage is not mislabeled. */
    public static float mitigateAbilityDamage(ServerPlayer attacker, Entity target, float amount) {
        if (!(target instanceof ServerPlayer defender) || attacker == defender
                || !Float.isFinite(amount) || amount <= 0) return amount;
        PlayerAbilityData defense = defender.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!passive(defender, defense, "insulation")) return amount;
        PlayerAbilityData offense = attacker.getData(AcademyAttachments.PLAYER_ABILITY);
        AbilityCategory source = offense.getCurrentAbility();
        float proficiency = defense.getProficiency("insulation");
        float multiplier = source == AbilityCategory.MELTDOWNER ? 2.5F
                : source == AbilityCategory.ELECTROMASTER ? 2.0F : 1.0F;
        float reduction = (0.10F + 0.10F * proficiency) * multiplier;
        if (!DynamicSkillRules.tryPay(defense, "insulation", 100F * amount, 0)) return amount;
        float reduced = amount * (1F - Math.clamp(reduction, 0, 0.95F));
        if (reduced < amount) {
            AbilityMutationService.addSkillExp(defender, defense, "insulation", amount * 0.0003f);
            EffectHelper.psychoBurst(defender.serverLevel(), defender.getX(),
                    defender.getY() + defender.getBbHeight() * 0.55, defender.getZ(), 2, 0.18);
        }
        return reduced;
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        tickPerfectPaperTraining(player, data);
        Anchor anchor = HARDENED.get(player.getUUID());
        if (anchor != null && (!passive(player, data, "psycho_harden")
                || !anchor.dimension().equals(player.level().dimension()))) {
            stopHarden(player, data, true);
            anchor = null;
        }
        if (anchor != null) {
            if (!DynamicSkillRules.tryPay(data, "psycho_harden", 2F, 0)) {
                stopHarden(player, data, true);
                tickShadow(player, data);
                return;
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 39, 9,
                    false, false, false));
            DynamicSkillRules.addExp(player, data, "psycho_harden", 0.00001F);
            if (player.tickCount % 10 == 0) {
                EffectHelper.psychoBurst(player.serverLevel(), player.getX(),
                        player.getY() + player.getBbHeight() * 0.5, player.getZ(), 2, 0.12);
            }
        }
        tickShadow(player, data);
    }

    /**
     * Perfect Paper is passive but gates skills at 0.5/1.0 proficiency.  Training while handling
     * paper gives that passive an attainable, server-owned progression path instead of leaving
     * its descendants permanently locked at the initial zero proficiency.
     */
    private static void tickPerfectPaperTraining(ServerPlayer player, PlayerAbilityData data) {
        if (player.tickCount % 5 != 0 || !passive(player, data, "perfect_paper")) return;
        if (!player.getMainHandItem().is(Items.PAPER)
                && !player.getOffhandItem().is(Items.PAPER)) return;
        AbilityMutationService.addSkillExp(player, data, "perfect_paper", 0.00125f);
        if (player.tickCount % 20 == 0) {
            EffectHelper.psychoBurst(player.serverLevel(), player.getX(),
                    player.getY() + 1.0, player.getZ(), 1, 0.08);
        }
    }

    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT)
                && passive(player, data, "insulation")) {
            float original = event.getAmount();
            if (DynamicSkillRules.tryPay(data, "insulation", 100F * original, 0)) {
                float reduction = 0.20F + 0.20F * data.getProficiency("insulation");
                event.setAmount(original * (1F - reduction));
                DynamicSkillRules.addExp(player, data, "insulation", original * 0.002F);
            }
        }
        if (!HARDENED.containsKey(player.getUUID())) return;
        if (!passive(player, data, "psycho_harden")) {
            stopHarden(player, data, false);
            return;
        }
        float blocked = event.getAmount();
        float p = data.getProficiency("psycho_harden");
        if (!DynamicSkillRules.tryPay(data, "psycho_harden",
                blocked * (60F - 30F * p), blocked * 0.1F)) {
            stopHarden(player, data, true);
            return;
        }
        event.setAmount(0);
        AbilityMutationService.addSkillExp(player, data, "psycho_harden", blocked * 0.0005f);
        EffectHelper.psychoBurst(player.serverLevel(), player.getX(),
                player.getY() + player.getBbHeight() * 0.5, player.getZ(), 6, 0.25);
    }

    public static boolean isHardened(ServerPlayer player) {
        return HARDENED.containsKey(player.getUUID());
    }

    public static boolean togglePsychoHarden(ServerPlayer player, PlayerAbilityData data) {
        if (isHardened(player)) {
            stopHarden(player, data, true);
            return true;
        }
        float p = data.getProficiency("psycho_harden");
        if (!DynamicSkillRules.tryPay(data, "psycho_harden",
                1500F - 500F * p, 100F - 50F * p)) return false;
        HARDENED.put(player.getUUID(), new Anchor(player.level().dimension(), player.position()));
        EffectHelper.psychoBurst(player.serverLevel(), player.getX(), player.getY() + 0.9,
                player.getZ(), 12, 0.45);
        return true;
    }

    private static void stopHarden(ServerPlayer player, PlayerAbilityData data, boolean cooldown) {
        if (!HARDENED.containsKey(player.getUUID())) return;
        HARDENED.remove(player.getUUID());
        if (cooldown && !data.isDevMode()) data.setCooldown("psycho_harden", 160);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4,
                false, false, false));
    }

    public static boolean hasLiquidShadow(ServerPlayer player) { return resolveShadow(player) != null; }

    public static boolean hasWaterBucket(ServerPlayer player) {
        if (player.getAbilities().instabuild) return true;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            if (player.getInventory().getItem(slot).is(Items.WATER_BUCKET)) return true;
        return player.getOffhandItem().is(Items.WATER_BUCKET);
    }

    /** Toggle seam kept public for dedicated-server GameTests and skill dispatch. */
    public static boolean toggleLiquidShadow(ServerPlayer player) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!passive(player, data, "liquid_shadow")) return false;
        Drowned existing = resolveShadow(player);
        if (existing != null) {
            existing.discard();
            SHADOWS.remove(player.getUUID());
            if (!data.isDevMode()) data.setCooldown("liquid_shadow", 100);
            return true;
        }
        if (!hasWaterBucket(player)) return false;
        float p = data.getProficiency("liquid_shadow");
        if (!DynamicSkillRules.tryPay(data, "liquid_shadow",
                2000F - 1000F * p, 300F - 100F * p)) return false;
        ServerLevel level = player.serverLevel();
        Drowned shadow = EntityType.DROWNED.create(level);
        if (shadow == null) return false;
        Vec3 spawn = shadowTarget(player);
        shadow.setPos(spawn.x, spawn.y, spawn.z);
        shadow.setNoAi(true);
        shadow.setSilent(true);
        shadow.setPersistenceRequired();
        shadow.setCustomName(Component.translatable(
                "item.academy.factor_telekinesis.liquid_shadow"));
        shadow.setCustomNameVisible(false);
        shadow.addTag(SHADOW_TAG);
        shadow.addTag(ownerTag(player.getUUID()));
        shadow.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 60,
                0, false, false));
        var maxHealth = shadow.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(100.0);
        shadow.setHealth(100.0F);
        if (!level.addFreshEntity(shadow)) return false;
        SHADOWS.put(player.getUUID(), shadow.getUUID());
        if (!player.getAbilities().instabuild) {
            consumeWaterBucket(player);
        }
        EffectHelper.psychoBurst(level, spawn.x, spawn.y + 0.8, spawn.z, 16, 0.4);
        AbilityMutationService.addSkillExp(player, data, "liquid_shadow", 0.002f);
        return true;
    }

    private static void tickShadow(ServerPlayer player, PlayerAbilityData data) {
        Drowned shadow = resolveShadow(player);
        if (shadow == null) return;
        if (shadow.isInWater()) {
            stopShadow(player, data, true);
            return;
        }
        if (!passive(player, data, "liquid_shadow")) {
            stopShadow(player, data, false);
            return;
        }
        if (!DynamicSkillRules.tryPay(data, "liquid_shadow", 1F, 0)) {
            stopShadow(player, data, true);
            return;
        }
        LivingEntity victim = validShadowVictim(player, shadow, player.getLastHurtMob())
                ? player.getLastHurtMob() : validShadowVictim(player, shadow, player.getLastHurtByMob())
                ? player.getLastHurtByMob() : null;
        Vec3 target = victim == null ? shadowTarget(player) : victim.position();
        Vec3 delta = target.subtract(shadow.position());
        if (delta.lengthSqr() > 64) {
            shadow.setPos(target.x, target.y, target.z);
            shadow.setDeltaMovement(Vec3.ZERO);
        } else {
            shadow.setDeltaMovement(delta.scale(0.22));
            shadow.hurtMarked = true;
        }
        if (victim != null && shadow.distanceToSqr(victim) <= 4 && player.tickCount % 20 == 0) {
            float damage = 20F + 10F * data.getProficiency("liquid_shadow");
            if (com.mohistmc.academy.skill.AcademyDamageHelper.hurt(player, victim,
                    player.damageSources().playerAttack(player), damage)) {
                if (!data.isDevMode()) data.tryConsumeDynamic(5F * damage, 0);
                AbilityMutationService.addSkillExp(player, data, "liquid_shadow", damage * 0.0001F);
            }
        }
        shadow.setRemainingFireTicks(0);
        if (player.tickCount % 2 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.SPLASH,
                    shadow.getX(), shadow.getY() + 0.9, shadow.getZ(),
                    5, 0.32, 0.75, 0.32, 0.02);
        }
        DynamicSkillRules.addExp(player, data, "liquid_shadow", 0.0001F);
    }

    private static boolean validShadowVictim(ServerPlayer player, Drowned shadow, LivingEntity victim) {
        return victim != null && victim != player && victim != shadow && victim.isAlive()
                && !player.isAlliedTo(victim) && player.distanceToSqr(victim) <= 64
                && com.mohistmc.academy.skill.AcademyDamageHelper.allowsTarget(victim);
    }

    private static Drowned resolveShadow(ServerPlayer player) {
        UUID id = SHADOWS.get(player.getUUID());
        Entity entity = id == null ? null : player.serverLevel().getEntity(id);
        if (entity instanceof Drowned drowned && drowned.isAlive()) return drowned;
        String ownerTag = ownerTag(player.getUUID());
        Drowned recovered = player.serverLevel().getEntitiesOfClass(Drowned.class,
                        player.getBoundingBox().inflate(128), candidate -> candidate.isAlive()
                                && candidate.getTags().contains(SHADOW_TAG)
                                && candidate.getTags().contains(ownerTag))
                .stream().findFirst().orElse(null);
        if (recovered == null) SHADOWS.remove(player.getUUID());
        else SHADOWS.put(player.getUUID(), recovered.getUUID());
        return recovered;
    }

    private static Vec3 shadowTarget(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0e-5) horizontal = new Vec3(0, 0, 1);
        return player.position().subtract(horizontal.normalize().scale(1.6)).add(0, 0.1, 0);
    }

    private static String ownerTag(UUID owner) { return OWNER_PREFIX + owner; }

    private static void consumeWaterBucket(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.WATER_BUCKET)) {
                stack.shrink(1);
                if (!player.getInventory().add(new ItemStack(Items.BUCKET)))
                    player.drop(new ItemStack(Items.BUCKET), false);
                return;
            }
        }
    }

    private static void stopShadow(ServerPlayer player, PlayerAbilityData data, boolean cooldown) {
        Drowned shadow = resolveShadow(player);
        if (shadow != null) shadow.discard();
        SHADOWS.remove(player.getUUID());
        if (cooldown && !data.isDevMode()) data.setCooldown("liquid_shadow", 100);
    }

    private static void clearPlayer(Entity entity) {
        HARDENED.remove(entity.getUUID());
        UUID shadowId = SHADOWS.remove(entity.getUUID());
        if (shadowId != null && entity.level() instanceof ServerLevel level) {
            Entity shadow = level.getEntity(shadowId);
            if (shadow != null) shadow.discard();
        }
    }

    @SubscribeEvent public static void drops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains(SHADOW_TAG)) event.setCanceled(true);
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getTags().contains(SHADOW_TAG)) {
            UUID ownerId = SHADOWS.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(entity.getUUID()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (ownerId != null && entity.level() instanceof ServerLevel level) {
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
                if (owner != null) {
                    PlayerAbilityData data = owner.getData(AcademyAttachments.PLAYER_ABILITY);
                    if (!data.isDevMode()) data.setCooldown("liquid_shadow", 100);
                }
                SHADOWS.remove(ownerId);
            } else {
                SHADOWS.values().removeIf(entity.getUUID()::equals);
            }
        } else if (entity instanceof ServerPlayer) clearPlayer(entity);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        HARDENED.remove(event.getEntity().getUUID());
        UUID shadowId = SHADOWS.remove(event.getEntity().getUUID());
        if (shadowId != null && event.getEntity() instanceof ServerPlayer player) {
            ServerLevel oldLevel = player.server.getLevel(event.getFrom());
            if (oldLevel != null) {
                Entity shadow = oldLevel.getEntity(shadowId);
                if (shadow != null) shadow.discard();
            }
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clearPlayer(event.getEntity()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        HARDENED.clear();
        SHADOWS.clear();
    }
}
