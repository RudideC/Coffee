/*
 * Copyright (c) 2022 Coffee Client, 0x150 and contributors.
 * Some rights reserved, refer to LICENSE file.
 */

package coffee.client.feature.module.impl.combat;

import coffee.client.CoffeeMain;
import coffee.client.feature.config.BooleanSetting;
import coffee.client.feature.config.DoubleSetting;
import coffee.client.feature.config.EnumSetting;
import coffee.client.feature.config.RangeSetting;
import coffee.client.feature.module.Module;
import coffee.client.feature.module.ModuleType;
import coffee.client.helper.Rotation;
import coffee.client.helper.manager.AttackManager;
import coffee.client.helper.render.Renderer;
import coffee.client.helper.util.Rotations;
import coffee.client.helper.util.Utils;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class AimAssist extends Module {

    final BooleanSetting attackPlayers = this.config.create(new BooleanSetting.Builder(true).name("Attack players")
        .description("Whether or not to aim at players")
        .get());
    final BooleanSetting attackHostile = this.config.create(new BooleanSetting.Builder(true).name("Attack hostile")
        .description("Whether or not to aim at hostile entities")
        .get());
    final BooleanSetting attackNeutral = this.config.create(new BooleanSetting.Builder(true).name("Attack neutral")
        .description("Whether or not to aim at neutral entities")
        .get());
    final BooleanSetting attackPassive = this.config.create(new BooleanSetting.Builder(true).name("Attack passive")
        .description("Whether or nott o aim at passive entities")
        .get());
    final BooleanSetting attackInvisible = this.config.create(new BooleanSetting.Builder(false).name("Attack invisible")
        .description("Whether or not to aim at everything else")
        .get());
    final BooleanSetting attackEverything = this.config.create(new BooleanSetting.Builder(true).name("Attack everything")
        .description("Whether or not to aim at everything else")
        .get());
    final BooleanSetting aimAtCombatPartner = this.config.create(new BooleanSetting.Builder(false).name("Aim at combat")
        .description("Whether or not to only aim at the combat partner")
        .get());
    final EnumSetting<AimMode> aimMode = this.config.create(new EnumSetting.Builder<>(AimMode.Top).name("Aim Mode")
        .description("Where to aim")
        .get());
    final DoubleSetting aimHeight = this.config.create(new DoubleSetting.Builder(1).name("Aim Height")
        .description("Where to aim (% of height)")
        .min(0.1)
        .max(100)
        .precision(1)
        .get());
    final DoubleSetting range = this.config.create(new DoubleSetting.Builder(3).name("Range")
        .description("How far away an entity has to be to aim at it")
        .min(1)
        .max(10)
        .precision(1)
        .get());
    final EnumSetting<PriorityMode> priority = this.config.create(new EnumSetting.Builder<>(PriorityMode.Distance).name("Priority")
        .description("What to prioritize when aiminig")
        .get());
    final DoubleSetting aimStrength = this.config.create(new DoubleSetting.Builder(1).name("Aim Strength")
        .description("The strength of the aim assist")
        .min(0.1)
        .max(100)
        .precision(1)
        .get());
    final BooleanSetting aimInstant = this.config.create(new BooleanSetting.Builder(false).name("Aim instantly")
        .description("Whether or not to aim instantly instead of smoothly")
        .get());
    final BooleanSetting aimRandom = this.config.create(new BooleanSetting.Builder(false).name("Aim random")
        .description("Whether or not to randomize speed when aiming")
        .get());
    RangeSetting.Range defaultValue = new RangeSetting.Range(1, 100);
    final RangeSetting randomness = this.config.create(new RangeSetting.Builder(defaultValue).name("Randomness")
        .description("How much random smoothing to add")
        .lowerMin(1)
        .lowerMax(100)
        .upperMin(1)
        .upperMax(100)
        .precision(1)
        .get());
    final DoubleSetting aimFov = this.config.create(new DoubleSetting.Builder(180).name("Aim Fov")
        .description("Fov limit for targeting (360 = no limit)")
        .min(10)
        .max(360)
        .precision(1)
        .get());
    final BooleanSetting aimFovY = this.config.create(new BooleanSetting.Builder(true).name("Aim Fov Y")
        .description("Whether or not to apply Aim Fov on the Y axis")
        .get());
    Entity le;

    public AimAssist() {
        super("AimAssist", "Automatically aims at people around you", ModuleType.COMBAT);
        attackPlayers.showIf(() -> !aimAtCombatPartner.getValue());
        attackHostile.showIf(() -> !aimAtCombatPartner.getValue());
        attackNeutral.showIf(() -> !aimAtCombatPartner.getValue());
        attackPassive.showIf(() -> !aimAtCombatPartner.getValue());
        attackInvisible.showIf(() -> !aimAtCombatPartner.getValue());
        attackEverything.showIf(() -> !aimAtCombatPartner.getValue());
        aimStrength.showIf(() -> !aimInstant.getValue());
        aimRandom.showIf(() -> !aimInstant.getValue());
        aimHeight.showIf(() -> aimMode.getValue() == AimMode.Custom);

    }

    @Override
    public void tick() {
        List<Entity> attacks = new ArrayList<>();
        if (aimAtCombatPartner.getValue()) {
            if (AttackManager.getLastAttackInTimeRange() != null) {
                attacks.add(AttackManager.getLastAttackInTimeRange());
            }
        } else {
            for (Entity entity : Objects.requireNonNull(CoffeeMain.client.world).getEntities()) {
                if (entity.equals(CoffeeMain.client.player)) continue;
                if (entity.getPos().distanceTo(CoffeeMain.client.player.getPos()) > range.getValue()) continue;
                if (!entity.isAlive()) continue;
                if (!entity.isAttackable()) continue;
                if (entity.isInvisible() && !attackInvisible.getValue()) continue;
                if (!isWithinFOV(entity)) continue;
                boolean checked = false;
                if (entity instanceof Angerable) {
                    checked = true;
                    if (attackNeutral.getValue()) {
                        attacks.add(entity);
                    } else {
                        continue;
                    }
                }
                if (entity instanceof PlayerEntity) {
                    if (attackPlayers.getValue()) {
                        attacks.add(entity);
                    }
                } else if (entity instanceof HostileEntity) {
                    if (attackHostile.getValue()) {
                        attacks.add(entity);
                    }
                } else if (entity instanceof PassiveEntity) {
                    if (attackPassive.getValue()) {
                        attacks.add(entity);
                    }
                } else if (attackEverything.getValue() && !checked) {
                    attacks.add(entity);
                }
            }

        }
        if (attacks.isEmpty()) {
            le = null;
            return;
        }
        if (priority.getValue() == PriorityMode.Distance) {
            le = attacks.stream()
                .sorted(Comparator.comparingDouble(value -> value.getPos().distanceTo(Objects.requireNonNull(CoffeeMain.client.player).getPos())))
                .toList()
                .get(0);
        } else {
            // get entity with the least health if mode is ascending, else get most health
            le = attacks.stream().sorted(Comparator.comparingDouble(value -> {
                if (value instanceof LivingEntity e) {
                    return e.getHealth() * (priority.getValue() == PriorityMode.Health_ascending ? -1 : 1);
                }
                return Integer.MAX_VALUE; // not a living entity, discard
            })).toList().get(0);
        }

    }

    @Override
    public void onFastTick() {
        aimAtTarget();
    }

    @Override
    public void enable() {

    }

    @Override
    public void disable() {

    }

    @Override
    public String getContext() {
        return null;
    }

    private boolean isWithinFOV(Entity entity) {
        if (aimFov.getValue() >= 360) return true;
        ClientPlayerEntity pla = CoffeeMain.client.player;
        if (pla == null) return false;
        
        Vec3d playerLook = pla.getRotationVec(1.0F);
            
        Vec3d playerPos = pla.getPos();
        Vec3d entityPos = entity.getPos();
        Vec3d toEntity = entityPos.subtract(playerPos);
        if (!aimFovY.getValue()) {
            toEntity = new Vec3d(toEntity.x, 0, toEntity.z);
            playerLook = new Vec3d(playerLook.x, 0 , playerLook.z).normalize();
        }
        toEntity = toEntity.normalize();
        
        double dotProduct = playerLook.dotProduct(toEntity);
        double angle = Math.toDegrees(Math.acos(dotProduct));
        dotProduct = Math.max(-1.0, Math.min(1.0, dotProduct)); // Clamping to avoid possible issues
        return angle <= (aimFov.getValue() / 2);
    }

    void aimAtTarget() {

        double modifiedStrength = (100 - aimStrength.getValue())*2;
        if (aimRandom.getValue()) {
            double randomOffset = ThreadLocalRandom.current().nextDouble(randomness.getValue().getMin(), randomness.getValue().getMax());
            modifiedStrength = modifiedStrength + randomOffset;
        }
        
        if (!aimInstant.getValue()){

            Rotations.lookAtPositionSmooth(le.getPos().add(0, (aimMode.getValue().getY(le, aimHeight.getValue())), 0), (float)(modifiedStrength));

        } else {
            Rotation py = Rotations.getPitchYaw(le.getPos().add(0, (aimMode.getValue().getY(le, aimHeight.getValue())), 0));
            Objects.requireNonNull(CoffeeMain.client.player).setPitch(py.getPitch());
            CoffeeMain.client.player.setYaw(py.getYaw());
        }
    }

    @Override
    public void onWorldRender(MatrixStack matrices) {
        if (le != null) {
            Vec3d origin = le.getPos();
            float h = le.getHeight();
            Renderer.R3D.renderLine(matrices, Utils.getCurrentRGB(), origin, origin.add(0, h, 0));
        }
    }

    @Override
    public void onHudRender() {

    }

    public enum PriorityMode {
        Distance, Health_ascending, Health_descending
    }

    public enum AimMode {
        Top(1.3d),
        Middle(2d),
        Bottom(4d),
        Custom,
        Eyes;
    
        private final Double div;
    
        AimMode() {
            this.div = null;
        }
    
        AimMode(double div) {
            this.div = div;
        }
    
        public double getY(Entity e, double customHeight) {
            if (this == Eyes) {
                return e.getEyePos().y - e.getPos().y;
            }
            if (this == Custom) {
                return (e.getHeight() / 100d) * customHeight;
            }
            return e.getHeight() / div;
        }
    }
}


