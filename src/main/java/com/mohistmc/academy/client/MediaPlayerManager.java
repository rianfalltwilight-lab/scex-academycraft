package com.mohistmc.academy.client;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.media.ExternalMediaManager;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.terminal.MediaTrack;
import com.mohistmc.academy.terminal.MediaTrackRegistry;
import com.mojang.blaze3d.audio.Channel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-only owner for the legacy data-terminal media channel. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class MediaPlayerManager {

    private static String currentTrack = null;
    private static String lastTrack = null;
    private static MediaSoundInstance currentSoundInstance = null;
    private static boolean paused = false;
    private static int playStartTick = 0;
    private static int pauseStartTick = 0;
    private static int accumulatedPausedTicks = 0;
    private static int currentDurationTicks = 0;

    private MediaPlayerManager() {}

    public static void play(String trackId) {
        stop();

        MediaTrack track = getTrack(trackId);
        if (track == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;

        mc.getMusicManager().stopPlaying();

        if (!track.external()) {
            SoundEvent soundEvent = BuiltInRegistries.SOUND_EVENT.get(track.soundId());
            if (soundEvent == null) return;
        }

        currentSoundInstance = new MediaSoundInstance(track, getVolume());
        mc.getSoundManager().play(currentSoundInstance);

        currentTrack = trackId;
        lastTrack = trackId;
        paused = false;
        playStartTick = mc.player != null ? mc.player.tickCount : 0;
        pauseStartTick = 0;
        accumulatedPausedTicks = 0;
        currentDurationTicks = track.durationSeconds() * 20;
    }

    public static void stop() {
        if (currentSoundInstance != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().stop(currentSoundInstance);
            }
        }
        currentSoundInstance = null;
        currentTrack = null;
        paused = false;
        playStartTick = 0;
        pauseStartTick = 0;
        accumulatedPausedTicks = 0;
        currentDurationTicks = 0;
    }

    public static void pauseCurrent() {
        if (currentSoundInstance == null || paused) return;
        paused = true;
        pauseStartTick = playerTick();
        withCurrentChannel(Channel::pause);
    }

    public static void continueCurrent() {
        if (currentSoundInstance == null || !paused) return;
        accumulatedPausedTicks += Math.max(0, playerTick() - pauseStartTick);
        pauseStartTick = 0;
        paused = false;
        withCurrentChannel(Channel::unpause);
    }

    public static void togglePause() {
        if (paused) continueCurrent();
        else pauseCurrent();
    }

    public static boolean isPaused() {
        return isPlaying() && paused;
    }

    public static String getLastTrack() {
        return lastTrack;
    }

    public static float getVolume() {
        return ACConfig.Client.mediaPlayerVolume();
    }

    public static void setVolume(float value) {
        float clamped = Math.clamp(value, 0.0f, 1.0f);
        ACConfig.Client.MEDIA_PLAYER_VOLUME.set((double) clamped);
        if (currentSoundInstance != null) currentSoundInstance.setMediaVolume(clamped);
    }

    public static String getCurrentTrack() {
        return currentTrack;
    }

    public static MediaTrack getTrack(String trackId) {
        if (trackId == null) return null;
        MediaTrack builtin = MediaTrackRegistry.getTrack(trackId);
        return builtin != null ? builtin : ExternalMediaManager.getTrack(trackId);
    }

    public static boolean isPlaying() {
        if (currentSoundInstance != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() != null && !mc.getSoundManager().isActive(currentSoundInstance)) {
                clearFinishedPlayback();
            }
        }
        return currentSoundInstance != null;
    }

    public static float getProgress() {
        if (!isPlaying() || currentDurationTicks <= 0) return 0f;
        return Math.min(1.0f, (float) elapsedTicks() / currentDurationTicks);
    }

    public static int getElapsedSeconds() {
        if (!isPlaying()) return 0;
        return elapsedTicks() / 20;
    }

    public static boolean isTrackPlaying(String trackId) {
        return isPlaying() && trackId.equals(currentTrack);
    }

    public static String formatTime(int totalSeconds) {
        int min = totalSeconds / 60;
        int sec = totalSeconds % 60;
        return String.format("%d:%02d", min, sec);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (currentSoundInstance == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.getSoundManager().isActive(currentSoundInstance)) {
            clearFinishedPlayback();
            return;
        }
        // 1.0.7 suppressed the vanilla music ticker every five ticks only
        // while its own media channel was actively playing.
        if (!paused && playerTick() % 5 == 0) mc.getMusicManager().stopPlaying();
        // Minecraft globally resumes every channel after an unpause. Reassert
        // the media-only pause so opening/closing a menu cannot restart it.
        if (paused) withCurrentChannel(Channel::pause);
        currentSoundInstance.setMediaVolume(getVolume());
    }

    private static int elapsedTicks() {
        int endpoint = paused ? pauseStartTick : playerTick();
        return Math.max(0, endpoint - playStartTick - accumulatedPausedTicks);
    }

    private static int playerTick() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.tickCount;
    }

    private static void clearFinishedPlayback() {
        currentSoundInstance = null;
        currentTrack = null;
        paused = false;
        playStartTick = pauseStartTick = accumulatedPausedTicks = currentDurationTicks = 0;
    }

    private static void withCurrentChannel(java.util.function.Consumer<Channel> operation) {
        if (currentSoundInstance == null) return;
        Minecraft mc = Minecraft.getInstance();
        var handle = mc.getSoundManager().soundEngine.instanceToChannel.get(currentSoundInstance);
        if (handle != null) handle.execute(operation);
    }

    private static final class MediaSoundInstance extends AbstractTickableSoundInstance {
        private final Path externalSource;
        private final Sound externalSound;
        private final WeighedSoundEvents externalEvent;

        private MediaSoundInstance(MediaTrack track, float volume) {
            super(SoundEvent.createVariableRangeEvent(track.soundId()),
                    SoundSource.MASTER, RandomSource.create());
            this.volume = volume;
            this.pitch = 1.0f;
            this.looping = false;
            this.delay = 0;
            this.attenuation = Attenuation.NONE;
            this.relative = true;
            this.externalSource = track.externalSource();
            if (externalSource == null) {
                this.externalSound = null;
                this.externalEvent = null;
            } else {
                this.externalSound = new Sound(track.soundId(), ConstantFloat.of(1.0f),
                        ConstantFloat.of(1.0f), 1, Sound.Type.FILE, true, false, 16);
                this.externalEvent = new WeighedSoundEvents(track.soundId(), null);
                this.externalEvent.addSound(externalSound);
            }
        }

        private void setMediaVolume(float volume) {
            this.volume = volume;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager soundManager) {
            if (externalSource == null) return super.resolve(soundManager);
            this.sound = externalSound;
            return externalEvent;
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            if (externalSource == null) return super.getStream(buffers, sound, looping);
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return new JOrbisAudioStream(Files.newInputStream(externalSource));
                } catch (IOException ex) {
                    throw new CompletionException(ex);
                }
            }, Util.ioPool());
        }

        @Override public void tick() {
            // Playback lifetime is owned by the streaming sound/channel.
        }
    }
}
