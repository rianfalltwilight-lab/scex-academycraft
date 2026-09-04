package com.mohistmc.academy.skill.ability.meltdowner;
import static com.mohistmc.academy.utils.MathUtils.lerpf;
public final class MineRayExpertEffect extends AbstractMineRayEffect {
 public String getId(){return "mine_ray_expert";} protected float range(){return 20;} protected int harvestLevel(){return 5;}
 protected float speed(float e){return lerpf(.5f,1,e);} protected float cpPerTick(float e){return lerpf(25,15,e);}
 protected float startOverload(float e){return lerpf(300,200,e);} public int getCooldownTicks(float e){return (int)lerpf(60,30,e);}
}
