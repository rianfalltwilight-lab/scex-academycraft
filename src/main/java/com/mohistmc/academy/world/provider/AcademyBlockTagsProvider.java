package com.mohistmc.academy.world.provider;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyBlocks;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

public class AcademyBlockTagsProvider extends BlockTagsProvider {
    public AcademyBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, AcademyCraft.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider p_256380_) {

        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(AcademyBlocks.BLOCKS.getEntries().stream().map(Supplier::get).toList().toArray(new Block[0]));

        // 1.0.7 harvest level 1: stone or better.
        this.tag(BlockTags.INCORRECT_FOR_WOODEN_TOOL).add(
                AcademyBlocks.CONSTRAIN_METAL.get(),
                AcademyBlocks.IMAG_FUSOR.get(),
                AcademyBlocks.MACHINE_FRAME.get(),
                AcademyBlocks.METAL_FORMER.get(),
                AcademyBlocks.NODE_BASIC.get(),
                AcademyBlocks.NODE_STANDARD.get(),
                AcademyBlocks.NODE_ADVANCED.get(),
                AcademyBlocks.PHASE_GEN.get(),
                AcademyBlocks.SOLAR_GEN.get(),
                // 1.0.7 harvest level 2: iron or better.
                AcademyBlocks.CRYSTAL_ORE.get(),
                AcademyBlocks.RESO_ORE.get(),
                AcademyBlocks.IMAGSIL_ORE.get(),
                AcademyBlocks.DEV_NORMAL.get(),
                AcademyBlocks.DEV_ADVANCED.get(),
                AcademyBlocks.DEV_NORMAL_SUB.get(),
                AcademyBlocks.DEV_ADVANCED_SUB.get(),
                AcademyBlocks.WIND_GEN_BASE_SUB.get(),
                AcademyBlocks.WINDGEN_BASE.get(),
                AcademyBlocks.WINDGEN_MAIN.get(),
                AcademyBlocks.WINDGEN_PILLAR.get(),
                AcademyBlocks.WINDGEN_FAN.get());

        this.tag(BlockTags.INCORRECT_FOR_STONE_TOOL).add(
                AcademyBlocks.CRYSTAL_ORE.get(),
                AcademyBlocks.RESO_ORE.get(),
                AcademyBlocks.IMAGSIL_ORE.get(),
                AcademyBlocks.DEV_NORMAL.get(),
                AcademyBlocks.DEV_ADVANCED.get(),
                AcademyBlocks.DEV_NORMAL_SUB.get(),
                AcademyBlocks.DEV_ADVANCED_SUB.get(),
                AcademyBlocks.WIND_GEN_BASE_SUB.get(),
                AcademyBlocks.WINDGEN_BASE.get(),
                AcademyBlocks.WINDGEN_MAIN.get(),
                AcademyBlocks.WINDGEN_PILLAR.get(),
                AcademyBlocks.WINDGEN_FAN.get());
    }
}
