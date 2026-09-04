package com.mohistmc.academy.skill.ability.meltdowner;
import static com.mohistmc.academy.utils.MathUtils.lerpf;
public final class MineRayBasicEffect extends AbstractMineRayEffect {
 public String getId(){return "mine_ray_basic";} protected float range(){return 10;} protected int harvestLevel(){return 2;}
 protected float speed(float e){return lerpf(.2f,.4f,e);} protected float cpPerTick(float e){return lerpf(12,7,e);}
 protected float startOverload(float e){return lerpf(200,150,e);} public int getCooldownTicks(float e){return (int)lerpf(40,20,e);}
}
