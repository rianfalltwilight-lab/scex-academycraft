package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.AcademyCraft;
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
import net.minecraft.world.entity.monster.Drowned;
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
        boolean favored = source == AbilityCategory.ELECTROMASTER || source == AbilityCategory.MELTDOWNER;
        float reduced = TelekinesisRules.mitigateAbilityDamage(amount,
                defense.getProficiency("insulation"), favored);
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
        boolean available = passive(player, data, "psycho_harden");
        Anchor anchor = HARDENED.get(player.getUUID());
        if (anchor == null && TelekinesisRules.mayEnterHardenedStance(
                available, player.isShiftKeyDown(), player.onGround())) {
            anchor = new Anchor(player.level().dimension(), player.position());
            HARDENED.put(player.getUUID(), anchor);
        }
        if (anchor != null && (!available || !player.isShiftKeyDown()
                || !anchor.dimension().equals(player.level().dimension()))) {
            HARDENED.remove(player.getUUID());
            anchor = null;
        }
        if (anchor != null) {
            Vec3 fixed = anchor.position();
            if (player.position().distanceToSqr(fixed) > 1.0e-6) {
                // Correct both the authoritative entity and the remote client's prediction.
                player.connection.teleport(fixed.x, fixed.y, fixed.z,
                        player.getYRot(), player.getXRot());
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.fallDistance = 0;
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
        if (!HARDENED.containsKey(player.getUUID())) return;
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!passive(player, data, "psycho_harden") || !player.isShiftKeyDown()) {
            HARDENED.remove(player.getUUID());
            return;
        }
        float blocked = event.getAmount();
        event.setAmount(0);
        AbilityMutationService.addSkillExp(player, data, "psycho_harden", blocked * 0.0002f);
        EffectHelper.psychoBurst(player.serverLevel(), player.getX(),
                player.getY() + player.getBbHeight() * 0.5, player.getZ(), 6, 0.25);
    }

    @SubscribeEvent
    public static void rightClickItem(PlayerInteractEvent.RightClickItem event) {
        handleShadowInteraction(event, event.getItemStack());
    }

    @SubscribeEvent
    public static void rightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        handleShadowInteraction(event, event.getItemStack());
    }

    private static void handleShadowInteraction(PlayerInteractEvent event, ItemStack held) {
        if (!event.getEntity().isShiftKeyDown()) return;
        UUID owner = event.getEntity().getUUID();
        boolean mayStop = SHADOWS.containsKey(owner) && held.is(Items.BUCKET);
        if (!held.is(Items.WATER_BUCKET) && !mayStop) return;
        PlayerAbilityData data = event.getEntity().getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.isAbilityActive() || data.getCurrentAbility() != AbilityCategory.TELEKINESIS
                || !data.hasLearnedSkill("liquid_shadow")) return;
        if (event instanceof PlayerInteractEvent.RightClickItem item) {
            item.setCanceled(true);
            item.setCancellationResult(InteractionResult.SUCCESS);
        } else if (event instanceof PlayerInteractEvent.RightClickBlock block) {
            block.setCanceled(true);
            block.setCancellationResult(InteractionResult.SUCCESS);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            toggleLiquidShadow(player, event.getHand());
        }
    }

    /** Toggle seam kept public for dedicated-server GameTests. */
    public static boolean toggleLiquidShadow(ServerPlayer player, InteractionHand hand) {
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!passive(player, data, "liquid_shadow")) return false;
        Drowned existing = resolveShadow(player);
        if (existing != null) {
            existing.discard();
            SHADOWS.remove(player.getUUID());
            return true;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.WATER_BUCKET)) return false;
        ServerLevel level = player.serverLevel();
        Drowned shadow = EntityType.DROWNED.create(level);
        if (shadow == null) return false;
        Vec3 spawn = shadowTarget(player);
        shadow.setPos(spawn.x, spawn.y, spawn.z);
        shadow.setNoAi(true);
        shadow.setNoGravity(true);
        shadow.setSilent(true);
        shadow.setPersistenceRequired();
        shadow.setCustomName(Component.translatable(
                "item.academy.factor_telekinesis.liquid_shadow"));
        shadow.setCustomNameVisible(false);
        shadow.addTag(SHADOW_TAG);
        shadow.addTag(ownerTag(player.getUUID()));
        shadow.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 60,
                0, false, false));
        if (!level.addFreshEntity(shadow)) return false;
        SHADOWS.put(player.getUUID(), shadow.getUUID());
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        }
        EffectHelper.psychoBurst(level, spawn.x, spawn.y + 0.8, spawn.z, 16, 0.4);
        AbilityMutationService.addSkillExp(player, data, "liquid_shadow", 0.005f);
        return true;
    }

    private static void tickShadow(ServerPlayer player, PlayerAbilityData data) {
        Drowned shadow = resolveShadow(player);
        if (shadow == null) return;
        if (!passive(player, data, "liquid_shadow")) {
            shadow.discard();
            SHADOWS.remove(player.getUUID());
            return;
        }
        Vec3 target = shadowTarget(player);
        Vec3 delta = target.subtract(shadow.position());
        if (delta.lengthSqr() > 64) {
            shadow.setPos(target.x, target.y, target.z);
            shadow.setDeltaMovement(Vec3.ZERO);
        } else {
            shadow.setDeltaMovement(delta.scale(0.22));
            shadow.hurtMarked = true;
        }
        shadow.setRemainingFireTicks(0);
        if (player.tickCount % 2 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.SPLASH,
                    shadow.getX(), shadow.getY() + 0.9, shadow.getZ(),
                    5, 0.32, 0.75, 0.32, 0.02);
        }
        if (player.tickCount % 20 == 0) {
            AbilityMutationService.addSkillExp(player, data, "liquid_shadow", 0.00025f);
        }
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
            SHADOWS.values().removeIf(entity.getUUID()::equals);
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
