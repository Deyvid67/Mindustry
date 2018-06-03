package io.anuke.mindustry.entities.units;

import io.anuke.mindustry.entities.Unit;
import io.anuke.mindustry.entities.Units;
import io.anuke.mindustry.game.Team;
import io.anuke.mindustry.graphics.Palette;
import io.anuke.mindustry.graphics.Trail;
import io.anuke.mindustry.type.AmmoType;
import io.anuke.mindustry.world.BlockFlag;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.graphics.Draw;
import io.anuke.ucore.util.Angles;
import io.anuke.ucore.util.Geometry;
import io.anuke.ucore.util.Mathf;
import io.anuke.ucore.util.Translator;

import static io.anuke.mindustry.Vars.world;

public class FlyingUnit extends BaseUnit {
    protected static Translator vec = new Translator();
    protected static float maxAim = 30f;
    protected static float wobblyness = 0.6f;

    protected Trail trail = new Trail(16);

    public FlyingUnit(UnitType type, Team team) {
        super(type, team);
    }

    @Override
    public void update() {
        super.update();

        rotation = velocity.angle();
        trail.update(x + Angles.trnsx(rotation + 180f, 6f) + Mathf.range(wobblyness),
                y + Angles.trnsy(rotation + 180f, 6f) + Mathf.range(wobblyness));
    }

    @Override
    public void drawSmooth() {
        Draw.alpha(hitTime / hitDuration);

        Draw.rect(type.name, x, y, rotation - 90);

        Draw.alpha(1f);
    }

    @Override
    public void drawOver() {
        trail.draw(Palette.lighterOrange, Palette.lightishOrange, 5f);
    }

    @Override
    public void behavior() {
        if(health <= health * type.retreatPercent &&
                Geometry.findClosest(x, y, world.indexer().getAllied(team, BlockFlag.repair)) != null){
            setState(retreat);
        }
    }


    @Override
    public UnitState getStartState(){
        return attack;
    }

    protected void circle(float circleLength){
        vec.set(target.getX() - x, target.getY() - y);

        if(vec.len() < circleLength){
            vec.rotate((circleLength-vec.len())/circleLength * 180f);
        }

        vec.setLength(type.speed * Timers.delta());

        velocity.add(vec);
    }

    protected void attack(float circleLength){
        vec.set(target.getX() - x, target.getY() - y);

        float ang = angleTo(target);
        float diff = Angles.angleDist(ang, rotation);

        if(diff > 100f && vec.len() < circleLength){
            vec.setAngle(velocity.angle());
        }else{
            vec.setAngle(Mathf.slerpDelta(velocity.angle(), vec.angle(),  0.44f));
        }

        vec.setLength(type.speed*Timers.delta());

        velocity.add(vec);
    }

    public final UnitState

    resupply = new UnitState(){
        public void entered() {
            target = null;
        }

        public void update() {
            if(inventory.totalAmmo() + 10 >= inventory.ammoCapacity()){
                state.set(attack);
            }else if(!targetHasFlag(BlockFlag.resupplyPoint)){
                retarget(() -> targetClosestAllyFlag(BlockFlag.resupplyPoint));
            }else{
                circle(20f);
            }
        }
    },
    attack = new UnitState(){
        public void entered() {
            target = null;
        }

        public void update() {
            if(Units.invalidateTarget(target, team, x, y)){
                target = null;
            }

            if(!inventory.hasAmmo()) {
                state.set(resupply);
            }else if (target == null){
                if(timer.get(timerTarget, 20)) {
                    Unit closest = Units.getClosestEnemy(team, x, y,
                            inventory.getAmmo().getRange(), other -> distanceTo(other) < 60f);
                    if(closest != null){
                        target = closest;
                    }else {
                        Tile target = Geometry.findClosest(x, y, world.indexer().getEnemy(team, BlockFlag.target));
                        if (target != null) FlyingUnit.this.target = target.entity;
                    }
                }
            }else{
                attack(150f);

                if (timer.get(timerReload, type.reload) && Mathf.angNear(angleTo(target), rotation, 13f)
                        && distanceTo(target) < inventory.getAmmo().getRange()) {
                    AmmoType ammo = inventory.getAmmo();
                    inventory.useAmmo();

                    shoot(ammo, Angles.moveToward(rotation, angleTo(target), maxAim), 4f);
                }
            }
        }
    },
    retreat = new UnitState() {
        public void entered() {
            target = null;
        }

        public void update() {
            if(health >= health){
                state.set(attack);
            }else if(!targetHasFlag(BlockFlag.repair)){
                retarget(() -> {
                    Tile target = Geometry.findClosest(x, y, world.indexer().getAllied(team, BlockFlag.repair));
                    if (target != null) FlyingUnit.this.target = target.entity;
                });
            }else{
                circle(20f);
            }
        }
    };
}