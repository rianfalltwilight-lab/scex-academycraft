package com.mohistmc.academy.regression;

import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.skill.ability.teleporter.*;
import com.mohistmc.academy.skill.ability.electromaster.*;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.item.*;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Adversarial probes execute the real release implementation in an isolated NeoForge server. */
@GameTestHolder("academy_audit")
@PrefixGameTestTemplate(false)
public final class AuditGameTests {
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 3, 3))));
        p.setYRot(0); p.setXRot(0);
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.TELEPORTER);
        data.setPlayerLevel(5); data.setCurrentCp(data.getMaxCp()); data.setCurrentOverload(0);
        data.setAbilityActive(true);
        return p;
    }

    private static BlockPos placementWall(ServerPlayer p) {
        BlockPos wall = p.blockPosition().offset(0, 1, 4);
        p.serverLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        p.serverLevel().setBlock(wall.north().below(), Blocks.STONE.defaultBlockState(), 3);
        return wall;
    }

    private static void check(GameTestHelper h, boolean ok, String message) { h.assertTrue(ok, message); }

    private static int drops(ServerPlayer p, net.minecraft.world.item.Item item) {
        return p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(10),
                e -> e.getItem().is(item)).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void canceledPlacement(GameTestHelper h, boolean multi) {
        var p = player(h); var level = h.getLevel(); var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        var wall = placementWall(p); var target = wall.north();
        var item = multi ? Items.OAK_DOOR : Items.BRICKS;
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 2));
        int[] events = {0}; boolean[] multiSeen = {false};
        Consumer<BlockEvent.EntityPlaceEvent> deny = e -> {
            if (e.getEntity() == p) { events[0]++; multiSeen[0] |= e instanceof BlockEvent.EntityMultiPlaceEvent; e.setCanceled(true); }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, deny);
        try {
            check(h, new ShiftTpEffect().tryRelease(p, data, 1), "Canceled placement must retain the legacy remote-drop branch");
        } finally { NeoForge.EVENT_BUS.unregister(deny); }
        check(h, events[0] == 1 && multiSeen[0] == multi, "Wrong placement event path: count=" + events[0] + ", multi=" + multiSeen[0]);
        check(h, level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir(), "Canceled placement left a block behind");
        check(h, p.getMainHandItem().getCount() == 1 && drops(p, item) == 1, "Canceled placement duplicated/lost an item");
        h.succeed();
    }

    @GameTest(template = "empty") public static void shiftSinglePlacementCancel(GameTestHelper h) { canceledPlacement(h, false); }
    @GameTest(template = "empty") public static void shiftMultiPlacementCancel(GameTestHelper h) { canceledPlacement(h, true); }

    @GameTest(template = "empty")
    public static void shiftBothDropsRejectedRefunds(GameTestHelper h) {
        var p = player(h); placementWall(p); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CACTUS, 2));
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        float cp = data.getCurrentCp(), ol = data.getCurrentOverload(); int[] rejects = {0};
        var resources = data.captureDynamicResources();
        Consumer<EntityJoinLevelEvent> deny = e -> { if (e.getEntity() instanceof ItemEntity i && i.getItem().is(Items.CACTUS)) { rejects[0]++; e.setCanceled(true); } };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, deny);
        try { check(h, !new ShiftTpEffect().tryRelease(p, data, 1), "Double rejection reported successful cast"); }
        finally { NeoForge.EVENT_BUS.unregister(deny); }
        check(h, rejects[0] == 2 && p.getMainHandItem().getCount() == 2 && drops(p, Items.CACTUS) == 0, "Rejected drops lost an item");
        check(h, Math.abs(data.getCurrentCp() - cp) < .001 && Math.abs(data.getCurrentOverload() - ol) < .001, "Failed cast charged CP/overload");
        check(h, data.captureDynamicResources().equals(resources), "Failed cast changed growth/recovery delays");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftFallbackDropAndCreativeConserveItems(GameTestHelper h) {
        var p = player(h); placementWall(p); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CACTUS, 2));
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        int[] joins = {0}; Consumer<EntityJoinLevelEvent> denyFirst = e -> {
            if (e.getEntity() instanceof ItemEntity i && i.getItem().is(Items.CACTUS) && ++joins[0] == 1) e.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, denyFirst);
        try { check(h, new ShiftTpEffect().tryRelease(p, data, 1), "Fallback drop failed"); }
        finally { NeoForge.EVENT_BUS.unregister(denyFirst); }
        check(h, joins[0] == 2 && p.getMainHandItem().getCount() == 1 && drops(p, Items.CACTUS) == 1, "Fallback drop conservation failed");
        p.setGameMode(GameType.CREATIVE); p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BRICKS, 2));
        check(h, new ShiftTpEffect().tryRelease(p, data, 1) && p.getMainHandItem().getCount() == 2, "Creative placement consumed item");
        h.succeed();
    }

    private static void tunnel(GameTestHelper h, double x, double z) {
        var p = player(h); var level = h.getLevel(); p.setPos(x, 100, z); p.setYRot(-90);
        var data = p.getData(AcademyAttachments.PLAYER_ABILITY); data.setDevMode(true);
        BlockPos base = p.blockPosition();
        for (BlockPos at : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(12, 3, 1))) level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        for (int wall : new int[]{2, 6}) for (int y = 0; y < 3; y++) for (int zz = -1; zz <= 1; zz++)
            level.setBlock(base.offset(wall, y, zz), Blocks.STONE.defaultBlockState(), 3);
        Vec3 from = p.position();
        check(h, new PenetrateTeleportEffect().tryRelease(p, data, 1, 10), "Valid wall gap was rejected");
        check(h, p.getX() > base.getX() + 3 && p.getX() < base.getX() + 6
                && level.noCollision(p, p.getBoundingBox()), "Scanner returned second wall at " + p.position() + " from " + from);
    }
    @GameTest(template = "empty") public static void penetratePositiveGap(GameTestHelper h) { tunnel(h, 200.5, 200.5); h.succeed(); }
    @GameTest(template = "empty") public static void penetrateNegativeGap(GameTestHelper h) { tunnel(h, -200.5, -200.5); h.succeed(); }

    @GameTest(template = "empty")
    public static void penetrateDoesNotOvershootRequestedDistance(GameTestHelper h) {
        var p = player(h); var data = p.getData(AcademyAttachments.PLAYER_ABILITY); data.setDevMode(true);
        Vec3 from = p.position();
        check(h, new PenetrateTeleportEffect().tryRelease(p, data, 1, 1), "Clear one-block request failed");
        check(h, from.distanceTo(p.position()) <= 1.00001 && from.distanceTo(p.position()) > 0, "Returned an untested step beyond range");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void markRechecksBlockedDestinationWithoutPayment(GameTestHelper h) {
        var p = player(h); var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        var effect = new MarkTeleportEffect(); effect.onChargingStart(p, data); effect.onChargingTick(p, data, 3);
        Vec3 dest = FlashingTargeting.destination(p, p.getLookAngle(), 8);
        check(h, dest != null, "Marker fixture is not clear");
        h.getLevel().setBlock(BlockPos.containing(dest).above(), Blocks.STONE.defaultBlockState(), 3);
        Vec3 from = p.position(); float cp = data.getCurrentCp(), ol = data.getCurrentOverload();
        check(h, !effect.tryRelease(p, data, 3), "Stale blocked marker accepted");
        check(h, p.position().equals(from) && data.getCurrentCp() == cp && data.getCurrentOverload() == ol, "Failed mark changed position/resources");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void flashingChecksWholeBodyAndStillAllowsAir(GameTestHelper h) {
        var p = player(h); Vec3 air = FlashingTargeting.destination(p, p.getLookAngle(), 4);
        check(h, air != null, "Aerial destination should remain valid");
        h.getLevel().setBlock(BlockPos.containing(air).above(), Blocks.STONE.defaultBlockState(), 3);
        check(h, FlashingTargeting.destination(p, p.getLookAngle(), 4) == null, "Flashing accepted head obstruction");
        check(h, !TeleportDestinations.isSafe(p, h.getLevel(), new Vec3(air.x, h.getLevel().getMaxBuildHeight() - .5, air.z)), "Accepted AABB above build height");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void railgunExactEnergyAndAdjacentValuesStop(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); Vec3 origin = p.getEyePosition();
        for (int i = 1; i <= 18; i++) level.setBlock(BlockPos.containing(origin.add(0, 0, i)), Blocks.OBSIDIAN.defaultBlockState(), 3);
        level.setBlock(BlockPos.containing(origin.add(0, 0, 19)), Blocks.BEDROCK.defaultBlockState(), 3);
        for (double energy : new double[]{899, 900, 901}) {
            double stop = RailgunEffect.traceBarrier(level, p, origin, new Vec3(0, 0, 1), 50, energy);
            check(h, stop > 0 && stop < 20, "Energy=" + energy + " jumped to full range: " + stop);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void railgunHonorsBreakCancellationAndReplacement(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); Vec3 origin = p.getEyePosition(); BlockPos wall = BlockPos.containing(origin.add(0, 0, 2));
        level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        Consumer<BlockEvent.BreakEvent> deny = e -> { if (e.getPos().equals(wall)) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, deny);
        try { check(h, RailgunEffect.traceBarrier(level, p, origin, new Vec3(0, 0, 1), 50, 900) < 3, "Canceled barrier ignored"); }
        finally { NeoForge.EVENT_BUS.unregister(deny); }
        Consumer<BlockEvent.BreakEvent> replace = e -> { if (e.getPos().equals(wall)) level.setBlock(wall, Blocks.BEDROCK.defaultBlockState(), 3); };
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, replace);
        try {
            invoke(new RailgunEffect(), "destroyLine", new Class<?>[]{ServerLevel.class, ServerPlayer.class, Vec3.class, Vec3.class, double.class, double.class}, level, p, origin, new Vec3(0, 0, 1), 10D, 900D);
        } finally { NeoForge.EVENT_BUS.unregister(replace); }
        check(h, level.getBlockState(wall).is(Blocks.BEDROCK), "Break callback replacement was destroyed"); h.succeed();
    }

    @GameTest(template = "empty")
    public static void reflectedRailgunCannotDamageThroughWall(GameTestHelper h) {
        var p = player(h); var level = h.getLevel();
        var target = EntityType.COW.create(level); target.setPos(p.position().add(0, 0, 5)); target.setNoAi(true); level.addFreshEntity(target);
        for (int y = 0; y < 3; y++) level.setBlock(p.blockPosition().offset(0, y, 2), Blocks.BEDROCK.defaultBlockState(), 3);
        float health = target.getHealth();
        invoke(new RailgunEffect(), "reflectDamage", new Class<?>[]{ServerPlayer.class}, p);
        check(h, target.getHealth() == health, "Reflected ray damaged an entity behind bedrock");
        for (int y = 0; y < 3; y++) level.setBlock(p.blockPosition().offset(0, y, 2), Blocks.AIR.defaultBlockState(), 3);
        invoke(new RailgunEffect(), "reflectDamage", new Class<?>[]{ServerPlayer.class}, p);
        check(h, target.getHealth() < health, "Clear reflected ray did not damage target"); h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void thunderClapHasNoVanillaServerLightningEffects(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        BlockPos rod = p.blockPosition().offset(0, 1, 4), copper = rod.below();
        level.setBlock(copper, Blocks.OXIDIZED_COPPER.defaultBlockState(), 3);
        level.setBlock(rod, Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, Direction.UP), 3);
        var creeper = EntityType.CREEPER.create(level); creeper.setPos(Vec3.atBottomCenterOf(rod.east())); creeper.setInvulnerable(true); creeper.setNoAi(true); level.addFreshEntity(creeper);
        int[] bolts = {0}; Consumer<EntityJoinLevelEvent> count = e -> { if (e.getEntity() instanceof LightningBolt) bolts[0]++; };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, count);
        try { new ThunderClapEffect().onChargingRelease(p, data, 40); }
        finally { NeoForge.EVENT_BUS.unregister(count); }
        check(h, bolts[0] == 0, "ThunderClap spawned a server lightning entity");
        h.runAfterDelay(20, () -> {
            check(h, level.getBlockState(copper).is(Blocks.OXIDIZED_COPPER) && !level.getBlockState(rod).getValue(LightningRodBlock.POWERED), "Visual lightning modified copper/rod");
            check(h, !creeper.isPowered() && !creeper.isOnFire(), "Visual lightning transformed/ignited creeper"); h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void handheldBlockedTargetKeepsEnergyAndPearl(GameTestHelper h) {
        var p = player(h); var level = h.getLevel(); var item = (HandheldTeleporterItem) AcademyItems.TELEPORTER_DEVICE.get();
        var stack = new ItemStack(item); item.setEnergy(stack, 10000); p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        p.getInventory().setItem(1, new ItemStack(Items.ENDER_PEARL, 2));
        Vec3 from = p.position(), dest = from.add(0, 0, 5);
        ExtraItemData.setTeleport(stack, level.dimension().location(), dest.x, dest.y, dest.z);
        BlockPos head = BlockPos.containing(dest).above(); level.setBlock(head, Blocks.STONE.defaultBlockState(), 3);
        item.use(level, p, InteractionHand.MAIN_HAND);
        check(h, p.position().equals(from) && item.getEnergyStored(stack) == 10000 && p.getInventory().getItem(1).getCount() == 2, "Blocked target moved/charged player");
        level.setBlock(head, Blocks.AIR.defaultBlockState(), 3);
        item.use(level, p, InteractionHand.MAIN_HAND);
        check(h, p.position().distanceToSqr(dest) < 1E-8 && item.getEnergyStored(stack) == 5000 && p.getInventory().getItem(1).getCount() == 1, "Valid handheld return failed conservation"); h.succeed();
    }

    private static Object invoke(Object target, String name, Class<?>[] parameters, Object... args) {
        try { Method method = target.getClass().getDeclaredMethod(name, parameters); method.setAccessible(true); return method.invoke(target, args); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template = "empty")
    public static void groundShockFootprintIsInvariantUnderIntegerTranslation(GameTestHelper h) {
        var level = h.getLevel(); var p = player(h); var data = p.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.VECMANIP); data.setDevMode(true);
        java.util.Set<BlockPos> reference = null;
        for (double coordinate : new double[]{1000.4, -999.6}) {
            p.setPos(coordinate, 100, coordinate); p.setOnGround(true); BlockPos base = p.blockPosition().below();
            for (BlockPos at : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 3, 27)))
                level.setBlock(at, at.getY() == base.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            level.random.setSeed(777);
            new com.mohistmc.academy.skill.ability.vecmanip.GroundShockEffect().onChargingRelease(p, data, 30);
            java.util.Set<BlockPos> footprint = new java.util.HashSet<>();
            for (BlockPos at : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 0, 27)))
                if (!level.getBlockState(at).is(Blocks.STONE)) footprint.add(at.subtract(base));
            check(h, !footprint.isEmpty(), "GroundShock did not affect the fixture");
            if (reference == null) reference = footprint;
            else check(h, footprint.equals(reference), "Negative coordinates changed the GroundShock footprint");
        }
        h.succeed();
    }
}
