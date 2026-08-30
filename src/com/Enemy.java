package com;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Serializable;

public class Enemy implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public int x, y;
    public int width = 32, height = 32;
    public int hp;
    public int maxHp;
    public int damage;
    public int expReward;
    public EnemyType type;
    public boolean isDead = false;
    public long lastAttackTime = 0;
    public int attackCooldown = 1000;

    // For sprite-based rendering: which animation frame to show and a brief
    // "hurt" flash window after taking damage.
    public long lastHitTime = -2000;
    public static final int HURT_FLASH_MS = 250;
    public boolean isMoving = false;
    
    private int directionTimer = 0;
    private int dx = 0, dy = 0;
    private int animationFrame = 0;
    
    public enum EnemyType {
        SLIME(30, 5, 20, new Color(120, 255, 120), new Color(80, 200, 80), "enemy/slime1", 44, 38, false),
        SLIME_ELITE(55, 9, 40, new Color(90, 210, 255), new Color(50, 150, 210), "enemy/slime2", 44, 38, false),
        SLIME_ROYAL(85, 13, 65, new Color(190, 120, 255), new Color(130, 70, 200), "enemy/slime3", 46, 40, false),
        GOBLIN(50, 8, 35, new Color(150, 200, 100), new Color(100, 150, 70), null, 32, 32, false),
        SKELETON(70, 12, 50, new Color(240, 240, 240), new Color(180, 180, 180), null, 32, 32, false),
        WRAITH(90, 14, 70, new Color(120, 220, 200), new Color(70, 160, 150), "enemy/wraith", 48, 48, false),
        BOSS(300, 20, 200, new Color(220, 50, 50), new Color(150, 30, 30), null, 64, 64, true),
        // Boss 1 (Level 5): Raku, a giant slime - reuses the Royal slime art at boss scale.
        RAKU(380, 18, 250, new Color(190, 120, 255), new Color(130, 70, 200), "enemy/slime3", 104, 90, true),
        // Boss 2 (Level 10): a huge, faster-shooting Wraith that keeps its distance.
        BOSS_WRAITH(420, 16, 320, new Color(120, 220, 200), new Color(70, 160, 150), "enemy/wraith", 92, 92, true);
        
        public final int hp;
        public final int damage;
        public final int exp;
        public final Color color1;
        public final Color color2;
        public final String spriteFolder; // null = no sprites yet, use the gradient-blob fallback
        public final int width;
        public final int height;
        public final boolean isBoss;

        EnemyType(int hp, int damage, int exp, Color color1, Color color2, String spriteFolder,
                  int width, int height, boolean isBoss) {
            this.hp = hp;
            this.damage = damage;
            this.exp = exp;
            this.color1 = color1;
            this.color2 = color2;
            this.spriteFolder = spriteFolder;
            this.width = width;
            this.height = height;
            this.isBoss = isBoss;
        }
    }
    
    public Enemy(int x, int y, EnemyType type) {
        this.x = x;
        this.y = y;
        this.type = type;
        this.hp = type.hp;
        this.maxHp = type.hp;
        this.damage = type.damage;
        this.expReward = type.exp;
        this.width = type.width;
        this.height = type.height;
    }
    
    public void update(Player player, int panelWidth, int panelHeight) {
        if (isDead) return;
        
        animationFrame++;
        int startX = x, startY = y;

        if (type == EnemyType.BOSS_WRAITH) {
            updateKiting(player, panelWidth, panelHeight, startX, startY);
            return;
        }
        
        double dist = Math.sqrt(Math.pow(player.x - x, 2) + Math.pow(player.y - y, 2));
        
        if (dist < 250 && dist > width) {
            int speed = (type.isBoss) ? 2 : 1;
            if (player.x < x) x -= speed;
            if (player.x > x) x += speed;
            if (player.y < y) y -= speed;
            if (player.y > y) y += speed;
        } else if (dist > 250) {
            directionTimer++;
            if (directionTimer > 60) {
                dx = (int)(Math.random() * 3) - 1;
                dy = (int)(Math.random() * 3) - 1;
                directionTimer = 0;
            }
            x += dx;
            y += dy;
        }
        
        x = Math.max(0, Math.min(panelWidth - width, x));
        y = Math.max(0, Math.min(panelHeight - height, y));

        isMoving = (x != startX || y != startY);
        
        if (dist < width + 10 && canAttack()) {
            player.takeDamage(damage);
            attack();
        }
    }

    // A ranged boss that actively backs away when the player closes in, and
    // creeps forward again if they retreat too far - keeps it dangerous at
    // range instead of just standing in one spot firing.
    private static final double KITE_TOO_CLOSE = 170;
    private static final double KITE_TOO_FAR = 300;

    private void updateKiting(Player player, int panelWidth, int panelHeight, int startX, int startY) {
        double dist = Math.sqrt(Math.pow(player.x - x, 2) + Math.pow(player.y - y, 2));
        int speed = 3;

        if (dist < KITE_TOO_CLOSE) {
            // Back away from the player
            if (player.x < x) x += speed; else if (player.x > x) x -= speed;
            if (player.y < y) y += speed; else if (player.y > y) y -= speed;
        } else if (dist > KITE_TOO_FAR) {
            // Creep back into range
            if (player.x < x) x -= speed; else if (player.x > x) x += speed;
            if (player.y < y) y -= speed; else if (player.y > y) y += speed;
        }

        x = Math.max(0, Math.min(panelWidth - width, x));
        y = Math.max(0, Math.min(panelHeight - height, y));

        isMoving = (x != startX || y != startY);

        if (dist < width + 10 && canAttack()) {
            player.takeDamage(damage);
            attack();
        }
    }
    
    public void takeDamage(int damage) {
        hp -= damage;
        lastHitTime = System.currentTimeMillis();
        if (hp <= 0) {
            hp = 0;
            isDead = true;
        }
    }

    public boolean isFlashingHurt() {
        return System.currentTimeMillis() - lastHitTime < HURT_FLASH_MS;
    }
    
    public boolean canAttack() {
        return System.currentTimeMillis() - lastAttackTime >= attackCooldown;
    }

    public static final int ATTACK_ANIM_MS = 350;
    public boolean isAttackAnimating() {
        return System.currentTimeMillis() - lastAttackTime < ATTACK_ANIM_MS;
    }

    // --- Ranged attack (Wraiths only) ---
    public static final int WRAITH_RANGED_RANGE = 260;
    public static final int WRAITH_RANGED_COOLDOWN_MS = 1800;
    public static final double WRAITH_PROJECTILE_SPEED = 4.5;
    public static final int BOSS_WRAITH_RANGED_RANGE = 320;
    public static final int BOSS_WRAITH_RANGED_COOLDOWN_MS = 800; // notably quicker than regular wraiths
    public static final double BOSS_WRAITH_PROJECTILE_SPEED = 6.0;
    private long lastRangedAttackTime = 0;

    /** Returns a projectile to add to the game if this enemy fires this tick, else null. */
    public Projectile tryRangedAttack(Player player) {
        boolean isBossWraith = type == EnemyType.BOSS_WRAITH;
        if ((type != EnemyType.WRAITH && !isBossWraith) || isDead) return null;

        int range = isBossWraith ? BOSS_WRAITH_RANGED_RANGE : WRAITH_RANGED_RANGE;
        int cooldown = isBossWraith ? BOSS_WRAITH_RANGED_COOLDOWN_MS : WRAITH_RANGED_COOLDOWN_MS;
        double speed = isBossWraith ? BOSS_WRAITH_PROJECTILE_SPEED : WRAITH_PROJECTILE_SPEED;

        double dist = Math.sqrt(Math.pow(player.x - x, 2) + Math.pow(player.y - y, 2));
        if (dist > range || dist < 44) return null; // too far, or already in melee range
        if (System.currentTimeMillis() - lastRangedAttackTime < cooldown) return null;

        lastRangedAttackTime = System.currentTimeMillis();
        lastAttackTime = System.currentTimeMillis(); // also flashes the jump/attack sprite

        double dx = (player.x + player.width / 2.0) - (x + width / 2.0);
        double dy = (player.y + player.height / 2.0) - (y + height / 2.0);
        return new Projectile(x + width / 2.0, y + height / 2.0, dx, dy,
            speed, damage, new Color(130, 230, 210), false);
    }
    
    public void attack() {
        lastAttackTime = System.currentTimeMillis();
    }

    /**
     * Draws this enemy. If spriteFrame is non-null, draws that sprite (scaled
     * to width/height) instead of the original gradient-blob rendering.
     * Passing null preserves the exact original look for types with no art yet.
     */
    public void draw(Graphics g, BufferedImage spriteFrame) {
        if (isDead) return;
        
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2d.setColor(new Color(0, 0, 0, 80));
        g2d.fillOval(x + 4, y + height - 4, width - 4, 8);

        if (spriteFrame != null) {
            // Brief white flash on hit, same idea as the player's invincibility feedback.
            g2d.drawImage(spriteFrame, x, y, width, height, null);
            if (isFlashingHurt()) {
                g2d.setColor(new Color(255, 255, 255, 110));
                g2d.fillRect(x, y, width, height);
            }
        } else {
            int pulse = (int)(Math.sin(animationFrame * 0.1) * 3);

            GradientPaint gradient = new GradientPaint(
                x, y, type.color1,
                x, y + height, type.color2
            );
            g2d.setPaint(gradient);
            g2d.fillOval(x - pulse, y - pulse, width + pulse * 2, height + pulse * 2);

            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.setStroke(new BasicStroke(2));
            g2d.drawOval(x - pulse, y - pulse, width + pulse * 2, height + pulse * 2);

            g2d.setColor(Color.BLACK);
            int eyeSize = (type.isBoss) ? 8 : 4;
            g2d.fillOval(x + width / 3 - eyeSize / 2, y + height / 3, eyeSize, eyeSize);
            g2d.fillOval(x + 2 * width / 3 - eyeSize / 2, y + height / 3, eyeSize, eyeSize);
        }
        
        int barWidth = width + 8;
        int barX = x - 4;
        int barY = y - 14;
        
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRoundRect(barX, barY, barWidth, 8, 4, 4);
        
        g2d.setColor(new Color(180, 50, 50));
        g2d.fillRoundRect(barX + 1, barY + 1, barWidth - 2, 6, 3, 3);
        
        g2d.setColor(new Color(100, 255, 100));
        int hpWidth = (int)((double)hp / maxHp * (barWidth - 2));
        g2d.fillRoundRect(barX + 1, barY + 1, hpWidth, 6, 3, 3);
        
        if (type.isBoss) {
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRoundRect(x - 10, y - 30, width + 20, 16, 8, 8);
            g2d.setColor(Color.RED);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2d.drawString("BOSS", x + width / 2 - 18, y - 18);
        }
    }
}
