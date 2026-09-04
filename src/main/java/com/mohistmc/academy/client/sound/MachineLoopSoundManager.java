package com.mohistmc.academy.client.sound;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.block.ImagFusor;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Restores the two position-bound working loops owned by the 1.0.7 machine tiles. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class MachineLoopSoundManager {
    private enum Kind { METAL_FORMER, IMAG_FUSOR }
    private record Key(ClientLevel level, BlockPos pos, Kind kind) {}

    private static final Map<Key, MachineLoop> LOOPS = new HashMap<>();

    private MachineLoopSoundManager() {}

    public static void tickMetalFormer(MetalFomerBlockEntity machine) {
        if (!(machine.getLevel() instanceof ClientLevel level)) return;
        BlockPos pos = machine.getBlockPos().immutable();
        sync(new Key(level, pos, Kind.METAL_FORMER), machine.isClientWorking(),
                AcademySounds.MACHINE_MACHINE_WORK.value(), () ->
                        level.getBlockEntity(pos) instanceof MetalFomerBlockEntity live
                                && live.isClientWorking());
    }

    public static void tickImagFusor(ImagFusorBlockEntity machine) {
        if (!(machine.getLevel() instanceof ClientLevel level)) return;
        BlockPos pos = machine.getBlockPos().immutable();
        boolean working = ImagFusor.isWorkingState(level.getBlockState(pos));
        sync(new Key(level, pos, Kind.IMAG_FUSOR), working,
                AcademySounds.MACHINE_IMAG_FUSOR_WORK.value(), () ->
                        level.getBlockEntity(pos) instanceof ImagFusorBlockEntity
                                && ImagFusor.isWorkingState(level.getBlockState(pos)));
    }

    private static void sync(Key key, boolean wanted, SoundEvent sound, BooleanSupplier alive) {
        Minecraft mc = Minecraft.getInstance();
        MachineLoop current = LOOPS.get(key);
        if (wanted && (current == null || current.isStopped())) {
            current = new MachineLoop(sound, key.pos(), 0.6f, alive);
            LOOPS.put(key, current);
            mc.getSoundManager().play(current);
        } else if (!wanted && current != null) {
            mc.getSoundManager().stop(current);
            LOOPS.remove(key);
        }
    }

    @SubscribeEvent
    public static void cleanup(ClientTickEvent.Post event) {
        ClientLevel currentLevel = Minecraft.getInstance().level;
        Iterator<Map.Entry<Key, MachineLoop>> iterator = LOOPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, MachineLoop> entry = iterator.next();
            if (entry.getKey().level() != currentLevel || entry.getValue().isStopped()) {
                Minecraft.getInstance().getSoundManager().stop(entry.getValue());
                iterator.remove();
            }
        }
    }

    public static void resetClientSession() {
        for (MachineLoop loop : LOOPS.values()) {
            Minecraft.getInstance().getSoundManager().stop(loop);
        }
        LOOPS.clear();
    }

    private static final class MachineLoop extends AbstractTickableSoundInstance {
        private final BooleanSupplier alive;

        private MachineLoop(SoundEvent sound, BlockPos pos, float volume,
                            BooleanSupplier alive) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.alive = alive;
            this.volume = volume;
            this.pitch = 1.0f;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.LINEAR;
            this.x = pos.getX() + 0.5;
            this.y = pos.getY() + 0.5;
            this.z = pos.getZ() + 0.5;
        }

        @Override
        public void tick() {
            if (!alive.getAsBoolean()) stop();
        }
    }
}
