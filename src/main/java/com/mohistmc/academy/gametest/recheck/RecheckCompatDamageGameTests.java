package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional exact-snapshot integration tests: real hurt, real Curios slots, no third-party compile dependency. */
@EventBusSubscriber(modid = AcademyCraft.MODID, bus = EventBusSubscriber.Bus.MOD)
@PrefixGameTestTemplate(false)
public final class RecheckCompatDamageGameTests {
    private static final List<String> REQUIRED_MODS = List.of(
            "botania", "patchouli", "curios", "thaumcraft", "thaumicbases", "dummmmmmy", "modularrouters");
    private RecheckCompatDamageGameTests() {}

    // Deliberately no @GameTestHolder: only this explicit registration can enable the suite.
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("academy.recheckCompatGate")) return;
        List<String> missing = REQUIRED_MODS.stream().filter(id -> !ModList.get().isLoaded(id)).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("Recheck compatibility gate requested with missing mods: " + missing);
        event.register(RecheckCompatDamageGameTests.class);
        LogUtils.getLogger().info("RECHECK_COMPAT_REGISTERED cases=6 requiredMods={} snapshotOnly=true", REQUIRED_MODS);
    }

    @GameTest(template = "empty", templateNamespace = "academy_recheck_compat", timeoutTicks = 180)
    public static void realCuriosCloakZerosAcceptedDamage(GameTestHelper h) { cloak(h, "control"); }

    @GameTest(template = "empty", templateNamespace = "academy_recheck_compat", timeoutTicks = 180)
    public static void cloakZeroDoesNotChargeLightningInsulation(GameTestHelper h) { cloak(h, "lightning_insulation"); }

    @GameTest(template = "empty", templateNamespace = "academy_recheck_compat", timeoutTicks = 180)
    public static void cloakZeroDoesNotChargeAbilityInsulation(GameTestHelper h) { cloak(h, "ability_insulation"); }

    @GameTest(template = "empty", templateNamespace = "academy_recheck_compat", timeoutTicks = 180)
    public static void cloakZeroDoesNotChargeOffenseArmour(GameTestHelper h) { cloak(h, "offense_armour"); }

    private static void cloak(GameTestHelper h, String mode) {
        var defender = RecheckCompatPlayers.connect(h);
        var attacker = RecheckCompatPlayers.connect(h);
        h.runAfterDelay(65, () -> {
            ServerPlayer player = defender.player();
            var observer = new ObserveIncoming(player, false);
            boolean previousPvp = ACConfig.Server.PVP_ENABLED.get();
            boolean previousServerPvp = h.getLevel().getServer().isPvpAllowed();
            WornCloak worn = null;
            NeoForge.EVENT_BUS.register(observer);
            try {
                ACConfig.Server.PVP_ENABLED.set(true);
                h.getLevel().getServer().setPvpAllowed(true);
                PlayerAbilityData data = ability(player,
                        mode.equals("offense_armour") ? AbilityCategory.AEROHAND : AbilityCategory.TELEKINESIS);
                ability(attacker.player(), AbilityCategory.ELECTROMASTER);
                if (mode.contains("insulation")) { data.learnSkill("insulation"); data.setProficiency("insulation", .5F); }
                if (mode.equals("offense_armour")) {
                    data.learnSkill("offense_armour"); data.setProficiency("offense_armour", .5F);
                    check(AeroPassiveRuntime.toggleOffenseArmour(player, data), "offense armour did not engage");
                }
                worn = WornCloak.equip(player);
                player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0;
                float healthBefore = player.getHealth();
                CompoundTag before = data.toSyncTag().copy();
                DamageSource source = mode.equals("lightning_insulation")
                        ? player.damageSources().lightningBolt()
                        : mode.equals("offense_armour") ? player.damageSources().playerAttack(attacker.player())
                        : player.damageSources().generic();
                boolean accepted = mode.equals("ability_insulation")
                        ? AcademyDamageHelper.hurt(attacker.player(), player, source, 8)
                        : player.hurt(source, 8);
                boolean cloakUsed = player.getCooldowns().isOnCooldown(worn.item);
                CompoundTag after = data.toSyncTag();
                LogUtils.getLogger().info("RECHECK_COMPAT_CLOAK mode={} incoming={} accepted={} health={}=>{} cloakCooldown={} cp={}=>{} zeroAtLowest={} canceledAtLowest={}",
                        mode, observer.seen, accepted, healthBefore, player.getHealth(), cloakUsed,
                        before.getFloat("cp"), after.getFloat("cp"), observer.amount == 0, observer.canceled);
                check(observer.seen == 1 && near(player.getHealth(), healthBefore) && cloakUsed,
                        "real worn Botania cloak did not handle damage: mode=" + mode + " incoming=" + observer.seen + " cooldown=" + cloakUsed);
                check(observer.amount == 0 || observer.canceled,
                        "Botania cloak neither zeroed nor canceled incoming damage: " + observer.amount);
                check(before.equals(after), "cloak-zeroed hurt changed Academy CP/growth/proficiency: mode=" + mode
                        + " before=" + before + " after=" + after);
                if (mode.equals("offense_armour")) check(AeroPassiveRuntime.isOffenseArmourEngaged(player), "cloak-zeroed hit stopped armour");
                h.succeed();
            } finally {
                if (worn != null) worn.close();
                AeroPassiveRuntime.terminateSustained(player, player.getData(AcademyAttachments.PLAYER_ABILITY));
                NeoForge.EVENT_BUS.unregister(observer);
                ACConfig.Server.PVP_ENABLED.set(previousPvp);
                h.getLevel().getServer().setPvpAllowed(previousServerPvp);
                attacker.close(); defender.close();
            }
        });
    }

    @GameTest(template = "empty", templateNamespace = "academy_recheck_compat")
    public static void realDummyRecordsDamageWithoutLosingHealthAndHonorsCancel(GameTestHelper h) {
        ResourceLocation id = ResourceLocation.parse("dummmmmmy:target_dummy");
        check(BuiltInRegistries.ENTITY_TYPE.containsKey(id), "actual dummy entity is not registered");
        Entity created = BuiltInRegistries.ENTITY_TYPE.get(id).create(h.getLevel());
        check(created instanceof LivingEntity, "dummy is not a LivingEntity");
        LivingEntity dummy = (LivingEntity) created;
        position(h, dummy, 2, 2, 2); dummy.setNoGravity(true);
        if (dummy instanceof Mob mob) mob.setNoAi(true);
        h.getLevel().addFreshEntity(dummy);
        var observer = new ObserveIncoming(dummy, false);
        NeoForge.EVENT_BUS.register(observer);
        try {
            dummy.setHealth(dummy.getMaxHealth()); dummy.invulnerableTime = 0;
            check((boolean) invoke(dummy.getClass(), dummy, "hasInfiniteHealth", new Class<?>[0]), "dummy fixture not in infinite-health mode");
            float health = dummy.getHealth(); float recorded = dummyDamage(dummy);
            boolean accepted = dummy.hurt(dummy.damageSources().generic(), 6);
            float recordedAfter = dummyDamage(dummy);
            check(accepted && observer.seen == 1 && near(dummy.getHealth(), health) && recordedAfter > recorded,
                    "actual dummy must accept/record real hurt while retaining HP: accepted=" + accepted + " records=" + recorded + "=>" + recordedAfter);
            observer.deny = true; dummy.invulnerableTime = 0;
            boolean rejected = dummy.hurt(dummy.damageSources().generic(), 5);
            check(!rejected && observer.seen == 2 && near(dummyDamage(dummy), recordedAfter) && near(dummy.getHealth(), health),
                    "canceled real dummy hurt changed health/damage record");
            LogUtils.getLogger().info("RECHECK_COMPAT_DUMMY accepted={} hp={} recorded={} canceledAccepted={} recordAfterCancel={}",
                    accepted, health, recordedAfter, rejected, dummyDamage(dummy));
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observer); dummy.discard(); }
    }

    @GameTest(template = "empty", templateNamespace = "academy_recheck_compat")
    public static void actualVoidSpikeUsesFakePlayerAndHonorsCancel(GameTestHelper h) {
        ResourceLocation id = ResourceLocation.parse("thaumicbases:void_spike");
        check(BuiltInRegistries.BLOCK.containsKey(id), "actual void spike is not registered");
        var block = BuiltInRegistries.BLOCK.get(id);
        BlockPos pos = h.absolutePos(new BlockPos(2, 1, 2));
        h.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        var pig = EntityType.PIG.create(h.getLevel());
        check(pig != null, "pig creation failed");
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); pig.setHealth(100);
        pig.setNoAi(true); pig.setNoGravity(true); position(h, pig, 2, 2, 2);
        h.getLevel().addFreshEntity(pig);
        var observer = new ObserveIncoming(pig, true);
        NeoForge.EVENT_BUS.register(observer);
        try {
            invokeSpike(block, h.getLevel().getBlockState(pos), h.getLevel(), pos, pig);
            check(observer.seen == 1 && observer.sourceEntity instanceof FakePlayer && near(pig.getHealth(), 100),
                    "actual void spike cancel/source control failed: seen=" + observer.seen + " source=" + observer.sourceEntity);
            observer.deny = false; pig.invulnerableTime = 0;
            invokeSpike(block, h.getLevel().getBlockState(pos), h.getLevel(), pos, pig);
            check(observer.seen == 2 && observer.sourceEntity instanceof FakePlayer && near(pig.getHealth(), 80),
                    "actual void spike did not apply 20 damage with FakePlayer source: hp=" + pig.getHealth());
            LogUtils.getLogger().info("RECHECK_COMPAT_VOID_SPIKE source={} sourceType={} events={} health={} canceledThenAccepted=true",
                    observer.sourceEntity.getUUID(), observer.sourceEntity.getClass().getName(), observer.seen, pig.getHealth());
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(observer); pig.discard(); h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3); }
    }

    private static void invokeSpike(Object block, BlockState state, Level level, BlockPos pos, Entity target) {
        // Calls the installed block's real collision entry against a placed world block, not a synthesized damage event.
        invoke(block.getClass(), block, "entityInside", new Class<?>[]{BlockState.class, Level.class, BlockPos.class, Entity.class}, state, level, pos, target);
    }

    private static float dummyDamage(LivingEntity dummy) {
        try {
            Field field = dummy.getClass().getDeclaredField("totalDamageTakenInCombat"); field.setAccessible(true);
            return field.getFloat(dummy);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("actual dummy damage ledger inaccessible", e); }
    }

    private static PlayerAbilityData ability(ServerPlayer player, AbilityCategory category) {
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category); data.setPlayerLevel(5); data.setAbilityActive(true); data.setDevMode(false);
        data.setCurrentCp(8000); data.setCurrentOverload(0);
        return data;
    }

    private static void position(GameTestHelper h, Entity entity, double x, double y, double z) {
        BlockPos origin = h.absolutePos(BlockPos.ZERO); entity.setPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    private static final class WornCloak implements AutoCloseable {
        final Object stacks;
        final Class<?> stackApi;
        final ItemStack original;
        final Item item;
        private WornCloak(Object stacks, Class<?> stackApi, ItemStack original, Item item) {
            this.stacks = stacks; this.stackApi = stackApi; this.original = original; this.item = item;
        }
        static WornCloak equip(ServerPlayer player) {
            ResourceLocation id = ResourceLocation.parse("botania:cloak_of_virtue");
            check(BuiltInRegistries.ITEM.containsKey(id), "actual Botania cloak not registered");
            Item item = BuiltInRegistries.ITEM.get(id);
            Class<?> api = type("top.theillusivec4.curios.api.CuriosApi");
            Class<?> handlerApi = type("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> slotApi = type("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Class<?> stackApi = type("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            Object handler = ((Optional<?>) invoke(api, null, "getCuriosInventory", new Class<?>[]{LivingEntity.class}, player)).orElseThrow();
            Object body = ((Optional<?>) invoke(handlerApi, handler, "getStacksHandler", new Class<?>[]{String.class}, "body")).orElseThrow();
            Object stacks = invoke(slotApi, body, "getStacks", new Class<?>[0]);
            check((int) invoke(stackApi, stacks, "getSlots", new Class<?>[0]) > 0, "real Curios body slot missing");
            ItemStack old = ((ItemStack) invoke(stackApi, stacks, "getStackInSlot", new Class<?>[]{int.class}, 0)).copy();
            WornCloak result = new WornCloak(stacks, stackApi, old, item);
            try {
                invoke(stackApi, stacks, "setStackInSlot", new Class<?>[]{int.class, ItemStack.class}, 0, new ItemStack(item));
                player.getCooldowns().removeCooldown(item);
                ItemStack found = (ItemStack) invoke(type("vazkii.botania.common.handler.EquipmentHandler"), null,
                        "findOrEmpty", new Class<?>[]{Item.class, LivingEntity.class}, item, player);
                check(found.is(item), "Botania did not identify cloak in actual Curios body slot");
                return result;
            } catch (RuntimeException | Error problem) { result.close(); throw problem; }
        }
        @Override public void close() { invoke(stackApi, stacks, "setStackInSlot", new Class<?>[]{int.class, ItemStack.class}, 0, original); }
    }

    public static final class ObserveIncoming {
        final UUID target;
        boolean deny, canceled;
        int seen;
        float amount;
        Entity sourceEntity;
        ObserveIncoming(LivingEntity target, boolean deny) { this.target = target.getUUID(); this.deny = deny; }
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void incoming(LivingIncomingDamageEvent event) {
            if (!target.equals(event.getEntity().getUUID())) return;
            seen++; amount = event.getAmount(); sourceEntity = event.getSource().getEntity();
            if (deny) event.setCanceled(true);
            canceled = event.isCanceled();
        }
    }

    private static Class<?> type(String name) {
        try { return Class.forName(name); }
        catch (ClassNotFoundException e) { throw new IllegalStateException("required integration class absent: " + name, e); }
    }
    private static Object invoke(Class<?> api, Object receiver, String method, Class<?>[] signature, Object... args) {
        try { return api.getMethod(method, signature).invoke(receiver, args); }
        catch (InvocationTargetException e) { throw new IllegalStateException("actual integration method failed: " + api.getName() + "." + method, e.getCause()); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("actual integration API unavailable: " + api.getName() + "." + method, e); }
    }
    private static boolean near(float a, float b) { return Math.abs(a - b) < .01F; }
    private static void check(boolean value, String message) { if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message); }
}