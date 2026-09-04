package com.mohistmc.academy.worldgen;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.stream.Stream;

/** Placement codecs needed to reproduce legacy probabilities that vanilla's integer rarity cannot express. */
public final class AcademyPlacementModifiers {
    private AcademyPlacementModifiers() {}

    public static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, AcademyCraft.MODID);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<Chance30>> CHANCE_30 =
            TYPES.register("chance_30", () -> () -> Chance30.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<OresEnabled>> ORES_ENABLED =
            TYPES.register("ores_enabled", () -> () -> OresEnabled.CODEC);
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<PhaseLiquidEnabled>> PHASE_LIQUID_ENABLED =
            TYPES.register("phase_liquid_enabled", () -> () -> PhaseLiquidEnabled.CODEC);

    public static final class Chance30 extends PlacementModifier {
        public static final Chance30 INSTANCE = new Chance30();
        public static final MapCodec<Chance30> CODEC = MapCodec.unit(INSTANCE);
        private Chance30() {}

        @Override
        public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
            return random.nextDouble() < 0.3D ? Stream.of(pos) : Stream.empty();
        }

        @Override
        public PlacementModifierType<?> type() {
            return CHANCE_30.get();
        }
    }

    /** Runtime placement gate matching 1.0.7's generic.genOres switch. */
    public static final class OresEnabled extends PlacementModifier {
        public static final OresEnabled INSTANCE = new OresEnabled();
        public static final MapCodec<OresEnabled> CODEC = MapCodec.unit(INSTANCE);
        private OresEnabled() {}

        @Override public Stream<BlockPos> getPositions(
                PlacementContext context, RandomSource random, BlockPos pos) {
            return ACConfig.Server.generateOres() ? Stream.of(pos) : Stream.empty();
        }

        @Override public PlacementModifierType<?> type() { return ORES_ENABLED.get(); }
    }

    /** Runtime placement gate matching 1.0.7's generic.genPhaseLiquid switch. */
    public static final class PhaseLiquidEnabled extends PlacementModifier {
        public static final PhaseLiquidEnabled INSTANCE = new PhaseLiquidEnabled();
        public static final MapCodec<PhaseLiquidEnabled> CODEC = MapCodec.unit(INSTANCE);
        private PhaseLiquidEnabled() {}

        @Override public Stream<BlockPos> getPositions(
                PlacementContext context, RandomSource random, BlockPos pos) {
            return ACConfig.Server.generatePhaseLiquid() ? Stream.of(pos) : Stream.empty();
        }

        @Override public PlacementModifierType<?> type() { return PHASE_LIQUID_ENABLED.get(); }
    }
}
