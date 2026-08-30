package com;

import java.awt.Color;

/**
 * Every playable class the party can contain. Stats here are level-1 baselines;
 * Party scales them up with player level (see Party.scaledMaxHp/scaledAttackPower).
 *
 * spriteFolder points at Resources/<spriteFolder>/{idle,run,attack1,attack2,damaged,die}/*.png
 * WARRIOR keeps pointing at the existing "character" folder so nothing breaks
 * today. Every other class currently has no art — GamePanel falls back to a
 * colored silhouette (using fallbackColor) until you drop sprites into the
 * matching folder, so the game runs and is fully playable right now.
 */
public enum CharacterClass {
    WARRIOR(
        "Warrior", "character",
        100, 10, 500, 1.00, 60, false, 0, false,
        new Color(100, 150, 255),
        "Balanced all-rounder. Starting class."
    ),
    TANK(
        "Tank", "character/tank",
        160, 8, 700, 0.80, 55, false, 0, false,
        new Color(150, 150, 170),
        "Huge HP pool, hits a little softer and moves slower."
    ),
    ROGUE(
        "Rogue", "character/rogue",
        80, 9, 300, 1.25, 50, false, 0, true,
        new Color(160, 60, 170),
        "Fast attacks, fast feet, low HP. Uses a quicker dual-swing style."
    ),
    MAGE(
        "Mage", "character/mage",
        70, 14, 650, 0.95, 220, true, 6, false,
        new Color(90, 180, 255),
        "Ranged magic bolts, long range, glass cannon."
    ),
    ARCHER(
        "Archer", "character/archer",
        85, 11, 450, 1.10, 260, true, 8, false,
        new Color(210, 180, 90),
        "Ranged arrows, longest range, fragile."
    );

    public final String displayName;
    public final String spriteFolder;
    public final int baseMaxHp;
    public final int baseAttackPower;
    public final int attackCooldownMs;
    public final double moveSpeedMultiplier;
    public final int attackRange;
    public final boolean isRanged;
    public final int projectileSpeed;
    // Melee classes only: true = use the attack2 sprite set as this class's
    // swing (a distinct moveset/style), false = use attack1 (the original swing).
    public final boolean useAltSwing;
    public final Color fallbackColor;
    public final String description;

    CharacterClass(String displayName, String spriteFolder, int baseMaxHp, int baseAttackPower,
                    int attackCooldownMs, double moveSpeedMultiplier, int attackRange,
                    boolean isRanged, int projectileSpeed, boolean useAltSwing,
                    Color fallbackColor, String description) {
        this.displayName = displayName;
        this.spriteFolder = spriteFolder;
        this.baseMaxHp = baseMaxHp;
        this.baseAttackPower = baseAttackPower;
        this.attackCooldownMs = attackCooldownMs;
        this.moveSpeedMultiplier = moveSpeedMultiplier;
        this.attackRange = attackRange;
        this.isRanged = isRanged;
        this.projectileSpeed = projectileSpeed;
        this.useAltSwing = useAltSwing;
        this.fallbackColor = fallbackColor;
        this.description = description;
    }
}
