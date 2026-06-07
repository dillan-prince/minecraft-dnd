package io.github.dillanprince.minecraftdnd.spell;

import java.util.List;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side resolution of spell effects. Kept separate from {@link Spell} (plain data,
 * shared with the client) so server-only types live only here. Targeting is computed at
 * resolution time by raycasting from the caster's view.
 *
 * <p>MVP simplifications: a fixed +3 spellcasting modifier, magic damage type for all
 * spells, and Resistance as a stand-in for Shield's AC bonus (no AC system yet).
 */
public final class SpellEffects {

    private SpellEffects() {}

    private static final double CAST_RANGE = 20.0;
    private static final int SPELL_MOD = 3;
    private static final double FIREBALL_RADIUS = 4.0; // ~20 ft

    public static void resolve(ServerPlayer caster, Spell spell) {
        ServerLevel level = (ServerLevel) caster.level();
        switch (spell.id()) {
            case "fire_bolt" -> fireBolt(caster, level);
            case "magic_missile" -> magicMissile(caster, level);
            case "healing_word" -> healingWord(caster, level);
            case "shield" -> shield(caster, level);
            case "fireball" -> fireball(caster, level);
            default -> { /* no effect defined yet */ }
        }
    }

    private static void fireBolt(ServerPlayer caster, ServerLevel level) {
        LivingEntity target = raycastTarget(caster, level);
        if (target == null) {
            miss(caster);
            return;
        }
        int damage = roll(caster, 1, 10); // 1d10
        target.hurtServer(level, magic(level, caster), damage);
        particles(level, target, ParticleTypes.FLAME, 24);
    }

    private static void magicMissile(ServerPlayer caster, ServerLevel level) {
        LivingEntity target = raycastTarget(caster, level);
        if (target == null) {
            miss(caster);
            return;
        }
        // Three darts, each 1d4+1 force damage; auto-hit, so apply the sum.
        int damage = roll(caster, 3, 4) + 3;
        target.hurtServer(level, magic(level, caster), damage);
        particles(level, target, ParticleTypes.ENCHANTED_HIT, 18);
    }

    private static void healingWord(ServerPlayer caster, ServerLevel level) {
        LivingEntity target = raycastTarget(caster, level);
        if (target == null) {
            target = caster; // default to self if not aiming at an ally
        }
        int healing = roll(caster, 2, 4) + SPELL_MOD; // 2d4 + mod
        target.heal(healing);
        particles(level, target, ParticleTypes.HEART, 8);
    }

    private static void shield(ServerPlayer caster, ServerLevel level) {
        // Stand-in for "+5 AC until your next turn": brief Resistance on the caster.
        caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 1), caster);
        particles(level, caster, ParticleTypes.ENCHANTED_HIT, 16);
    }

    private static void fireball(ServerPlayer caster, ServerLevel level) {
        Vec3 center = aimPoint(caster, level);
        int damage = roll(caster, 8, 6); // 8d6
        AABB area = new AABB(center, center).inflate(FIREBALL_RADIUS);
        DamageSource source = magic(level, caster);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != caster && e.isAlive());
        for (LivingEntity target : targets) {
            target.hurtServer(level, source, damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    // --- helpers ---

    /** Sum of {@code count} dice with {@code sides} faces each. */
    private static int roll(ServerPlayer caster, int count, int sides) {
        RandomSource random = caster.getRandom();
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += random.nextInt(sides) + 1;
        }
        return total;
    }

    private static DamageSource magic(ServerLevel level, ServerPlayer caster) {
        return level.damageSources().indirectMagic(caster, caster);
    }

    /** First living entity the caster is looking at (block-occluded), within range. */
    private static LivingEntity raycastTarget(ServerPlayer caster, ServerLevel level) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = aimPoint(caster, level);
        AABB box = new AABB(eye, end).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, caster, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != caster, 0.3F);
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    /** The point the caster is aiming at: the first block hit, or max range if none. */
    private static Vec3 aimPoint(ServerPlayer caster, ServerLevel level) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(CAST_RANGE));
        BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return blockHit.getLocation();
    }

    private static void particles(ServerLevel level, LivingEntity target, net.minecraft.core.particles.SimpleParticleType type, int count) {
        level.sendParticles(type, target.getX(), target.getY() + 1.0, target.getZ(), count, 0.3, 0.4, 0.3, 0.02);
    }

    private static void miss(ServerPlayer caster) {
        caster.sendSystemMessage(Component.literal("No target in sight.").withStyle(ChatFormatting.GRAY));
    }
}
