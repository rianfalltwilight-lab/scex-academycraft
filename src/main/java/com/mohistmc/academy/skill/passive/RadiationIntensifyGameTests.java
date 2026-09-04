package com.mohistmc.academy.skill.passive;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class RadiationIntensifyGameTests {
    private static final String EMPTY = "empty";

    private RadiationIntensifyGameTests() {}

    @GameTest(template = EMPTY)
    public static void radiationAmplifiesLaterDamageButNotCreatingHit(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = caster.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.MELTDOWNER);
        data.setPlayerLevel(5);
        data.setAbilityActive(true);
        data.learnSkill("rad_intensify");

        Pig target = EntityType.PIG.create(helper.getLevel());
        if (target == null) {
            helper.fail("could not create radiation target");
            return;
        }
        target.setPos(caster.getX() + 2, caster.getY(), caster.getZ());
        helper.getLevel().addFreshEntity(target);

        float before = target.getHealth();
        if (!PassiveDamageHelper.meltdownerAttack(caster, data, target, "electron_bomb", 4)) {
            helper.fail("creating Meltdowner hit was rejected");
            return;
        }
        float creatingHit = before - target.getHealth();
        if (Math.abs(creatingHit - 4) > .001f) {
            helper.fail("radiation incorrectly amplified its creating hit to " + creatingHit);
            return;
        }
        if (RadiationIntensifyRuntime.markedTicks(target) != 60
                || Math.abs(RadiationIntensifyRuntime.markedRate(target) - 1.8f) > .001f) {
            helper.fail("mastered radiation did not create the exact 60-tick, 1.8x mark");
            return;
        }

        DamageContainer next = new DamageContainer(helper.getLevel().damageSources().generic(), 10);
        LivingIncomingDamageEvent nextEvent = new LivingIncomingDamageEvent(target, next);
        RadiationIntensifyRuntime.incomingDamage(nextEvent);
        if (Math.abs(nextEvent.getAmount() - 18) > .001f) {
            helper.fail("later arbitrary damage was not amplified by the radiation mark");
            return;
        }

        for (int i = 0; i < 60; i++) {
            RadiationIntensifyRuntime.entityTick(new EntityTickEvent.Post(target));
        }
        DamageContainer expired = new DamageContainer(helper.getLevel().damageSources().generic(), 10);
        LivingIncomingDamageEvent expiredEvent = new LivingIncomingDamageEvent(target, expired);
        RadiationIntensifyRuntime.incomingDamage(expiredEvent);
        if (Math.abs(expiredEvent.getAmount() - 10) > .001f) {
            helper.fail("expired radiation mark continued amplifying damage");
            return;
        }
        helper.succeed();
    }
}
