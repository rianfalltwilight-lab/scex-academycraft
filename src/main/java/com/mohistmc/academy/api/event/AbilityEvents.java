package com.mohistmc.academy.api.event;

import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** Public, player-observable ability lifecycle events (the modern counterpart of 1.0.7's ability events). */
public final class AbilityEvents {
    private AbilityEvents() {}
    public abstract static class AbilityEvent extends Event {
        public final Player player;
        protected AbilityEvent(Player player) { this.player = player; }
        public PlayerAbilityData getAbilityData() { return player.getData(com.mohistmc.academy.skill.AcademyAttachments.PLAYER_ABILITY); }
    }
    public static final class Activate extends AbilityEvent { public Activate(Player p){super(p);} }
    public static final class Deactivate extends AbilityEvent { public Deactivate(Player p){super(p);} }
    public static final class CategoryChanged extends AbilityEvent {
        public final AbilityCategory oldCategory, category;
        public CategoryChanged(Player p, AbilityCategory oldCategory, AbilityCategory category){super(p);this.oldCategory=oldCategory;this.category=category;}
    }
    public static final class LevelChanged extends AbilityEvent {
        public final int oldLevel, level;
        public LevelChanged(Player p,int oldLevel,int level){super(p);this.oldLevel=oldLevel;this.level=level;}
    }
    public static final class Overloaded extends AbilityEvent {
        public final float overload, maximum;
        public Overloaded(Player p,float overload,float maximum){super(p);this.overload=overload;this.maximum=maximum;}
    }
    public static final class PresetSwitched extends AbilityEvent {
        public final int oldPreset, preset;
        public PresetSwitched(Player p,int oldPreset,int preset){super(p);this.oldPreset=oldPreset;this.preset=preset;}
    }
    public static final class PresetUpdated extends AbilityEvent {
        public final int preset, slot; public final String oldSkillId, skillId;
        public PresetUpdated(Player p,int preset,int slot,String oldSkillId,String skillId){super(p);this.preset=preset;this.slot=slot;this.oldSkillId=oldSkillId;this.skillId=skillId;}
    }
    public static final class SkillExpAdded extends AbilityEvent {
        public final Skill skill; public final float amount, oldExp, exp;
        public SkillExpAdded(Player p,Skill skill,float amount,float oldExp,float exp){super(p);this.skill=skill;this.amount=amount;this.oldExp=oldExp;this.exp=exp;}
    }
    public static final class SkillExpChanged extends AbilityEvent {
        public final Skill skill; public final float oldExp, exp;
        public SkillExpChanged(Player p,Skill skill,float oldExp,float exp){super(p);this.skill=skill;this.oldExp=oldExp;this.exp=exp;}
    }
    public static final class SkillLearned extends AbilityEvent {
        public final Skill skill;
        public SkillLearned(Player p,Skill skill){super(p);this.skill=skill;}
    }
}
