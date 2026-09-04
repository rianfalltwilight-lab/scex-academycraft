package com.mohistmc.academy.skill.ability.meltdowner;
import static com.mohistmc.academy.utils.MathUtils.lerpf;
public final class MineRayLuckEffect extends AbstractMineRayEffect {
 public String getId(){return "mine_ray_luck";} protected float range(){return 20;} protected int harvestLevel(){return 5;} protected int fortuneLevel(){return 3;}
 protected float speed(float e){return lerpf(.5f,1,e);} protected float cpPerTick(float e){return lerpf(50,35,e);}
 protected float startOverload(float e){return lerpf(350,300,e);} public int getCooldownTicks(float e){return (int)lerpf(60,30,e);}
}
