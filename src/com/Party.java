package com;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Roster of unlocked classes. Every unlocked member keeps their own saved HP
 * while benched, so swapping mid-run isn't a free heal.
 *
 * Leveling is shared across the whole party (one level/exp on Player), but
 * each class's actual maxHp/attackPower is derived from its own base stats
 * scaled by that shared level - see scaledMaxHp/scaledAttackPower.
 */
public class Party implements Serializable {
    private static final long serialVersionUID = 1L;

    // Which GameMap id's boss unlocks which class(es). A boss can unlock more
    // than one class at once (e.g. boss 1 unlocks both Rogue and Mage).
    public static final Map<Integer, List<CharacterClass>> BOSS_UNLOCKS = new LinkedHashMap<>();
    static {
        BOSS_UNLOCKS.put(4, List.of(CharacterClass.ROGUE, CharacterClass.MAGE));  // Level 5: Raku's Lair
        BOSS_UNLOCKS.put(9, List.of(CharacterClass.TANK));                        // Level 10: The Last Vigil (Wraith)
    }

    public List<CharacterClass> unlocked = new ArrayList<>();
    public CharacterClass active = CharacterClass.WARRIOR;
    private Map<CharacterClass, Integer> savedHp = new LinkedHashMap<>();

    public Party() {
        unlocked.add(CharacterClass.WARRIOR);
    }

    public boolean isUnlocked(CharacterClass cls) {
        return unlocked.contains(cls);
    }

    public int scaledMaxHp(CharacterClass cls, int level) {
        return cls.baseMaxHp + (level - 1) * 20;
    }

    public int scaledAttackPower(CharacterClass cls, int level) {
        return cls.baseAttackPower + (level - 1) * 5;
    }

    /** Returns true if this actually unlocked a new class (false if already had it). */
    public boolean unlock(CharacterClass cls, Player player) {
        if (isUnlocked(cls)) return false;
        unlocked.add(cls);
        savedHp.put(cls, scaledMaxHp(cls, player.level));
        return true;
    }

    /** Swaps the active party member, applying that class's stats to the live Player. */
    public void switchTo(CharacterClass cls, Player player) {
        if (!isUnlocked(cls) || cls == active) return;

        savedHp.put(active, player.hp);

        int newMaxHp = scaledMaxHp(cls, player.level);
        Integer prevHp = savedHp.get(cls);

        active = cls;
        player.characterClass = cls;
        player.maxHp = newMaxHp;
        player.attackPower = scaledAttackPower(cls, player.level);
        player.attackCooldown = cls.attackCooldownMs;
        player.hp = (prevHp != null) ? Math.min(prevHp, newMaxHp) : newMaxHp;
        savedHp.put(cls, player.hp);
    }

    /** Call after a level-up so the active member's HP/attack scale up correctly. */
    public void refreshActiveStats(Player player) {
        player.maxHp = scaledMaxHp(active, player.level);
        player.attackPower = scaledAttackPower(active, player.level);
        player.hp = player.maxHp; // full heal on level up, matches old behavior
        savedHp.put(active, player.hp);
    }
}
