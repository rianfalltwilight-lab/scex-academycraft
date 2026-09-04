package com.mohistmc.academy.skill;

import com.mohistmc.academy.network.LearnSkillPacket;
import com.mohistmc.academy.terminal.AppRegistry;
import com.mohistmc.academy.terminal.TerminalApp;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.LegacyAbilityRules;

public class PlayerAbilityData implements com.mohistmc.academy.skill.ability.meltdowner.LightShieldResourceLedger.ResourceAccount {

    public static final float BASE_MAX_CP = 1800;
    /** Porting baseline: AcademyCraft 1.0.7 default.conf ability.init_cp[5] = 8000. */
    public static final float LEVEL_5_INITIAL_CP = 8000;
    public static final float BASE_MAX_OVERLOAD = 100;
    public static final float BASE_CP_REGEN = 1.0f;
    public static final int PRESET_COUNT = 4;
    public static final float MAX_RESOURCE_CAP = 1_000_000f;
    public static final float MAX_REGEN_RATE = 10_000f;

    private AbilityCategory currentAbility = null;
    private int playerLevel = 0;
    /** 1.0.7 AbilityData.expAddedThisLevel; reset only when the ability level changes. */
    private float levelProgressExp = 0;
    private float currentCp = BASE_MAX_CP;
    private float maxCp = BASE_MAX_CP;
    private float currentOverload = 0;
    private float maxOverload = BASE_MAX_OVERLOAD;
    /** CPData.addMaxCP/addMaxOverload: usage growth kept separate from level bases and course bonuses. */
    private float usageMaxCp = 0;
    private float usageMaxOverload = 0;
    private float cpRegenRate = BASE_CP_REGEN;
    private int cpRecoveryDelay;
    private int overloadRecoveryDelay;

    private final Set<String> learnedSkills = new HashSet<>();
    private final Map<String, Float> skillProficiency = new HashMap<>();

    private final SkillPreset[] presets = new SkillPreset[PRESET_COUNT];
    private int currentPreset = 0;
    private boolean abilityActive = false;

    // 冷却系统: skillId → 剩余冷却ticks (package-private 供 Codec 访问)
    final Map<String, Integer> cooldowns = new HashMap<>();

    private boolean terminalInstalled = false;
    private final Set<String> installedApps = new HashSet<>();
    private final Set<String> loadedMedia = new HashSet<>();
    private int misakaId = -1;
    /** Server-persistent one-shot flag for legacy generic.giveCloudTerminal. */
    private boolean tutorialItemGranted = false;
    private final List<TeleportLocation> teleportLocations = new ArrayList<>();
    public static final int MAX_TELEPORT_LOCATIONS = 32;

    /** 曾经获得过的物品(拾取/合成/烧炼记录),用于教程条件 */
    private final Set<String> obtainedItems = new HashSet<>();
    /** 1.0.7 TutorialData.activatedTuts counterpart; prevents duplicate update notices. */
    private final Set<String> activatedTutorials = new HashSet<>();


    // ==================== 开发者模式 ====================
    private boolean devMode = false;

    public PlayerAbilityData() {
        for (int i = 0; i < PRESET_COUNT; i++) {
            presets[i] = new SkillPreset();
        }
        installedApps.add(AppRegistry.ABOUT.getAppId());
        installedApps.add(AppRegistry.SETTINGS.getAppId());
        installedApps.add(AppRegistry.TUTORIAL.getAppId());
    }

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public record TeleportLocation(String name, String dimension, double x, double y, double z) {}

    public List<TeleportLocation> getTeleportLocations() { return Collections.unmodifiableList(teleportLocations); }
    public boolean addTeleportLocation(TeleportLocation location) {
        TeleportLocation safe = sanitizeLocation(location);
        if (safe == null || teleportLocations.size() >= MAX_TELEPORT_LOCATIONS) return false;
        teleportLocations.add(safe); return true;
    }
    public static TeleportLocation sanitizeLocation(TeleportLocation location) {
        if (location == null || location.name() == null || location.dimension() == null) return null;
        String name = location.name().strip();
        if (name.isEmpty() || name.length() > 32 || ResourceLocation.tryParse(location.dimension()) == null) return null;
        if (!Double.isFinite(location.x()) || !Double.isFinite(location.y()) || !Double.isFinite(location.z())) return null;
        if (Math.abs(location.x()) > 29_999_984 || Math.abs(location.z()) > 29_999_984 || location.y() < -2048 || location.y() > 2048) return null;
        return new TeleportLocation(name, location.dimension(), location.x(), location.y(), location.z());
    }
    public boolean removeTeleportLocation(int index) {
        if (index < 0 || index >= teleportLocations.size()) return false;
        teleportLocations.remove(index); return true;
    }

    public AbilityCategory getCurrentAbility() {
        return currentAbility;
    }

    public void setCurrentAbility(AbilityCategory category) {
        this.currentAbility = category;
    }

    public boolean hasAbility() {
        return currentAbility != null;
    }

    public int getPlayerLevel() {
        return playerLevel;
    }

    public void setPlayerLevel(int level) {
        int bounded = Math.clamp(level, 0, 5);
        if (this.playerLevel != bounded) {
            this.playerLevel = bounded;
            this.levelProgressExp = 0;
            // 1.0.7 CPData.Events.changedLevel explicitly cleared both usage-growth fields.
            this.usageMaxCp = 0;
            this.usageMaxOverload = 0;
            recalculateMaxResources(true);
        }
    }

    public float getLevelProgressExp() {
        return levelProgressExp;
    }

    public void setLevelProgressExp(float value) {
        levelProgressExp = finite(value) ? Math.clamp(value, 0.0f, 1024.0f) : 0.0f;
    }

    public void addLevelProgress(float consumedSkillExp) {
        if (!hasAbility() || !finite(consumedSkillExp) || consumedSkillExp <= 0 || playerLevel >= 5) return;
        setLevelProgressExp(levelProgressExp + consumedSkillExp);
    }

    public float getLevelProgressThreshold() {
        if (!hasAbility() || playerLevel < 1 || playerLevel > 5) return 0.0f;
        int controllable = (int) SkillRegistry.getSkillsByCategory(currentAbility).stream()
                .filter(skill -> skill.getType() == SkillType.ACTIVE && skill.getLevel() == playerLevel)
                .count();
        return LegacyLevelProgress.threshold(playerLevel, controllable);
    }

    public float getLevelProgress() {
        return LegacyLevelProgress.fraction(levelProgressExp, getLevelProgressThreshold());
    }

    public boolean canLevelUp() {
        return LegacyLevelProgress.canLevelUp(hasAbility(), playerLevel,
                levelProgressExp, getLevelProgressThreshold());
    }

    public void maxOutLevelProgress() {
        // CommandAIMBase in 1.0.7 writes the gauge even at Level 5. The
        // separate canLevelUp() gate still prevents advancing beyond 5.
        if (hasAbility()) setLevelProgressExp(getLevelProgressThreshold());
    }

    public float getCurrentCp() {
        return currentCp;
    }

    public void setCurrentCp(float cp) {
        this.currentCp = finite(cp) ? Math.clamp(cp, 0, getMaxCp()) : 0;
    }

    public float getMaxCp() {
        return maxCp;
    }

    public float getUsageMaxCp() { return usageMaxCp; }
    public float getUsageMaxOverload() { return usageMaxOverload; }

    public void setUsageResourceGrowth(float cp, float overload) {
        usageMaxCp = Float.isFinite(cp) ? Math.clamp(cp, 0,
                LegacyResourceProgression.maxAddedCp(playerLevel)) : 0;
        usageMaxOverload = Float.isFinite(overload) ? Math.clamp(overload, 0,
                LegacyResourceProgression.maxAddedOverload(playerLevel)) : 0;
        recalculateMaxResources(false);
    }

    public void recalculateMaxResources(boolean refill) {
        int level = hasAbility() ? playerLevel : 0;
        usageMaxCp = Math.clamp(Float.isFinite(usageMaxCp) ? usageMaxCp : 0,
                0, LegacyResourceProgression.maxAddedCp(level));
        usageMaxOverload = Math.clamp(Float.isFinite(usageMaxOverload) ? usageMaxOverload : 0,
                0, LegacyResourceProgression.maxAddedOverload(level));
        maxCp = LegacyResourceProgression.initialCp(level) + usageMaxCp
                + LegacyResourceProgression.courseCpBonus(learnedSkills.contains("brain_course"),
                learnedSkills.contains("brain_course_advanced"));
        maxOverload = LegacyResourceProgression.initialOverload(level) + usageMaxOverload
                + LegacyResourceProgression.courseOverloadBonus(learnedSkills.contains("brain_course_advanced"));
        cpRegenRate = BASE_CP_REGEN
                * LegacyResourceProgression.recoveryMultiplier(learnedSkills.contains("mind_course"));
        if (refill) {
            currentCp = maxCp;
            currentOverload = 0;
        } else {
            currentCp = finite(currentCp) ? Math.clamp(currentCp, 0, maxCp) : 0;
            currentOverload = finite(currentOverload) ? Math.clamp(currentOverload, 0, maxOverload) : 0;
        }
    }

    public void addMaxCp(float amount) {
        setUsageResourceGrowth(usageMaxCp + amount, usageMaxOverload);
    }

    public float getCurrentOverload() {
        return currentOverload;
    }

    public void setCurrentOverload(float overload) {
        this.currentOverload = finite(overload) ? Math.clamp(overload, 0, getMaxOverload()) : 0;
    }

    public void addOverload(float amount) {
        setCurrentOverload(currentOverload + amount);
    }

    public float getMaxOverload() {
        return maxOverload;
    }

    public void addMaxOverload(float amount) {
        setUsageResourceGrowth(usageMaxCp, usageMaxOverload + amount);
    }

    public float getCpRegenRate() {
        return cpRegenRate;
    }

    int cpRecoveryDelay() { return cpRecoveryDelay; }
    int overloadRecoveryDelay() { return overloadRecoveryDelay; }
    void setRecoveryDelays(int cp, int overload) {
        cpRecoveryDelay = Math.clamp(cp, 0, 1200);
        overloadRecoveryDelay = Math.clamp(overload, 0, 1200);
    }

    public void addCpRegenRate(float multiplier) {
        float candidate = this.cpRegenRate * (1.0f + multiplier);
        this.cpRegenRate = finite(candidate) ? Math.clamp(candidate, 0, MAX_REGEN_RATE) : BASE_CP_REGEN;
    }

    public boolean hasLearnedSkill(String skillId) {
        if (devMode) return true;
        return learnedSkills.contains(skillId);
    }

    public void learnSkill(String skillId) {
        if (!validKey(skillId) || learnedSkills.size() >= 256) return;
        boolean added = learnedSkills.add(skillId);
        if (!skillProficiency.containsKey(skillId) && skillProficiency.size() < 256) {
            skillProficiency.put(skillId, 0.0f);
        }
        if (added) recalculateMaxResources(true);
    }

    /**
     * Removes a learned skill while retaining its proficiency, matching the
     * 1.0.7 debug command semantics. Any preset slots which referenced the
     * skill are cleared so an unlearned action cannot remain bound client-side.
     */
    public boolean unlearnSkill(String skillId) {
        if (!validKey(skillId) || !learnedSkills.remove(skillId)) return false;
        for (SkillPreset preset : presets) {
            for (int slot = 0; slot < SkillPreset.SLOT_COUNT; slot++) {
                if (skillId.equals(preset.getSlot(slot))) preset.clearSlot(slot);
            }
        }
        recalculateMaxResources(true);
        return true;
    }

    public Set<String> getLearnedSkills() {
        return learnedSkills;
    }

    public float getProficiency(String skillId) {
        return skillProficiency.getOrDefault(skillId, 0.0f);
    }

    /** Read-only view used by bounded persistence and command-compatible unlearning. */
    public Map<String, Float> getSkillProficiencies() {
        return Collections.unmodifiableMap(skillProficiency);
    }

    public void addProficiency(String skillId, float amount) {
        if (!validKey(skillId) || !finite(amount)) return;
        if (!skillProficiency.containsKey(skillId) && skillProficiency.size() >= 256) return;
        float current = skillProficiency.getOrDefault(skillId, 0.0f);
        skillProficiency.put(skillId, Math.clamp(finite(current) ? current + amount : amount, 0, 1));
    }

    public void setProficiency(String skillId, float value) {
        if (!validKey(skillId) || (!skillProficiency.containsKey(skillId) && skillProficiency.size() >= 256)) return;
        skillProficiency.put(skillId, finite(value) ? Math.clamp(value, 0.0f, 1.0f) : 0);
    }

    public boolean canLearnSkill(Skill skill) {
        if (devMode) return true;
        if (skill.getCategory() != currentAbility) return false;
        if (learnedSkills.contains(skill.getId())) return false;
        // 1.0.7 DevConditionLevel requires the ability level itself; learning a
        // higher-level skill is not the mechanism which advances that level.
        if (skill.getLevel() > playerLevel) return false;

        for (Skill.Prerequisite prereq : skill.getPrerequisites()) {
            String prereqId = prereq.skillId();
            if (prereqId.startsWith("any_level_")) {
                int requiredLevel = Integer.parseInt(prereqId.substring("any_level_".length()));
                boolean hasAnySkillAtLevel = SkillRegistry.getSkillsByCategory(currentAbility).stream()
                        .filter(s -> s.getLevel() == requiredLevel && !s.getId().equals(skill.getId()))
                        .anyMatch(s -> learnedSkills.contains(s.getId()));
                if (!hasAnySkillAtLevel) return false;
            } else {
                if (!learnedSkills.contains(prereqId)) return false;
                if (getProficiency(prereqId) < prereq.proficiencyRequired()) return false;
            }
        }
        return true;
    }

    // ==================== 冷却系统 ====================

    /** 设置技能冷却（tick） */
    public void setCooldown(String skillId, int ticks) {
        if (validKey(skillId) && ticks > 0) {
            cooldowns.put(skillId, Math.min(ticks, 20 * 60 * 60));
        }
    }

    /** 技能是否在冷却中 */
    public boolean isOnCooldown(String skillId) {
        return cooldowns.getOrDefault(skillId, 0) > 0;
    }

    /** 获取技能剩余冷却 tick */
    public int getCooldownTicks(String skillId) {
        return cooldowns.getOrDefault(skillId, 0);
    }

    /** Clears every ability cooldown and returns the number of cleared entries. */
    public int clearCooldowns() {
        int count = cooldowns.size();
        cooldowns.clear();
        return count;
    }

    /** 获取技能最大冷却 tick（用于 HUD 显示） */
    public int getMaxCooldownTicks(String skillId) {
        // 从 cooldowns 中我们只有剩余值，最大冷却存储在 setCooldown 时
        // 这里返回一个近似值（skill effect 的 cooldown）
        Skill skill = SkillRegistry.getSkill(currentAbility, skillId);
        if (skill != null && skill.getEffect() != null) {
            return skill.getEffect().getCooldownTicks(getProficiency(skillId));
        }
        return 100; // fallback
    }

    public boolean canUseSkill(Skill skill) {
        var tuning = ACConfig.Server.skill(skill.getId());
        if (!tuning.enabled()) return false;
        if (isDevMode()) return true;
        if (!hasLearnedSkill(skill.getId())) return false;
        if (isOnCooldown(skill.getId())) return false;
        SkillEffect effect = skill.getEffect();
        // Dynamic legacy effects own their complete transactional preflight.
        // Registry costs for those entries are presentation estimates only and
        // must not reject a cast before canActivate/canStartCharging evaluates
        // the real proficiency- and context-dependent CP/overload values.
        if (effect != null && !effect.appliesBaseResourceCost()) return true;
        if (currentCp < skill.getBaseCpCost() * tuning.cp()) return false;
        return currentOverload + skill.getBaseOverload() * tuning.overload() <= maxOverload;
    }

    public void useSkill(Skill skill) {
        if (!canUseSkill(skill)) return;
        SkillEffect effect = skill.getEffect();
        if (!isDevMode() && (effect == null || effect.appliesBaseResourceCost())) {
            var tuning = ACConfig.Server.skill(skill.getId());
            float cpCost = skill.getBaseCpCost() * tuning.cp();
            float overloadCost = skill.getBaseOverload() * tuning.overload();
            currentCp -= cpCost;
            addOverload(overloadCost);
            var rules = ACConfig.Server.legacyRules();
            growUsageResources(cpCost, overloadCost, rules);
            cpRecoveryDelay = rules.cpDelay();
            overloadRecoveryDelay = rules.overloadDelay();
        }
        if (effect != null) {
            int cd = effect.getCooldownTicks(getProficiency(skill.getId()));
            setCooldown(skill.getId(), cd);
        }
    }

    /** Commits a server-authoritative dynamic cost and applies the same legacy growth/recovery side effects as base costs. */
    public boolean tryConsumeDynamic(float cpCost, float overloadCost) {
        if (isDevMode()) return true;
        if (!Float.isFinite(cpCost) || !Float.isFinite(overloadCost) || cpCost < 0 || overloadCost < 0) return false;
        if (currentCp < cpCost || currentOverload + overloadCost > maxOverload) return false;
        currentCp -= cpCost;
        addOverload(overloadCost);
        var rules = ACConfig.Server.legacyRules();
        growUsageResources(cpCost, overloadCost, rules);
        cpRecoveryDelay = rules.cpDelay();
        overloadRecoveryDelay = rules.overloadDelay();
        return true;
    }

    /**
     * 1.0.7 {@code consumeWithForce}: debit as much CP as exists and clamp
     * overload at its cap without rejecting the action.  Teleporter skills
     * deliberately used this after their own distance/target validation.
     */
    public boolean consumeDynamicForced(float cpCost, float overloadCost) {
        if (isDevMode()) return true;
        LegacyResourceProgression.ForcedResources result = LegacyResourceProgression.consumeWithForce(
                currentCp, currentOverload, maxOverload, cpCost, overloadCost);
        if (result == null) return false;
        currentCp = result.cp();
        currentOverload = result.overload();
        var rules = ACConfig.Server.legacyRules();
        growUsageResources(cpCost, overloadCost, rules);
        cpRecoveryDelay = rules.cpDelay();
        overloadRecoveryDelay = rules.overloadDelay();
        return true;
    }

    private void growUsageResources(float cpCost, float overloadCost, LegacyAbilityRules.Snapshot rules) {
        usageMaxCp = LegacyResourceProgression.growCp(usageMaxCp, cpCost, rules.maxCpGrowth(), playerLevel);
        usageMaxOverload = LegacyResourceProgression.growOverload(usageMaxOverload, overloadCost,
                rules.maxOverloadGrowth(), playerLevel);
        recalculateMaxResources(false);
    }

    /** Compensation used only when an externally attempted dynamic action reports failure. */
    public void refundDynamic(float cpCost, float overloadCost) {
        if (isDevMode()) return;
        if (!Float.isFinite(cpCost) || !Float.isFinite(overloadCost) || cpCost < 0 || overloadCost < 0) return;
        currentCp = Math.min(maxCp, currentCp + cpCost);
        currentOverload = Math.max(0, currentOverload - overloadCost);
    }

    public void restoreCp(float amount) {
        if (!Float.isFinite(amount) || amount <= 0) return;
        currentCp = Math.min(maxCp, currentCp + amount);
    }

    // ==================== 激活状态 ====================

    public boolean isAbilityActive() {
        return abilityActive;
    }

    public void setAbilityActive(boolean active) {
        this.abilityActive = active;
    }

    public void toggleAbilityActive() {
        this.abilityActive = !this.abilityActive;
    }

    // ==================== 预设系统 ====================

    public SkillPreset getPreset(int index) {
        if (index < 0 || index >= PRESET_COUNT) return presets[0];
        return presets[index];
    }

    public SkillPreset getCurrentPreset() {
        return presets[currentPreset];
    }

    public int getCurrentPresetIndex() {
        return currentPreset;
    }

    public void setCurrentPreset(int index) {
        this.currentPreset = Math.clamp(index, 0, PRESET_COUNT - 1);
    }

    public void setSlot(int presetIndex, int slotIndex, String skillId) {
        presets[presetIndex].setSlot(slotIndex, skillId);
    }

    public void clearSlot(int presetIndex, int slotIndex) {
        presets[presetIndex].clearSlot(slotIndex);
    }

    public String getSlotSkillId(int presetIndex, int slotIndex) {
        return presets[presetIndex].getSlot(slotIndex);
    }

    public Skill getSlotSkill(int presetIndex, int slotIndex) {
        String id = presets[presetIndex].getSlot(slotIndex);
        return id == null ? null : SkillRegistry.getSkill(currentAbility, id);
    }

    // ==================== 数据终端 ====================

    public boolean isTerminalInstalled() {
        return terminalInstalled;
    }

    public void setTerminalInstalled(boolean installed) {
        this.terminalInstalled = installed;
    }

    public int getMisakaId() {
        return misakaId;
    }

    public void setMisakaId(int misakaId) {
        this.misakaId = misakaId;
    }

    public boolean isTutorialItemGranted() {
        return tutorialItemGranted;
    }

    public void setTutorialItemGranted(boolean tutorialItemGranted) {
        this.tutorialItemGranted = tutorialItemGranted;
    }

    // ==================== 获得物品记录(教程条件) ====================

    public void markObtained(String itemId) {
        if (validKey(itemId) && obtainedItems.size() < 4096) obtainedItems.add(itemId);
    }

    public boolean hasObtained(String itemId) {
        return obtainedItems.contains(itemId);
    }

    public Set<String> getObtainedItems() {
        return obtainedItems;
    }

    public boolean activateTutorial(String tutorialId) {
        return validKey(tutorialId) && activatedTutorials.size() < 256 && activatedTutorials.add(tutorialId);
    }

    public boolean hasActivatedTutorial(String tutorialId) {
        return activatedTutorials.contains(tutorialId);
    }

    public Set<String> getActivatedTutorials() {
        return Collections.unmodifiableSet(activatedTutorials);
    }

    public boolean hasApp(String appId) {
        return installedApps.contains(appId);
    }

    public boolean hasApp(TerminalApp app) {
        return installedApps.contains(app.getAppId());
    }

    public void installApp(String appId) {
        if (validKey(appId) && installedApps.size() < 256) installedApps.add(appId);
    }

    public void installApp(TerminalApp app) {
        installedApps.add(app.getAppId());
    }

    public Set<String> getInstalledApps() {
        return installedApps;
    }

    public Set<String> getLoadedMedia() {
        return loadedMedia;
    }

    public boolean hasLoadedMedia(String mediaId) {
        return loadedMedia.contains(mediaId);
    }

    public void addLoadedMedia(String mediaId) {
        if (validKey(mediaId) && loadedMedia.size() < 1024) loadedMedia.add(mediaId);
    }

    public void tick() {
        sanitizeResources();
        var rules = ACConfig.Server.legacyRules();
        if (cpRecoveryDelay > 0) cpRecoveryDelay--; else
            currentCp = Math.min(currentCp + LegacyAbilityRules.cpRecovery(currentCp,maxCp,cpRegenRate,rules), maxCp);
        if (overloadRecoveryDelay > 0) overloadRecoveryDelay--; else
            currentOverload = Math.max(currentOverload - LegacyAbilityRules.overloadRecovery(currentOverload,maxOverload,rules), 0);

        cooldowns.entrySet().removeIf(entry -> {
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) return true;
            entry.setValue(remaining);
            return false;
        });
    }

    public void sanitizeResources() {
        recalculateMaxResources(false);
        setLevelProgressExp(levelProgressExp);
    }
    public void sanitizeForSerialization() {
        sanitizeResources();
        learnedSkills.removeIf(id -> !validKey(id));
        skillProficiency.entrySet().removeIf(e -> !validKey(e.getKey()));
        skillProficiency.replaceAll((id, value) -> finite(value) ? Math.clamp(value, 0, 1) : 0);
        cooldowns.entrySet().removeIf(e -> !validKey(e.getKey()) || e.getValue() == null || e.getValue() <= 0);
        cooldowns.replaceAll((id, ticks) -> Math.min(ticks, 20 * 60 * 60));
        obtainedItems.removeIf(id -> !validKey(id));
        activatedTutorials.removeIf(id -> !validKey(id));
        installedApps.removeIf(id -> !validKey(id));
        loadedMedia.removeIf(id -> !validKey(id));
        teleportLocations.removeIf(location -> sanitizeLocation(location) == null);
    }
    private static boolean finite(float value) { return Float.isFinite(value); }
    private static boolean validKey(String value) { return value != null && !value.isBlank() && value.length() <= 128; }

    public void reset() {
        currentAbility = null;
        playerLevel = 0;
        levelProgressExp = 0;
        currentCp = BASE_MAX_CP;
        maxCp = BASE_MAX_CP;
        currentOverload = 0;
        maxOverload = BASE_MAX_OVERLOAD;
        usageMaxCp = 0;
        usageMaxOverload = 0;
        cpRegenRate = BASE_CP_REGEN;
        learnedSkills.clear();
        skillProficiency.clear();
        abilityActive = false;
        for (SkillPreset preset : presets) {
            preset.clearAll();
        }
        currentPreset = 0;
    }

    public void syncTo(Player player) {
        // Synchronisation must never replace the server-authoritative attachment.
        // Long-lived skill sessions and persistent-only fields retain this identity.
        if (player instanceof ServerPlayer serverPlayer) {
            LearnSkillPacket.syncToClient(serverPlayer, authoritativeView());
        }
    }

    /** The attachment itself is the authority; synchronisation returns no replacement object. */
    PlayerAbilityData authoritativeView() { return this; }

    public CompoundTag toSyncTag() {
        sanitizeForSerialization();
        CompoundTag tag = new CompoundTag();
        if (hasAbility()) {
            tag.putString("ability", currentAbility.id());
        }
        tag.putInt("level", playerLevel);
        tag.putFloat("level_progress_exp", levelProgressExp);
        tag.putFloat("cp", currentCp);
        tag.putFloat("max_cp", maxCp);
        tag.putFloat("overload", currentOverload);
        tag.putFloat("max_overload", maxOverload);
        tag.putFloat("usage_max_cp", usageMaxCp);
        tag.putFloat("usage_max_overload", usageMaxOverload);
        tag.putFloat("cp_regen", cpRegenRate);

        ListTag learnedList = new ListTag();
        for (String skillId : learnedSkills) {
            learnedList.add(StringTag.valueOf(skillId));
        }
        tag.put("learned", learnedList);

        ListTag obtainedList = new ListTag();
        for (String itemId : obtainedItems) {
            obtainedList.add(StringTag.valueOf(itemId));
        }
        tag.put("obtained", obtainedList);

        ListTag activatedTutorialList = new ListTag();
        for (String tutorialId : activatedTutorials) {
            activatedTutorialList.add(StringTag.valueOf(tutorialId));
        }
        tag.put("activated_tutorials", activatedTutorialList);

        CompoundTag profTag = new CompoundTag();
        for (var entry : skillProficiency.entrySet()) {
            profTag.putFloat(entry.getKey(), entry.getValue());
        }
        tag.put("proficiency", profTag);

        tag.putInt("current_preset", currentPreset);
        CompoundTag presetsTag = new CompoundTag();
        for (int p = 0; p < PRESET_COUNT; p++) {
            CompoundTag presetTag = new CompoundTag();
            for (int s = 0; s < SkillPreset.SLOT_COUNT; s++) {
                String skillId = presets[p].getSlot(s);
                if (skillId != null) {
                    presetTag.putString("slot_" + s, skillId);
                }
            }
            presetsTag.put("preset_" + p, presetTag);
        }
        tag.put("presets", presetsTag);

        tag.putBoolean("ability_active", abilityActive);

        tag.putBoolean("terminal_installed", terminalInstalled);
        ListTag appList = new ListTag();
        for (String appId : installedApps) {
            appList.add(StringTag.valueOf(appId));
        }
        tag.put("installed_apps", appList);

        tag.putInt("misaka_id", misakaId);

        ListTag mediaList = new ListTag();
        for (String mediaId : loadedMedia) {
            mediaList.add(StringTag.valueOf(mediaId));
        }
        tag.put("loaded_media", mediaList);

        tag.putBoolean("dev_mode", devMode);

        CompoundTag cdTag = new CompoundTag();
        for (var entry : cooldowns.entrySet()) {
            cdTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("cooldowns", cdTag);

        ListTag locations = new ListTag();
        for (TeleportLocation location : teleportLocations) {
            CompoundTag loc = new CompoundTag();
            loc.putString("name", location.name());
            loc.putString("dimension", location.dimension());
            loc.putDouble("x", location.x());
            loc.putDouble("y", location.y());
            loc.putDouble("z", location.z());
            locations.add(loc);
        }
        tag.put("teleport_locations", locations);

        return tag;
    }

    public static PlayerAbilityData fromSyncTag(CompoundTag tag) {
        PlayerAbilityData data = new PlayerAbilityData();
        if (tag.contains("ability")) {
            AbilityCategory cat = AbilityCategory.fromId(tag.getString("ability"));
            if (cat != null) data.setCurrentAbility(cat);
        }
        data.setPlayerLevel(tag.getInt("level"));
        if (tag.contains("level_progress_exp")) data.setLevelProgressExp(tag.getFloat("level_progress_exp"));

        if (tag.contains("learned")) {
            ListTag list = tag.getList("learned", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.learnSkill(list.getString(i));
            }
        }

        float usageCp = tag.contains("usage_max_cp") ? tag.getFloat("usage_max_cp") : 0;
        float usageOverload = tag.contains("usage_max_overload") ? tag.getFloat("usage_max_overload") : 0;
        data.setUsageResourceGrowth(usageCp, usageOverload);
        data.setCurrentCp(tag.getFloat("cp"));
        data.setCurrentOverload(tag.getFloat("overload"));

        if (tag.contains("obtained")) {
            ListTag list = tag.getList("obtained", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.markObtained(list.getString(i));
            }
        }

        if (tag.contains("activated_tutorials")) {
            ListTag list = tag.getList("activated_tutorials", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) data.activateTutorial(list.getString(i));
        }
        if (tag.contains("proficiency")) {
            CompoundTag profTag = tag.getCompound("proficiency");
            for (String key : profTag.getAllKeys()) {
                data.setProficiency(key, profTag.getFloat(key));
            }
        }

        if (tag.contains("current_preset")) {
            data.setCurrentPreset(tag.getInt("current_preset"));
        }
        if (tag.contains("presets")) {
            CompoundTag presetsTag = tag.getCompound("presets");
            for (int p = 0; p < PRESET_COUNT; p++) {
                String presetKey = "preset_" + p;
                if (presetsTag.contains(presetKey)) {
                    CompoundTag presetTag = presetsTag.getCompound(presetKey);
                    for (int s = 0; s < SkillPreset.SLOT_COUNT; s++) {
                        String slotKey = "slot_" + s;
                        if (presetTag.contains(slotKey)) {
                            data.setSlot(p, s, presetTag.getString(slotKey));
                        }
                    }
                }
            }
        }

        if (tag.contains("ability_active")) {
            data.setAbilityActive(tag.getBoolean("ability_active"));
        }

        if (tag.contains("terminal_installed")) {
            data.setTerminalInstalled(tag.getBoolean("terminal_installed"));
        }

        if (tag.contains("misaka_id")) {
            data.setMisakaId(tag.getInt("misaka_id"));
        }

        if (tag.contains("installed_apps")) {
            ListTag appList = tag.getList("installed_apps", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < appList.size(); i++) {
                data.installApp(appList.getString(i));
            }
        }

        if (tag.contains("loaded_media")) {
            ListTag mediaList = tag.getList("loaded_media", net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < mediaList.size(); i++) {
                data.addLoadedMedia(mediaList.getString(i));
            }
        }

        if (tag.contains("dev_mode")) {
            data.setDevMode(tag.getBoolean("dev_mode"));
        }

        if (tag.contains("cooldowns")) {
            CompoundTag cdTag = tag.getCompound("cooldowns");
            for (String key : cdTag.getAllKeys()) {
                data.setCooldown(key, cdTag.getInt(key));
            }
        }

        if (tag.contains("teleport_locations")) {
            ListTag locations = tag.getList("teleport_locations", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(locations.size(), MAX_TELEPORT_LOCATIONS); i++) {
                CompoundTag loc = locations.getCompound(i);
                data.addTeleportLocation(new TeleportLocation(loc.getString("name"), loc.getString("dimension"),
                        loc.getDouble("x"), loc.getDouble("y"), loc.getDouble("z")));
            }
        }

        data.sanitizeResources();
        return data;
    }
}
