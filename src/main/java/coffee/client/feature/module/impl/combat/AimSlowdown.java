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
    import coffee.client.helper.AimModifyHelper;
    import net.minecraft.client.network.ClientPlayerEntity;
    import net.minecraft.client.util.math.MatrixStack;
    import net.minecraft.entity.Entity;
    import net.minecraft.entity.LivingEntity;
    import net.minecraft.entity.mob.Angerable;
    import net.minecraft.entity.mob.HostileEntity;
    import net.minecraft.entity.passive.PassiveEntity;
    import net.minecraft.entity.player.PlayerEntity;
    import net.minecraft.util.math.Vec3d;
    import net.minecraft.util.math.Box;

    import java.util.Optional;
    import java.util.ArrayList;
    import java.util.Comparator;
    import java.util.List;
    import java.util.Objects;

    public class AimSlowdown extends Module {

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
    final DoubleSetting range = this.config.create(new DoubleSetting.Builder(3).name("Range")
        .description("How far away an entity has to be to aim at it")
        .min(1)
        .max(10)
        .precision(1)
        .get());
    final EnumSetting<PriorityMode> priority = this.config.create(new EnumSetting.Builder<>(PriorityMode.Distance).name("Priority")
        .description("What to prioritize when aiminig")
        .get());
    final BooleanSetting splitXY = this.config.create(new BooleanSetting.Builder(false).name("Split X and Y")
        .description("Splits X and Y slowdown")
        .get());
    final DoubleSetting slowPercent = this.config.create(new DoubleSetting.Builder(1).name("Slow %")
        .description("Strength of the slowdown (in %)")
        .min(0)
        .max(100)
        .precision(1)
        .get());
    final DoubleSetting slowPercentX = this.config.create(new DoubleSetting.Builder(1).name("Slow % X")
        .description("Strength of the slowdown on the X axis (in %)")
        .min(0)
        .max(100)
        .precision(1)
        .get());
    final DoubleSetting slowPercentY = this.config.create(new DoubleSetting.Builder(1).name("Slow % Y")
        .description("Strength of the slowdown (in %)")
        .min(0)
        .max(100)
        .precision(1)
        .get());
    Entity le;

    public AimSlowdown() {
        super("AimSlowdown", "Slows down your sensitivity when aiming over an enemy", ModuleType.COMBAT);
        attackPlayers.showIf(() -> !aimAtCombatPartner.getValue());
        attackHostile.showIf(() -> !aimAtCombatPartner.getValue());
        attackNeutral.showIf(() -> !aimAtCombatPartner.getValue());
        attackPassive.showIf(() -> !aimAtCombatPartner.getValue());
        attackInvisible.showIf(() -> !aimAtCombatPartner.getValue());
        attackEverything.showIf(() -> !aimAtCombatPartner.getValue());
        slowPercent.showIf(() -> !splitXY.getValue());
        slowPercentX.showIf(() -> splitXY.getValue());
        slowPercentY.showIf(() -> splitXY.getValue());
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

        setSlowDown();

    }

    private boolean isLookingAtEntity(Entity entity) {
        ClientPlayerEntity player = CoffeeMain.client.player;
        if (player == null) return false;
        Vec3d cameraPos = player.getCameraPosVec(1.0f);
        Vec3d lookVec = player.getRotationVec(1.0f);
        Vec3d rayEnd = cameraPos.add(lookVec.multiply(range.getValue()));
        Box entityBox = entity.getBoundingBox();
        Optional<Vec3d> hit = entityBox.raycast(cameraPos, rayEnd);
        
        return hit.isPresent();
    }

    @Override
    public void onFastTick() {
        if (le != null && isLookingAtEntity(le)) {
            coffee.client.helper.AimModifyHelper.setEnabled(true);
            return;
        }
        coffee.client.helper.AimModifyHelper.setEnabled(false);
    }

    @Override
    public void enable() {
        coffee.client.helper.AimModifyHelper.setEnabled(false);
    }

    @Override
    public void disable() {

    }

    @Override
    public String getContext() {
        return null;
    }

    void setSlowDown() {
        if (!splitXY.getValue()) {
            float unifiedMultipliers = 1f - (float)(slowPercent.getValue() / 100);
            coffee.client.helper.AimModifyHelper.setSlowdownMultiplierX(unifiedMultipliers);
            coffee.client.helper.AimModifyHelper.setSlowdownMultiplierY(unifiedMultipliers);
        } else {
            coffee.client.helper.AimModifyHelper.setSlowdownMultiplierX(1f - (float)(slowPercentX.getValue() / 100));
            coffee.client.helper.AimModifyHelper.setSlowdownMultiplierY(1f - (float)(slowPercentY.getValue() / 100));
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
}