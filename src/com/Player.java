package com;



import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class Player implements Serializable {
    private static final long serialVersionUID = 2L;

    public int x, y;
    public int width = 64, height = 64;
    public int hp = 100;
    public int maxHp = 100;
    public int level = 1;
    public int experience = 0;
    public int currentMap = 0;
    public boolean finalBossDefeated = false;
    public CharacterClass characterClass = CharacterClass.WARRIOR;
    private Set<String> flags = new HashSet<>();
    
    public int attackPower = 30;
    public long lastAttackTime = 0;
    public int attackCooldown = 500;
    
    public int animationFrame = 0;
    public int direction = 0;

    public boolean isAttacking = false;
    public int attackFrame = 0;

    // Invincibility frames: prevents multiple enemies from stacking damage
    // into the player on the same tick / within a short window of each other.
    public static final int INVINCIBILITY_DURATION = 800; // ms
    public long lastHitTime = -INVINCIBILITY_DURATION;

    public Player(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean isInvincible() {
        return System.currentTimeMillis() - lastHitTime < INVINCIBILITY_DURATION;
    }

    /**
     * Applies damage unless the player is currently invincible or already dead.
     * Returns true if the damage was actually applied (useful for triggering
     * hit effects only on a real hit, not on every enemy collision tick).
     */
    public boolean takeDamage(int damage) {
        if (isInvincible() || hp <= 0) return false;
        hp -= damage;
        if (hp < 0) hp = 0;
        lastHitTime = System.currentTimeMillis();
        return true;
    }
    
    public void heal(int amount) {
        hp += amount;
        if (hp > maxHp) hp = maxHp;
    }
    
    public boolean hasFlag(String flag) {
        if (flags == null) flags = new HashSet<>();
        return flag != null && flags.contains(flag);
    }

    public void setFlag(String flag) {
        if (flags == null) flags = new HashSet<>();
        if (flag != null) flags.add(flag);
    }

    /** Returns true if the player gained at least one level. */
    public boolean gainExperience(int exp) {
        experience += exp;
        boolean leveledUp = false;
        while (experience >= level * 100) {
            experience -= level * 100;
            level++;
            maxHp += 20;
            hp = maxHp;
            attackPower += 5;
            leveledUp = true;
        }
        return leveledUp;
    }
    
    public boolean canAttack() {
        return System.currentTimeMillis() - lastAttackTime >= attackCooldown;
    }
    
    public void attack() {
        lastAttackTime = System.currentTimeMillis();
    }
}