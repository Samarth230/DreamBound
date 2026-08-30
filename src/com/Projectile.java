package com;

import java.awt.*;
import java.io.Serializable;

public class Projectile implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int MAX_LIFE_TICKS = 90; // ~1.5s at 60fps before despawning

    public double x, y;
    public double dx, dy;
    public int damage;
    public boolean alive = true;
    public Color color;
    public boolean fromPlayer = true; // false = enemy-fired, damages the player instead
    private int life = 0;

    public Projectile(double x, double y, double dirX, double dirY, double speed, int damage, Color color) {
        this(x, y, dirX, dirY, speed, damage, color, true);
    }

    public Projectile(double x, double y, double dirX, double dirY, double speed, int damage, Color color, boolean fromPlayer) {
        this.x = x;
        this.y = y;
        double len = Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 1e-6) len = 1;
        this.dx = (dirX / len) * speed;
        this.dy = (dirY / len) * speed;
        this.damage = damage;
        this.color = color;
        this.fromPlayer = fromPlayer;
    }

    public void update(int panelWidth, int panelHeight) {
        x += dx;
        y += dy;
        life++;
        if (life > MAX_LIFE_TICKS || x < -20 || y < -20 || x > panelWidth + 20 || y > panelHeight + 20) {
            alive = false;
        }
    }

    public boolean hits(int tx, int ty, int tw, int th) {
        return x >= tx && x <= tx + tw && y >= ty && y <= ty + th;
    }

    public void draw(Graphics2D g2d) {
        g2d.setColor(color);
        g2d.fillOval((int) x - 5, (int) y - 5, 10, 10);
        g2d.setColor(new Color(255, 255, 255, 190));
        g2d.fillOval((int) x - 2, (int) y - 2, 4, 4);
    }
}
