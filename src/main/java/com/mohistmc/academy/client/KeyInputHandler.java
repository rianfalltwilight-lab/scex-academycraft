package com.mohistmc.academy.client;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.gui.DataTerminalGui;
import com.mohistmc.academy.client.gui.SkillSlotGui;
import com.mohistmc.academy.client.gui.LocationTeleportGui;
import com.mohistmc.academy.client.sound.ClientSoundUtils;
import com.mohistmc.academy.network.SkillKeyDownPacket;
import com.mohistmc.academy.network.SkillKeyUpPacket;
import com.mohistmc.academy.network.ChargingAckPacket;
import com.mohistmc.academy.network.ChargingHandshake;
import com.mohistmc.academy.network.ToggleAbilityPacket;
import com.mohistmc.academy.network.UseSkillPacket;
import com.mohistmc.academy.network.FlashingActionPacket;
import com.mohistmc.academy.network.ChargingCancelPacket;
import com.mohistmc.academy.network.SwitchPresetPacket;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.ability.teleporter.FlashingTargeting;
import com.mohistmc.academy.world.AcademySounds;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import com.mohistmc.academy.world.AcademyParticles;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public class KeyInputHandler {

    public static final KeyMapping OPEN_SKILL_SLOT = new KeyMapping(
            "key.academy.skill_slot",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.academy"
    );

    public static final KeyMapping TOGGLE_ABILITY = new KeyMapping(
            "key.academy.toggle_ability",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.academy"
    );

    public static final KeyMapping SKILL_1 = new KeyMapping(
            "key.academy.skill_1",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            "key.categories.academy"
    );

    public static final KeyMapping SKILL_2 = new KeyMapping(
            "key.academy.skill_2",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            "key.categories.academy"
    );

    public static final KeyMapping SKILL_3 = new KeyMapping(
            "key.academy.skill_3",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.academy"
    );

    public static final KeyMapping SKILL_4 = new KeyMapping(
            "key.academy.skill_4",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.categories.academy"
    );

    public static final KeyMapping SWITCH_PRESET = new KeyMapping(
            "key.academy.switch_preset",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.academy"
    );

    public static final KeyMapping OPEN_TERMINAL = new KeyMapping(
            "key.academy.open_terminal",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.academy"
    );

    private static final KeyMapping[] SKILL_KEYS = { SKILL_1, SKILL_2, SKILL_3, SKILL_4 };

    // 记录哪些技能槽位正在蓄力
    private static final boolean[] CHARGING_SLOTS = new boolean[SKILL_KEYS.length];
    private static final long[] CHARGING_EPOCHS = new long[SKILL_KEYS.length];
    private static final String[] CHARGING_SKILLS = new String[SKILL_KEYS.length];
    private static final boolean[] RELEASE_PENDING = new boolean[SKILL_KEYS.length];
    private static final int[] CHARGING_REQUEST_TICKS = new int[SKILL_KEYS.length];
    private static final long[] CHARGING_GENERATIONS = new long[SKILL_KEYS.length];
    private static final boolean[] CHARGING_TOMBSTONES = new boolean[SKILL_KEYS.length];
    private static final boolean[] CANCEL_PENDING = new boolean[SKILL_KEYS.length];
    private static long nextChargingGeneration;
    private static boolean screenWasOpen;
    private static boolean toggleWasDown;
    private static long togglePressedAtMillis = -1;
    private static boolean formerDefaultMigrationChecked;

    // 记录上一帧的 isDown() 状态，用于边缘检测（按下/抬起）
    private static final boolean[] WAS_DOWN = new boolean[SKILL_KEYS.length];
    private static boolean flashingActive = false;
    private static int flashingHeldDirection = -1;
    private static float penetrateDistance = Float.NaN;
    public static void setFlashingActive(boolean active) {
        flashingActive = active;
        if (!active) flashingHeldDirection = -1;
    }
    public static boolean isFlashingActive() { return flashingActive; }
    /** Presentation-only direction: 0=A, 1=D, 2=W, 3=S; never authorizes teleport. */
    public static int getFlashingHeldDirection() { return flashingHeldDirection; }

    /** Local-only teardown. Never emits packets because the connection may already be gone. */
    public static void resetClientSession() {
        for (int i = 0; i < SKILL_KEYS.length; i++) {
            CHARGING_SLOTS[i] = false;
            CHARGING_EPOCHS[i] = 0;
            CHARGING_SKILLS[i] = null;
            RELEASE_PENDING[i] = false;
            CHARGING_REQUEST_TICKS[i] = 0;
            CHARGING_GENERATIONS[i] = 0;
            CHARGING_TOMBSTONES[i] = false;
            CANCEL_PENDING[i] = false;
            WAS_DOWN[i] = false;
        }
        nextChargingGeneration = 0;
        screenWasOpen = false;
        toggleWasDown = false;
        togglePressedAtMillis = -1;
        com.mohistmc.academy.client.gui.CPBarOverlay.setShowNumbers(false);
        flashingActive = false;
        flashingHeldDirection = -1;
        penetrateDistance = Float.NaN;
        LegacyVecmanipClientController.reset();
    }

    /** Client presentation state only; the server remains authoritative. */
    public static boolean isSkillHeld(int slot) {
        return slot >= 0 && slot < CHARGING_SLOTS.length && CHARGING_SLOTS[slot];
    }

    public static KeyMapping[] getSkillKeys() {
        return SKILL_KEYS;
    }

    /** Whether a mapped ability slot currently owns a vanilla mouse action. */
    public static boolean overridesMouseButton(int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return false;
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.isAbilityActive()) return false;
        for (int i = 0; i < SKILL_KEYS.length; i++) {
            if (SKILL_KEYS[i].matchesMouse(button)
                    && data.getCurrentPreset().getSlot(i) != null) return true;
        }
        return false;
    }

    /**
     * 由 SyncChargingStatePacket 调用，用于同步服务端自动释放后的客户端状态
     */
    public static void resetChargingSlot(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < CHARGING_SLOTS.length) {
            if ("penetrate_teleport".equals(CHARGING_SKILLS[slotIndex])) penetrateDistance = Float.NaN;
            CHARGING_SLOTS[slotIndex] = false;
            CHARGING_EPOCHS[slotIndex] = 0;
            CHARGING_SKILLS[slotIndex] = null;
            RELEASE_PENDING[slotIndex] = false;
            CHARGING_REQUEST_TICKS[slotIndex] = 0;
            CHARGING_TOMBSTONES[slotIndex] = false;
            CANCEL_PENDING[slotIndex] = false;
        }
    }

    public static void finishCharging(long epoch) {
        if (epoch == 0) return;
        for (int i=0;i<CHARGING_EPOCHS.length;i++) if (CHARGING_EPOCHS[i] == epoch) resetChargingSlot(i);
    }

    /** Accepts only the server-created session identity; delayed acks can safely flush an early key release. */
    public static boolean acceptChargingState(int slot, String skillId, long epoch, long generation, boolean accepted, int ticks) {
        if (slot < 0 || slot >= CHARGING_SLOTS.length) return false;
        ChargingHandshake.AckAction action=ChargingHandshake.ack(CHARGING_SLOTS[slot],CHARGING_TOMBSTONES[slot],
                CHARGING_GENERATIONS[slot],CHARGING_SKILLS[slot],generation,skillId,accepted,epoch);
        if(action==ChargingHandshake.AckAction.IGNORE)return false;
        if(action==ChargingHandshake.AckAction.CLEAR){resetChargingSlot(slot);return true;}
        if (CHARGING_EPOCHS[slot] != 0 && CHARGING_EPOCHS[slot] != epoch) return false;
        if (ticks < 0) { resetChargingSlot(slot); return true; }
        CHARGING_EPOCHS[slot]=epoch;
        CHARGING_REQUEST_TICKS[slot]=0;
        PacketDistributor.sendToServer(new ChargingAckPacket(epoch,generation));
        if (CANCEL_PENDING[slot] || action==ChargingHandshake.AckAction.ACK_AND_CANCEL) {
            PacketDistributor.sendToServer(new ChargingCancelPacket(slot, skillId, epoch));
            CANCEL_PENDING[slot]=false;
            RELEASE_PENDING[slot]=false;
        } else if (RELEASE_PENDING[slot]) {
            PacketDistributor.sendToServer(releasePacket(slot, skillId, epoch));
            // Keep the tombstone until the server's terminal state packet arrives.
            RELEASE_PENDING[slot]=false;
        }
        return true;
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SKILL_SLOT);
        event.register(TOGGLE_ABILITY);
        event.register(SKILL_1);
        event.register(SKILL_2);
        event.register(SKILL_3);
        event.register(SKILL_4);
        event.register(SWITCH_PRESET);
        event.register(OPEN_TERMINAL);
    }

    /**
     * 处理非技能按键（终端、技能槽、切换能力、切换预设）。
     * 技能按键的充能逻辑已完全移至 onClientTick 进行边缘检测，
     * 避免 consumeClick() 在长按期间因 GLFW_REPEAT 累积导致状态不同步。
     */
    @SubscribeEvent
    public static void onKeyInput(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Legacy Flashing semantics: a direction press creates a local destination marker;
        // releasing that same key asks the authoritative server to perform. A lost release
        // is also bounded by the server-side held-input timeout.  The final 1.12.2
        // implementation deliberately follows the player's Minecraft movement
        // bindings instead of assuming a US-layout WASD keyboard.
        if (flashingActive && mc.screen == null) {
            int direction = flashingDirection(mc, event.getKey(), event.getScanCode());
            if (direction >= 0) {
                if (event.getAction() == GLFW.GLFW_PRESS && flashingHeldDirection == -1) {
                    flashingHeldDirection = direction;
                    PacketDistributor.sendToServer(new FlashingActionPacket(FlashingActionPacket.HOLD_BASE + direction));
                } else if (event.getAction() == GLFW.GLFW_RELEASE && flashingHeldDirection == direction) {
                    PacketDistributor.sendToServer(new FlashingActionPacket(FlashingActionPacket.RELEASE_BASE + direction));
                    flashingHeldDirection = -1;
                }
                return;
            }
        }

        if (event.getAction() != GLFW.GLFW_PRESS) return;

        if (OPEN_TERMINAL.consumeClick()) {
            if (com.mohistmc.academy.client.gui.FreqTransmitterGui.isTargetingWorldBlock()) {
                com.mohistmc.academy.client.gui.FreqTransmitterGui.cancelActiveSession();
                return;
            }
            if (mc.screen == null) {
                PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
                if (data.isTerminalInstalled()) {
                    mc.setScreen(new DataTerminalGui());
                } else {
                    mc.player.displayClientMessage(Component.literal("§7[数据终端] §c尚未安装数据终端，请使用数据终端安装。"), true);
                }
            }
            return;
        }

        if (OPEN_SKILL_SLOT.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new SkillSlotGui());
            }
            return;
        }

        if (mc.screen != null) return;

        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (!data.hasAbility()) return;

        if (!data.isAbilityActive()) {
            if (SWITCH_PRESET.consumeClick()) {
                mc.player.displayClientMessage(Component.literal("§c能力未激活"), true);
                return;
            }
            return;
        }

        if (SWITCH_PRESET.consumeClick()) {
            int next = (data.getCurrentPresetIndex() + 1) % PlayerAbilityData.PRESET_COUNT;
            PacketDistributor.sendToServer(new SwitchPresetPacket(next));
            mc.player.displayClientMessage(Component.literal("§7请求切换预设: " + (next + 1)), true);
            ClientSoundUtils.playClient(AcademySounds.ABILITY_PRESET_SWITCH, SoundSource.MASTER, 0.5f, 1.0f);
        }
    }

    /** Final 1.12.2 Penetrate Teleport owns the wheel while its marker is active. */
    @SubscribeEvent
    public static void onMouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || event.getScrollDeltaY() == 0) return;
        if (chargingSlot("penetrate_teleport") < 0) return;
        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        float maximum = penetrateMaximum(data);
        float current = Float.isFinite(penetrateDistance) ? penetrateDistance : maximum;
        float next = current + (float) Math.signum(event.getScrollDeltaY());
        // The legacy UI changes only if the complete wheel step remains in range.
        if (next >= .5f && next <= maximum) penetrateDistance = next;
        event.setCanceled(true);
    }

    /** Final 1.12.2 Flashing direction order: left, right, forward, back. */
    private static int flashingDirection(Minecraft mc, int keyCode, int scanCode) {
        if (mc.options.keyLeft.matches(keyCode, scanCode)) return 0;
        if (mc.options.keyRight.matches(keyCode, scanCode)) return 1;
        if (mc.options.keyUp.matches(keyCode, scanCode)) return 2;
        if (mc.options.keyDown.matches(keyCode, scanCode)) return 3;
        return -1;
    }

    /**
     * 客户端 tick：通过 isDown() 边缘检测统一处理所有技能按键的按下/抬起。
     * 使用边缘检测替代 consumeClick()，彻底避免 GLFW_REPEAT 累积导致的
     * "技能释放后仍在蓄力" 问题。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        migrateFormerDefaults(mc);
        boolean screenOpen = mc.screen != null;
        if (screenOpen) {
            if (!screenWasOpen) cancelChargingForScreen();
            screenWasOpen = true;
            if (flashingActive && flashingHeldDirection >= 0) {
                PacketDistributor.sendToServer(new FlashingActionPacket(FlashingActionPacket.CANCEL_BASE + flashingHeldDirection));
                flashingHeldDirection = -1;
            }
            for (int i=0;i<SKILL_KEYS.length;i++) WAS_DOWN[i]=SKILL_KEYS[i].isDown();
            toggleWasDown = TOGGLE_ABILITY.isDown();
            togglePressedAtMillis = -1;
            com.mohistmc.academy.client.gui.CPBarOverlay.setShowNumbers(false);
            return;
        }
        screenWasOpen = false;

        PlayerAbilityData data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        handleAbilityToggle(mc, data);

        if (flashingActive && flashingHeldDirection >= 0) {
            // Heartbeats distinguish a legitimately long hold from a lost key-up packet.
            if (mc.player.tickCount % 10 == 0)
                PacketDistributor.sendToServer(new FlashingActionPacket(FlashingActionPacket.HOLD_BASE + flashingHeldDirection));
            float exp = data.getProficiency("flashing");
            Vec3 direction = FlashingTargeting.direction(mc.player, flashingHeldDirection);
            Vec3 marker = FlashingTargeting.destination(mc.player, direction, 12 + 6 * exp);
            if (marker != null && mc.level.random.nextDouble() < .4) {
                mc.level.addParticle(AcademyParticles.TELEPORT.get(),
                        marker.x + mc.level.random.nextDouble() * 2 - 1,
                        marker.y + .2 + mc.level.random.nextDouble() * 1.4 - 1.6,
                        marker.z + mc.level.random.nextDouble() * 2 - 1,
                        (mc.level.random.nextDouble() * 2 - 1) * .03,
                        mc.level.random.nextDouble() * .05,
                        (mc.level.random.nextDouble() * 2 - 1) * .03);
            }
        }

        for (int i = 0; i < SKILL_KEYS.length; i++) {
            boolean down = SKILL_KEYS[i].isDown();

            // 上升沿：按键按下
            if (down && !WAS_DOWN[i]) {
                while (SKILL_KEYS[i].consumeClick()) { /* drain KeyMapping click bookkeeping */ }
                Skill skill = data.getSlotSkill(data.getCurrentPresetIndex(), i);
                String skillId = data.getCurrentPreset().getSlot(i);
                // Inactive ability delegates do not own the key in 1.0.7;
                // mouse 1/2 must remain ordinary mining/use without chat spam.
                if (data.isAbilityActive() && skillId != null && skill != null && data.canUseSkill(skill)) {
                    if (skill.getEffect() instanceof ChargingSkillEffect) {
                        if (CHARGING_SLOTS[i] && isToggleContextSkill(CHARGING_SKILLS[i])) {
                            // These 1.0.7 delegates are toggles. Releasing the
                            // first press does nothing; pressing the same delegate
                            // again terminates its charge/active context.
                            if (CHARGING_EPOCHS[i] != 0)
                                PacketDistributor.sendToServer(releasePacket(i,
                                        CHARGING_SKILLS[i], CHARGING_EPOCHS[i]));
                            else RELEASE_PENDING[i] = true;
                        } else if (!CHARGING_SLOTS[i]) {
                            CHARGING_SLOTS[i] = true;
                            CHARGING_SKILLS[i] = skillId;
                            CHARGING_EPOCHS[i] = 0;
                            RELEASE_PENDING[i] = false;
                            CHARGING_REQUEST_TICKS[i] = 0;
                            CHARGING_TOMBSTONES[i] = false;
                            if ("penetrate_teleport".equals(skillId)) penetrateDistance = penetrateMaximum(data);
                            long generation = ++nextChargingGeneration;
                            if (generation == 0) generation = ++nextChargingGeneration;
                            CHARGING_GENERATIONS[i] = generation;
                            PacketDistributor.sendToServer(new SkillKeyDownPacket(i, generation));
                        }
                    } else {
                        activateOneShot(mc, i, skillId);
                    }
                } else if (data.isAbilityActive() && skillId == null) {
                    mc.player.displayClientMessage(Component.literal("§7槽位 " + (i + 1) + " 未装备技能"), true);
                } else if (data.isAbilityActive()) {
                    mc.player.displayClientMessage(Component.literal("§c技能无法使用"), true);
                    ClientSoundUtils.playClient(AcademySounds.ABILITY_DENY,
                            SoundSource.MASTER, 0.4f, 1.0f);
                }
            }

            // 下降沿：按键抬起
            if (!down && WAS_DOWN[i]) {
                if (CHARGING_SLOTS[i] && !isToggleContextSkill(CHARGING_SKILLS[i])) {
                    if (CHARGING_EPOCHS[i] != 0)
                        PacketDistributor.sendToServer(releasePacket(i, CHARGING_SKILLS[i], CHARGING_EPOCHS[i]));
                    else RELEASE_PENDING[i] = true;
                }
            }

            WAS_DOWN[i] = down;

            // A dropped ack/NACK must not latch a slot forever. Two seconds is well above normal
            // packet latency while still giving deterministic recovery after disconnect/race loss.
            if (CHARGING_SLOTS[i] && !CHARGING_TOMBSTONES[i]
                    && ChargingHandshake.shouldTombstone(true,CHARGING_EPOCHS[i],++CHARGING_REQUEST_TICKS[i])) {
                // Do not forget request identity: an accepted ACK may have crossed this timeout.
                // It will be answered immediately with the server epoch, closing the session.
                CHARGING_TOMBSTONES[i] = true;
                RELEASE_PENDING[i] = true;
            }

        }
    }

    private static void activateOneShot(Minecraft mc, int slot, String skillId) {
        if ("location_teleport".equals(skillId)) {
            mc.setScreen(new LocationTeleportGui());
        } else if ("flashing".equals(skillId)) {
            flashingActive = !flashingActive;
            PacketDistributor.sendToServer(new FlashingActionPacket(
                    flashingActive ? FlashingActionPacket.START : FlashingActionPacket.END));
        } else {
            PacketDistributor.sendToServer(new UseSkillPacket(slot));
        }
    }

    private static boolean isToggleContextSkill(String skillId) {
        return "storm_wing".equals(skillId) || "vec_deviation".equals(skillId)
                || "vec_reflection".equals(skillId);
    }

    private static int chargingSlot(String skillId) {
        for (int i = 0; i < CHARGING_SKILLS.length; i++)
            if (CHARGING_SLOTS[i] && skillId.equals(CHARGING_SKILLS[i])) return i;
        return -1;
    }

    private static float penetrateMaximum(PlayerAbilityData data) {
        float exp = data.getProficiency("penetrate_teleport");
        return 10 + 25 * exp;
    }

    /** Presentation-only distance; the packet value is clamped again by the server. */
    public static float getPenetrateDistance(PlayerAbilityData data) {
        float maximum = penetrateMaximum(data);
        float selected = Float.isFinite(penetrateDistance)
                ? Math.clamp(penetrateDistance, .5f, maximum) : maximum;
        if (!data.isDevMode()) selected = Math.min(selected,
                data.getCurrentCp() / Math.max(.0001f, 14 - 5 * data.getProficiency("penetrate_teleport")));
        return Math.max(0, selected);
    }

    private static SkillKeyUpPacket releasePacket(int slot, String skillId, long epoch) {
        float value = "penetrate_teleport".equals(skillId) ? penetrateDistance : Float.NaN;
        return new SkillKeyUpPacket(slot, skillId, epoch, value);
    }

    private static void handleAbilityToggle(Minecraft mc, PlayerAbilityData data) {
        boolean down = TOGGLE_ABILITY.isDown();
        if (down && !toggleWasDown) {
            while (TOGGLE_ABILITY.consumeClick()) { /* drain click bookkeeping */ }
            togglePressedAtMillis = System.currentTimeMillis();
            com.mohistmc.academy.client.gui.CPBarOverlay.setShowNumbers(true);
        } else if (!down && toggleWasDown) {
            long held = togglePressedAtMillis < 0 ? Long.MAX_VALUE
                    : System.currentTimeMillis() - togglePressedAtMillis;
            com.mohistmc.academy.client.gui.CPBarOverlay.setShowNumbers(false);
            togglePressedAtMillis = -1;
            if (held < 300 && data.hasAbility()) {
                PacketDistributor.sendToServer(ToggleAbilityPacket.INSTANCE);
                ClientSoundUtils.playClient(AcademySounds.ABILITY_PRESET_CONFIRM,
                        SoundSource.MASTER, 0.4f, 1.0f);
            }
        }
        toggleWasDown = down;
    }

    /** Migrate only the exact untouched keyboard-only tuple shipped in 0.0.10. */
    private static void migrateFormerDefaults(Minecraft mc) {
        if (formerDefaultMigrationChecked) return;
        formerDefaultMigrationChecked = true;
        boolean untouched = "key.keyboard.r".equals(SKILL_1.saveString())
                && "key.keyboard.t".equals(SKILL_2.saveString())
                && "key.keyboard.y".equals(SKILL_3.saveString())
                && "key.keyboard.u".equals(SKILL_4.saveString())
                && "key.keyboard.m".equals(TOGGLE_ABILITY.saveString())
                && "key.keyboard.b".equals(SWITCH_PRESET.saveString());
        if (!untouched) return;
        SKILL_1.setKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_LEFT));
        SKILL_2.setKey(InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
        SKILL_3.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
        SKILL_4.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F));
        TOGGLE_ABILITY.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_V));
        SWITCH_PRESET.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_C));
        KeyMapping.resetMapping();
        mc.options.save();
    }

    private static void cancelChargingForScreen() {
        for (int i=0;i<CHARGING_SLOTS.length;i++) {
            if (!CHARGING_SLOTS[i]) continue;
            CANCEL_PENDING[i]=true;
            RELEASE_PENDING[i]=false;
            CHARGING_TOMBSTONES[i]=true;
            if (CHARGING_EPOCHS[i] != 0) {
                PacketDistributor.sendToServer(new ChargingCancelPacket(i, CHARGING_SKILLS[i], CHARGING_EPOCHS[i]));
            }
        }
    }
}
