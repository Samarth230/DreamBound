package com;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;


public class GamePanel extends JPanel implements ActionListener {
    // Save file now lives in a fixed per-user directory instead of the process's
    // working directory, so it works the same whether launched from an IDE,
    // a terminal in a different folder, or a packaged jar.
    public static final File SAVE_DIR = new File(System.getProperty("user.home"), ".dreambound");
    public static final File SAVE_FILE = new File(SAVE_DIR, "savegame.dat");

    private static final double PLAYER_SPEED = 187.5; // px/sec, matches the old 3px @ ~16ms tick
    private static final double POINT_BLANK_RANGE = 46; // melee hits land here regardless of facing

    private final Timer timer;
    private long lastFrameTime = System.nanoTime();

    private int deathFrame = 0;
    private int deathAnimTick = 0;
    private Player player;
    private Party party;
    private List<GameMap> maps;
    private GameMap currentMap;
    private List<Projectile> projectiles = new ArrayList<>();
    private NPC activeNpc = null;
    private DialogueNode activeDialogueNode = null;
    private static final int TALK_RANGE = 70;
    private final java.util.ArrayDeque<Runnable> pendingPopups = new java.util.ArrayDeque<>();

    /** Holds idle/run/attack1/attack2/damaged/die frame arrays for one class. */
    private static class AnimationSet {
        BufferedImage[] idle = new BufferedImage[0];
        BufferedImage[] run = new BufferedImage[0];
        BufferedImage[] attack1 = new BufferedImage[0];
        BufferedImage[] attack2 = new BufferedImage[0];
        BufferedImage[] damaged = new BufferedImage[0];
        BufferedImage[] die = new BufferedImage[0];
    }
    private final Map<CharacterClass, AnimationSet> classAnimations = new EnumMap<>(CharacterClass.class);
    private final Map<Enemy.EnemyType, AnimationSet> enemyAnimations = new EnumMap<>(Enemy.EnemyType.class);
    private final Map<String, BufferedImage> decorationImages = new HashMap<>();
    private final Map<String, BufferedImage> groundTiles = new HashMap<>();

    private int playerAnimFrame = 0;
    private int animTick = 0;
    
    private final boolean[] keys = new boolean[4];
    private boolean spacePressed = false;

    private final CardLayout cardLayout;
    private final JPanel container;
    
    private String message = "";
    private long messageTime = 0;
    private int frameCount = 0;
    private long lastPortalTime = 0;
    private static final int PORTAL_COOLDOWN = 1000; // 1 second
    
    private List<Particle> particles = new ArrayList<>();
    private List<HealthPickup> healthPickups = new ArrayList<>();

    private boolean shiftPressed = false;
    private long lastDashTime = 0;
    private static final int DASH_COOLDOWN = 2000; // 2 seconds
    private static final int DASH_DISTANCE = 80;


    public GamePanel(CardLayout cardLayout, JPanel container) {
        this.cardLayout = cardLayout;
        this.container = container;
        setPreferredSize(new Dimension(800, 600));
        setBackground(Color.BLACK);
        setFocusable(true);
        setupKeyBindings();
        // Key Bindings still latch a key as "held" if focus is lost mid-press
        // (e.g. alt-tab), so clear everything as a safety net when that happens.
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                clearInputState();
            }
        });
        loadAnimations();
        timer = new Timer(16, this);
        timer.start();
    }

    private void clearInputState() {
        for (int i = 0; i < keys.length; i++) keys[i] = false;
        spacePressed = false;
        shiftPressed = false;
    }

    private void setupKeyBindings() {
        InputMap im = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        bindKey(im, am, "up", KeyEvent.VK_UP, pressed -> keys[0] = pressed);
        bindKey(im, am, "down", KeyEvent.VK_DOWN, pressed -> keys[1] = pressed);
        bindKey(im, am, "left", KeyEvent.VK_LEFT, pressed -> keys[2] = pressed);
        bindKey(im, am, "right", KeyEvent.VK_RIGHT, pressed -> keys[3] = pressed);
        bindKey(im, am, "attack", KeyEvent.VK_SPACE, pressed -> spacePressed = pressed);
        bindKey(im, am, "dash", KeyEvent.VK_SHIFT, pressed -> shiftPressed = pressed);

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, false), "save");
        am.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Guard: don't allow saving a dead player into a permanently
                // unrecoverable game-over state.
                if (player == null || player.hp <= 0) return;
                boolean ok = saveGame();
                showMessage(ok ? "Game saved successfully!" : "Save failed!");
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false), "menu");
        am.put("menu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (activeDialogueNode != null) {
                    closeDialogue();
                    return;
                }
                cardLayout.show(container, "HOME");
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, false), "talk");
        am.put("talk", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleTalkKey();
            }
        });

        // Number keys 1-5: swap active party member normally, or pick a
        // dialogue choice when a conversation is open (choices are 1-indexed
        // in the dialogue box to match).
        int[] slotKeys = { KeyEvent.VK_1, KeyEvent.VK_2, KeyEvent.VK_3, KeyEvent.VK_4, KeyEvent.VK_5 };
        for (int i = 0; i < slotKeys.length; i++) {
            final int slotIndex = i;
            String id = "switchClass" + i;
            im.put(KeyStroke.getKeyStroke(slotKeys[i], 0, false), id);
            am.put(id, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (activeDialogueNode != null) {
                        selectDialogueChoice(slotIndex);
                    } else {
                        switchToPartySlot(slotIndex);
                    }
                }
            });
        }
    }

    private void handleTalkKey() {
        if (player == null || currentMap == null) return;
        if (activeDialogueNode != null) return; // already talking

        for (NPC npc : currentMap.npcs) {
            if (npc.playerInRange(player, TALK_RANGE)) {
                activeNpc = npc;
                activeDialogueNode = npc.getStartNode();
                return;
            }
        }
    }

    private void closeDialogue() {
        activeNpc = null;
        activeDialogueNode = null;
        if (!pendingPopups.isEmpty()) {
            pendingPopups.poll().run();
        }
    }

    /** Queues a "so-and-so has joined" popup. If nothing else is showing, it opens immediately;
     *  otherwise it waits so simultaneous unlocks (e.g. boss 1's Rogue+Mage) each get their own screen. */
    private void announceJoin(CharacterClass cls) {
        Runnable open = () -> openJoinPopup(cls);
        pendingPopups.add(open);
        if (activeDialogueNode == null) {
            pendingPopups.poll().run();
        }
    }

    private void triggerVillageArrival() {
        Runnable open = this::openVillageArrivalPopup;
        pendingPopups.add(open);
        if (activeDialogueNode == null) {
            pendingPopups.poll().run();
        }
    }

    private void openVillageArrivalPopup() {
        NPC intro = new NPC("Elder Wren", 0, 0, new Color(200, 170, 110), null);

        DialogueNode welcome = new DialogueNode("welcome",
            "\"Then come in - you've earned a real meal and a real bed. This is Sanctuary Village.\n" +
            "There's a few of us here who might have answers for you, if you're ready to hear them.\"");
        welcome.choice(new DialogueChoice("Thank you.", "").setFlag("metElderAtVillage"));

        DialogueNode explainRaku = new DialogueNode("explainRaku",
            "\"Raku - the slime that's been swallowing travelers whole out past the ruins. " +
            "If you walked out of that keep breathing, you beat it, one way or another.\"");
        explainRaku.choice(new DialogueChoice("I did.", "welcome"));

        DialogueNode start = new DialogueNode("start",
            "Elder Wren hurries over before you've even taken in the village gate.\n" +
            "\"You're... you're really back. You defeated Raku?\"");
        start.choice(new DialogueChoice("Yes. It's gone.", "welcome"));
        start.choice(new DialogueChoice("...Raku?", "explainRaku"));

        intro.addNode(start);
        intro.addNode(explainRaku);
        intro.addNode(welcome);

        activeNpc = intro;
        activeDialogueNode = start;
    }

    private void openJoinPopup(CharacterClass cls) {
        NPC intro = new NPC(cls.displayName + " has joined!", 0, 0, cls.fallbackColor, null);
        DialogueNode node = new DialogueNode("start", joinLine(cls));
        int slot = party.unlocked.indexOf(cls) + 1;
        node.choice(new DialogueChoice("Got it. (Press " + slot + " to switch to " + cls.displayName + ")", ""));
        intro.addNode(node);
        activeNpc = intro;
        activeDialogueNode = node;
    }

    private String joinLine(CharacterClass cls) {
        switch (cls) {
            case ROGUE:
                return "Name's Kael. Fast and quiet, that's the whole plan.\n" +
                    "I hit quick up close but I can't take much punishment - press the number keys anytime to swap me in.";
            case MAGE:
                return "I'm Sable. I don't do close range - I'll put a bolt through anything from a distance.\n" +
                    "Just don't let me get surrounded, I break easy.";
            case TANK:
                return "Bron, at your service. I can take hits the others can't.\n" +
                    "Swap to me when things get rough - I'll hold the line.";
            case ARCHER:
                return "Name's Fen. Picked up a bow somewhere in all this.\n" +
                    "Longest range in the party - I'll keep my distance and pepper anything that gets close to you.";
            default:
                return cls.displayName + " has joined the party.";
        }
    }

    private void selectDialogueChoice(int index) {
        if (activeDialogueNode == null || activeNpc == null) return;

        List<DialogueChoice> available = new ArrayList<>();
        for (DialogueChoice c : activeDialogueNode.choices) {
            if (c.isAvailable(player)) available.add(c);
        }
        if (index < 0 || index >= available.size()) return;

        DialogueChoice chosen = available.get(index);
        if (chosen.setFlag != null) {
            player.setFlag(chosen.setFlag);
        }

        if (chosen.nextNodeId == null || chosen.nextNodeId.isEmpty()) {
            closeDialogue();
        } else {
            activeDialogueNode = activeNpc.nodes.get(chosen.nextNodeId);
        }
    }

    private void switchToPartySlot(int slotIndex) {
        if (player == null || party == null || player.hp <= 0) return;
        if (slotIndex < 0 || slotIndex >= party.unlocked.size()) return;
        CharacterClass target = party.unlocked.get(slotIndex);
        if (target == party.active) return;
        party.switchTo(target, player);
        showMessage("Switched to " + target.displayName + "!");
    }

    private interface KeyStateSetter {
        void set(boolean pressed);
    }

    private void bindKey(InputMap im, ActionMap am, String id, int keyCode, KeyStateSetter setter) {
        String pressedId = id + "Pressed";
        String releasedId = id + "Released";

        im.put(KeyStroke.getKeyStroke(keyCode, 0, false), pressedId);
        am.put(pressedId, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { setter.set(true); }
        });

        im.put(KeyStroke.getKeyStroke(keyCode, 0, true), releasedId);
        am.put(releasedId, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) { setter.set(false); }
        });
    }

    public void startNewGame() {
        player = new Player(getWidth() / 2 - 16, getHeight() / 2 - 16);
        party = new Party();
        player.characterClass = party.active;
        initializeMaps();
        currentMap = maps.get(player.currentMap);
        particles.clear();
        projectiles.clear();
        deathFrame = 0;
        deathAnimTick = 0;
        lastFrameTime = System.nanoTime();
        activeNpc = null;
        activeDialogueNode = null;
        showMessage("Your adventure begins!");
        requestFocusInWindow();
    }
    
    private void initializeMaps() {
        maps = new ArrayList<>();
        maps.add(GameMap.createStartingArea());        // 0  Level 1
        maps.add(GameMap.createForest());               // 1  Level 2
        maps.add(GameMap.createCave());                  // 2  Level 3
        maps.add(GameMap.createRuinsApproach());          // 3  Level 4
        maps.add(GameMap.createRakusLair());               // 4  Level 5  - BOSS 1 (Raku)
        maps.add(GameMap.createSanctuaryVillage());         // 5  Level 6  - checkpoint
        maps.add(GameMap.createOvergrownThicket());          // 6  Level 7
        maps.add(GameMap.createCrossroads());                 // 7  Level 8  - FORK
        maps.add(GameMap.createBushHollow());                  // 8  Level 9  - main path
        maps.add(GameMap.createWraithBossRoom());               // 9  Level 10 - BOSS 2 (Wraith)
        maps.add(GameMap.createRockyPass());                     // 10 alt path 1
        maps.add(GameMap.createStoneWastes());                    // 11 alt path 2
        maps.add(GameMap.createRuinedThrone());                    // 12 alt boss
        maps.add(GameMap.createSunkenArchive());                    // 13 side path
    }

    public boolean saveGame() {
        if (player == null) return false;
        try {
            SAVE_DIR.mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
                oos.writeObject(player);
                oos.writeObject(maps);
                oos.writeObject(party);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean loadGame() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SAVE_FILE))) {
            this.player = (Player) ois.readObject();
            this.maps = (List<GameMap>) ois.readObject();
            this.party = (Party) ois.readObject();
            this.currentMap = maps.get(player.currentMap);
            particles.clear();
            projectiles.clear();
            deathFrame = 0;
            deathAnimTick = 0;
            lastFrameTime = System.nanoTime();
            activeNpc = null;
            activeDialogueNode = null;
            requestFocusInWindow();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        frameCount++;
        
        if (player == null) {
            repaint();
            return;
        }
        
        if (player.hp <= 0) {
            deathAnimTick++;
            AnimationSet activeAnim = classAnimations.get(player.characterClass);
            if (activeAnim != null && activeAnim.die.length > 0 && deathAnimTick % 8 == 0) {
                deathFrame = Math.min(deathFrame + 1, activeAnim.die.length - 1);
            }
            repaint();
            return;
        }
        
        if (activeDialogueNode != null) {
            repaint();
            return;
        }
        
        long now = System.nanoTime();
        double dt = (now - lastFrameTime) / 1_000_000_000.0;
        lastFrameTime = now;
        dt = Math.min(dt, 0.05); // clamp so a lag spike can't teleport the player

        double moveX = 0, moveY = 0;
        boolean isMoving = false;
        if (keys[0]) { moveY -= 1; player.direction = 1; isMoving = true; }
        if (keys[1]) { moveY += 1; player.direction = 0; isMoving = true; }
        if (keys[2]) { moveX -= 1; player.direction = 2; isMoving = true; }
        if (keys[3]) { moveX += 1; player.direction = 3; isMoving = true; }

        if (isMoving) {
            // Normalize so diagonal movement isn't ~41% faster than cardinal movement.
            double len = Math.sqrt(moveX * moveX + moveY * moveY);
            moveX /= len;
            moveY /= len;
            double speed = PLAYER_SPEED * player.characterClass.moveSpeedMultiplier;
            player.x += (int) Math.round(moveX * speed * dt);
            player.y += (int) Math.round(moveY * speed * dt);
        }
        
            animTick++;
    if (animTick >= 8) {
        animTick = 0;
        
        if (keys[0] || keys[1] || keys[2] || keys[3]) {
            playerAnimFrame = (playerAnimFrame + 1) % 7; 
        } else {
            playerAnimFrame = 0;
        }
    }
        if (isMoving) {
            player.animationFrame++;
        }

        player.x = Math.max(0, Math.min(getWidth() - player.width, player.x));
        player.y = Math.max(0, Math.min(getHeight() - player.height, player.y));
        
        int hpBeforeEnemies = player.hp;
        currentMap.update(player, getWidth(), getHeight());
        if (player.hp < hpBeforeEnemies) {
            createHitEffect(player.x + player.width / 2, player.y + player.height / 2);
        }

        for (Enemy enemy : currentMap.enemies) {
            Projectile shot = enemy.tryRangedAttack(player);
            if (shot != null) {
                projectiles.add(shot);
            }
        }

        for (Projectile p : projectiles) {
            p.update(getWidth(), getHeight());
            if (!p.alive) continue;

            if (p.fromPlayer) {
                for (Enemy enemy : currentMap.enemies) {
                    if (!enemy.isDead && p.hits(enemy.x, enemy.y, enemy.width, enemy.height)) {
                        enemy.takeDamage(p.damage);
                        createHitEffect(enemy.x + enemy.width / 2, enemy.y + enemy.height / 2);
                        p.alive = false;
                        break;
                    }
                }
            } else {
                if (p.hits(player.x, player.y, player.width, player.height)) {
                    if (player.takeDamage(p.damage)) {
                        createHitEffect(player.x + player.width / 2, player.y + player.height / 2);
                    }
                    p.alive = false;
                }
            }
        }
        projectiles.removeIf(p -> !p.alive);

if (shiftPressed && System.currentTimeMillis() - lastDashTime >= DASH_COOLDOWN) {
    int dashX = 0, dashY = 0;
    
    if (keys[0]) dashY = -DASH_DISTANCE;
    if (keys[1]) dashY = DASH_DISTANCE; 
    if (keys[2]) dashX = -DASH_DISTANCE; 
    if (keys[3]) dashX = DASH_DISTANCE; 
    
    if (dashX != 0 || dashY != 0) {
        player.x += dashX;
        player.y += dashY;
        
        player.x = Math.max(0, Math.min(getWidth() - player.width, player.x));
        player.y = Math.max(0, Math.min(getHeight() - player.height, player.y));
        
        lastDashTime = System.currentTimeMillis();
        createDashEffect(player.x + player.width/2, player.y + player.height/2);
    }
}
currentMap.enemies.removeIf(enemy -> {
    if (enemy.isDead) {
        boolean leveledUp = player.gainExperience(enemy.expReward);
        showMessage("+" + enemy.expReward + " EXP!");
        createExplosionEffect(enemy.x + enemy.width / 2, enemy.y + enemy.height / 2, enemy.type.color1);

        if (leveledUp) {
            party.refreshActiveStats(player);
            showMessage("Level up! Now level " + player.level + "!");
        }

        if (Math.random() < 0.3) {
            int healAmount = (int)(player.maxHp * 0.2);
            healthPickups.add(new HealthPickup(
                enemy.x + enemy.width / 2, 
                enemy.y + enemy.height / 2,
                healAmount
            ));
        }
        
        if (enemy.type.isBoss) {
            player.finalBossDefeated = true;
            showMessage("VICTORY! " + currentMap.name + " boss defeated!");

            List<CharacterClass> unlocks = Party.BOSS_UNLOCKS.get(currentMap.id);
            if (unlocks != null) {
                for (CharacterClass cls : unlocks) {
                    if (party.unlock(cls, player)) {
                        announceJoin(cls);
                    }
                }
            }
        }
        return true;
    }
    return false;
});

        
for (GameMap.Portal portal : currentMap.portals) {
    if (portal.playerTouching(player) && System.currentTimeMillis() - lastPortalTime >= PORTAL_COOLDOWN) {
        if (portal.requiredLevel > 0 && player.level < portal.requiredLevel) {
            showMessage("Level " + portal.requiredLevel + " required to enter!");
            lastPortalTime = System.currentTimeMillis();
        } else if (portal.requiredFlag != null && !player.hasFlag(portal.requiredFlag)) {
            showMessage("This portal is sealed. Something else needs to happen first.");
            lastPortalTime = System.currentTimeMillis();
        } else if (portal.requireCleared && !allEnemiesDead()) {
            showMessage("Clear the enemies here first!");
            lastPortalTime = System.currentTimeMillis();
        } else {
            player.currentMap = portal.targetMap;
            player.x = portal.targetX;
            player.y = portal.targetY;
            currentMap = maps.get(player.currentMap);
            showMessage("Entered: " + currentMap.name);
            createPortalEffect(portal.targetX, portal.targetY);
            lastPortalTime = System.currentTimeMillis();

            if (currentMap.isCheckpoint) {
                // Bonus recruit waiting at the checkpoint, in addition to the boss unlocks.
                if (party.unlock(CharacterClass.ARCHER, player)) {
                    announceJoin(CharacterClass.ARCHER);
                }
                boolean saved = saveGame();
                showMessage(saved ? "Checkpoint reached - game saved!" : "Checkpoint reached (save failed)");

                if (!player.hasFlag("metElderAtVillage")) {
                    triggerVillageArrival();
                }
            }
        }
        break;
    }
}
        
if (spacePressed && player.canAttack()) {
    player.isAttacking = true;
    player.attackFrame = 0;
    player.lastAttackTime = System.currentTimeMillis();
    
    createAttackEffect(player.x + player.width / 2, player.y + player.height / 2);

    if (player.characterClass.isRanged) {
        double dirX = 0, dirY = 0;
        switch (player.direction) {
            case 0: dirY = 1; break;  // down
            case 1: dirY = -1; break; // up
            case 2: dirX = -1; break; // left
            case 3: dirX = 1; break;  // right
        }
        projectiles.add(new Projectile(
            player.x + player.width / 2.0, player.y + player.height / 2.0,
            dirX, dirY, player.characterClass.projectileSpeed,
            player.attackPower, player.characterClass.fallbackColor
        ));
    } else {
        double range = player.characterClass.attackRange;
        for (Enemy enemy : currentMap.enemies) {
            if (!enemy.isDead) {
                double dist = Math.sqrt(Math.pow(player.x - enemy.x, 2) + Math.pow(player.y - enemy.y, 2));
                boolean inRange = dist < range;
                boolean canHit = dist < POINT_BLANK_RANGE || isFacingEnemy(player, enemy);
                if (inRange && canHit) {
                    enemy.takeDamage(player.attackPower);
                    createHitEffect(enemy.x + enemy.width / 2, enemy.y + enemy.height / 2);
                }
            }
        }
    }
    
    spacePressed = false;
}

if (player.isAttacking) {
    player.attackFrame++;
    if (player.attackFrame >= 4) {
        player.isAttacking = false;
        player.attackFrame = 0;
    }
}

        
        particles.removeIf(p -> p.isDead());
        for (Particle p : particles) {
            p.update();
        }
        healthPickups.removeIf(pickup -> {
    pickup.lifetime--;
    
    if (pickup.playerTouching(player)) {
        player.heal(pickup.healAmount);
        showMessage("+" + pickup.healAmount + " HP!");
        createHealEffect(player.x + player.width / 2, player.y + player.height / 2);
        return true;
    }
    
    return pickup.lifetime <= 0;
});

        repaint();
    }
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        if (player == null) {
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
            g2d.drawString("No game running. Select New Game or Load Game.", 200, 300);
            return;
        }
        
        drawGroundTiles(g2d);

        currentMap.draw(g2d, getWidth(), getHeight());

        drawDecorations(g2d);

        drawNPCs(g2d);

        drawEnemies(g2d);
        
        drawPlayer(g2d);

        for (Projectile p : projectiles) {
            p.draw(g2d);
        }
        
        for (Particle p : particles) {
            p.draw(g2d);
        }
        
        drawHUD(g2d);
        
        if (System.currentTimeMillis() - messageTime < 3000) {
            drawMessage(g2d);
        }
        
        if (player.hp <= 0) {
            drawGameOver(g2d);
        }

        if (activeDialogueNode != null) {
            drawDialogueBox(g2d);
        }
        
        if ((currentMap.id == 9 || currentMap.id == 12) && allEnemiesDead()) {
            drawVictory(g2d);
        }
    }
private void loadAnimations() {
    for (CharacterClass cls : CharacterClass.values()) {
        AnimationSet set = new AnimationSet();
        set.attack1 = safeLoadFrames(cls.spriteFolder + "/attack1");
        set.attack2 = safeLoadFrames(cls.spriteFolder + "/attack2");
        set.run = safeLoadFrames(cls.spriteFolder + "/run");
        set.idle = safeLoadFrames(cls.spriteFolder + "/idle");
        set.damaged = safeLoadFrames(cls.spriteFolder + "/damaged");
        set.die = safeLoadFrames(cls.spriteFolder + "/die");
        classAnimations.put(cls, set);

        int spriteCount = set.idle.length + set.run.length + set.attack1.length;
        if (spriteCount > 0) {
            System.out.println("✓ Loaded animations for " + cls.displayName);
        } else {
            System.out.println("… No sprites found for " + cls.displayName
                + " yet (Resources/" + cls.spriteFolder + "/...) - using fallback silhouette.");
        }
    }

    for (Enemy.EnemyType type : Enemy.EnemyType.values()) {
        if (type.spriteFolder == null) continue; // no art for this type yet, keeps the gradient-blob look
        AnimationSet set = new AnimationSet();
        set.idle = safeLoadFrames(type.spriteFolder + "/idle");
        set.run = safeLoadFrames(type.spriteFolder + "/run");
        set.attack1 = safeLoadFrames(type.spriteFolder + "/attack1");
        set.damaged = safeLoadFrames(type.spriteFolder + "/damaged");
        set.die = safeLoadFrames(type.spriteFolder + "/die");
        enemyAnimations.put(type, set);
        System.out.println("✓ Loaded animations for enemy " + type);
    }

    loadDecorationImages();
}

private void loadDecorationImages() {
    String[] categories = { "bushes", "ruins", "rocks", "pfw_trees", "pfw_bushes", "pfw_rocks" };
    for (String category : categories) {
        File dir = resolveResourceDir("decorations/" + category);
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null) continue;
        for (File f : files) {
            try {
                BufferedImage img = ImageIO.read(f);
                String key = category + "/" + f.getName().replaceFirst("\\.png$", "");
                decorationImages.put(key, img);
            } catch (IOException ex) {
                System.out.println("✗ Error loading decoration " + f.getName() + ": " + ex.getMessage());
            }
        }
    }
    System.out.println("✓ Loaded " + decorationImages.size() + " decoration images");

    String[] tileNames = { "grass", "path" };
    for (String tileName : tileNames) {
        try {
            File f = new File(resolveResourceDir("tiles"), tileName + ".png");
            if (f.exists()) {
                groundTiles.put(tileName, ImageIO.read(f));
            }
        } catch (IOException ex) {
            System.out.println("✗ Error loading tile " + tileName + ": " + ex.getMessage());
        }
    }
    System.out.println("✓ Loaded " + groundTiles.size() + " ground tiles");
}

private BufferedImage[] safeLoadFrames(String relativePath) {
    try {
        return loadFramesFromDir(relativePath);
    } catch (IOException ex) {
        System.out.println("✗ Error loading " + relativePath + ": " + ex.getMessage());
        return new BufferedImage[0];
    }
}

private BufferedImage[] loadFramesFromDir(String relativePath) throws IOException {
    File dir = resolveResourceDir(relativePath);
    File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
    if (files == null || files.length == 0) {
        return new BufferedImage[0];
    }
    Arrays.sort(files, Comparator.comparingInt(f -> extractFrameIndex(f.getName())));
    BufferedImage[] frames = new BufferedImage[files.length];
    for (int i = 0; i < files.length; i++) {
        frames[i] = ImageIO.read(files[i]);
    }
    return frames;
}

// Sprites used to only load correctly if the process's working directory was
// exactly the project root. This falls back to resolving relative to wherever
// the compiled classes actually are, so it also works when launched from an
// IDE run config or a different terminal cwd.
private File resolveResourceDir(String relativePath) {
    File fromWorkingDir = new File("Resources/" + relativePath);
    if (fromWorkingDir.isDirectory()) return fromWorkingDir;

    try {
        File codeLocation = new File(
            GamePanel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File base = codeLocation.isDirectory() ? codeLocation : codeLocation.getParentFile();
        // Walk up a couple levels to find a sibling "Resources" folder (handles
        // both "src/com/" and a build output dir like "bin/" or "out/").
        for (File dir = base; dir != null; dir = dir.getParentFile()) {
            File candidate = new File(dir, "Resources/" + relativePath);
            if (candidate.isDirectory()) return candidate;
        }
    } catch (Exception ignored) {
        // fall through to the working-directory guess below
    }

    return fromWorkingDir;
}

private int extractFrameIndex(String name) {
    int value = 0;
    for (int i = 0; i < name.length(); i++) {
        char c = name.charAt(i);
        if (Character.isDigit(c)) {
            value = value * 10 + (c - '0');
        }
    }
    return value;
}
  
private void drawGroundTiles(Graphics2D g2d) {
    if (currentMap.groundTileKey == null) return;
    BufferedImage tile = groundTiles.get(currentMap.groundTileKey);
    if (tile == null) return;

    int size = 32; // upscaled from the 16px source tile to match the game's pixel scale
    for (int y = 0; y < getHeight(); y += size) {
        for (int x = 0; x < getWidth(); x += size) {
            g2d.drawImage(tile, x, y, size, size, null);
        }
    }

    if (currentMap.pathY >= 0) {
        BufferedImage path = groundTiles.get("path");
        if (path != null) {
            int pathHeight = 72;
            int top = currentMap.pathY - pathHeight / 2;
            for (int x = 0; x < getWidth(); x += size) {
                g2d.drawImage(path, x, top, size, pathHeight, null);
            }
        }
    }
}

private void drawDecorations(Graphics2D g2d) {
    for (GameMap.Decoration deco : currentMap.decorations) {
        if (deco.key.equals("procedural:house")) {
            drawProceduralHouse(g2d, deco.x, deco.y, deco.size);
            continue;
        }
        BufferedImage img = decorationImages.get(deco.key);
        if (img != null) {
            g2d.drawImage(img, deco.x, deco.y, deco.size, deco.size, null);
        }
    }
}

/** Placeholder house (no house sprites exist yet) - a simple body + roof + door + window,
 *  drawn to roughly "size" wide so it reads as a small building behind an NPC. Swap this
 *  out for real sprites the moment house art is available; nothing else needs to change. */
private void drawProceduralHouse(Graphics2D g2d, int x, int y, int size) {
    int w = size;
    int h = (int) (size * 0.8);
    int roofH = (int) (size * 0.45);

    g2d.setColor(new Color(0, 0, 0, 70));
    g2d.fillOval(x - 4, y + h + roofH - 8, w + 8, 14);

    // Walls
    g2d.setColor(new Color(196, 164, 132));
    g2d.fillRect(x, y + roofH, w, h);
    g2d.setColor(new Color(160, 130, 100));
    g2d.drawRect(x, y + roofH, w, h);

    // Roof
    int[] roofX = { x - 6, x + w / 2, x + w + 6 };
    int[] roofY = { y + roofH, y, y + roofH };
    g2d.setColor(new Color(150, 60, 55));
    g2d.fillPolygon(roofX, roofY, 3);
    g2d.setColor(new Color(110, 40, 38));
    g2d.drawPolygon(roofX, roofY, 3);

    // Door
    int doorW = Math.max(6, w / 4);
    int doorH = (int) (h * 0.55);
    g2d.setColor(new Color(90, 60, 40));
    g2d.fillRect(x + w / 2 - doorW / 2, y + roofH + h - doorH, doorW, doorH);

    // Window
    int winSize = Math.max(4, w / 6);
    g2d.setColor(new Color(140, 200, 220));
    g2d.fillRect(x + w - winSize * 2, y + roofH + 6, winSize, winSize);
    g2d.setColor(new Color(90, 60, 40));
    g2d.drawRect(x + w - winSize * 2, y + roofH + 6, winSize, winSize);
}

private void drawNPCs(Graphics2D g2d) {
    for (NPC npc : currentMap.npcs) {
        // Fallback silhouette (no NPC art yet - same pattern as unsprited classes).
        g2d.setColor(new Color(0, 0, 0, 90));
        g2d.fillOval(npc.x + 4, npc.y + npc.height - 4, npc.width - 4, 8);

        g2d.setColor(npc.fallbackColor);
        g2d.fillRoundRect(npc.x, npc.y, npc.width, npc.height, 10, 10);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 12));
        String initial = npc.name.substring(0, 1);
        g2d.drawString(initial, npc.x + npc.width / 2 - 4, npc.y + npc.height / 2 + 4);

        g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
        FontMetrics fm = g2d.getFontMetrics();
        int nameW = fm.stringWidth(npc.name);
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRoundRect(npc.x + npc.width / 2 - nameW / 2 - 4, npc.y - 16, nameW + 8, 14, 6, 6);
        g2d.setColor(Color.WHITE);
        g2d.drawString(npc.name, npc.x + npc.width / 2 - nameW / 2, npc.y - 6);

        if (activeDialogueNode == null && player != null && npc.playerInRange(player, TALK_RANGE)) {
            String prompt = "[E] Talk";
            int pw = fm.stringWidth(prompt);
            g2d.setColor(new Color(255, 230, 120));
            g2d.drawString(prompt, npc.x + npc.width / 2 - pw / 2, npc.y - 22);
        }
    }
}

private void drawDialogueBox(Graphics2D g2d) {
    if (activeDialogueNode == null || activeNpc == null) return;

    int boxX = 40, boxY = 400, boxW = getWidth() - 80, boxH = 180;

    g2d.setColor(new Color(15, 15, 25, 235));
    g2d.fillRoundRect(boxX, boxY, boxW, boxH, 14, 14);
    g2d.setColor(new Color(200, 180, 140));
    g2d.setStroke(new BasicStroke(2));
    g2d.drawRoundRect(boxX, boxY, boxW, boxH, 14, 14);

    g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
    g2d.setColor(new Color(255, 220, 150));
    g2d.drawString(activeNpc.name, boxX + 16, boxY + 22);

    g2d.setFont(new Font("SansSerif", Font.PLAIN, 13));
    g2d.setColor(Color.WHITE);
    List<String> lines = wrapText(activeDialogueNode.text, g2d.getFontMetrics(), boxW - 32);
    int lineY = boxY + 44;
    for (String line : lines) {
        g2d.drawString(line, boxX + 16, lineY);
        lineY += 16;
    }

    List<DialogueChoice> available = new ArrayList<>();
    for (DialogueChoice c : activeDialogueNode.choices) {
        if (c.isAvailable(player)) available.add(c);
    }

    int choiceY = boxY + boxH - 14 - (available.size() * 16);
    if (choiceY < lineY + 6) choiceY = lineY + 10;
    g2d.setColor(new Color(140, 200, 255));
    for (int i = 0; i < available.size(); i++) {
        g2d.drawString((i + 1) + ". " + available.get(i).label, boxX + 16, choiceY + i * 18);
    }
}

private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
    List<String> result = new ArrayList<>();
    for (String paragraph : text.split("\n")) {
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(candidate) > maxWidth && line.length() > 0) {
                result.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        result.add(line.toString());
    }
    return result;
}

private void drawEnemies(Graphics2D g2d) {
    for (Enemy enemy : currentMap.enemies) {
        AnimationSet anim = enemyAnimations.get(enemy.type);
        BufferedImage frame = null;

        if (anim != null) {
            if (enemy.isFlashingHurt() && anim.damaged.length > 0) {
                frame = anim.damaged[(frameCount / 3) % anim.damaged.length];
            } else if (enemy.isAttackAnimating() && anim.attack1.length > 0) {
                frame = anim.attack1[(frameCount / 3) % anim.attack1.length];
            } else if (enemy.isMoving && anim.run.length > 0) {
                frame = anim.run[(frameCount / 6) % anim.run.length];
            } else if (anim.idle.length > 0) {
                frame = anim.idle[(frameCount / 10) % anim.idle.length];
            }
        }

        enemy.draw(g2d, frame);
    }
}

private void drawPlayer(Graphics2D g2d) {
    // Shadow
    g2d.setColor(new Color(0, 0, 0, 100));
    g2d.fillOval(player.x + 4, player.y + player.height - 4, player.width - 4, 8);
    
    // Draw current animation frame
    AnimationSet anim = classAnimations.get(player.characterClass);
    BufferedImage currentFrame = null;

    if (anim != null) {
        if (player.hp <= 0 && anim.die.length > 0) {
            currentFrame = anim.die[Math.min(deathFrame, anim.die.length - 1)];
        } else if (player.isAttacking) {
            BufferedImage[] swing = (player.characterClass.useAltSwing && anim.attack2.length > 0)
                ? anim.attack2 : anim.attack1;
            if (swing.length > 0) {
                int frame = player.attackFrame % swing.length;
                currentFrame = swing[frame];
            }
        } else if (player.isInvincible() && anim.damaged.length > 0) {
            int frame = (frameCount / 4) % anim.damaged.length;
            currentFrame = anim.damaged[frame];
        } else if ((keys[0] || keys[1] || keys[2] || keys[3]) && anim.run.length > 0) {
            int frame = playerAnimFrame % anim.run.length;
            currentFrame = anim.run[frame];
        } else if (anim.idle.length > 0) {
            int frame = (frameCount / 8) % anim.idle.length;
            currentFrame = anim.idle[frame];
        }
    }

    // Flicker while invincible so a hit is clearly felt, even without the damage sprite.
    boolean hideForFlicker = player.hp > 0 && player.isInvincible()
        && ((System.currentTimeMillis() / 100) % 2 == 0);

    if (!hideForFlicker) {
        if (currentFrame != null) {
            if (player.direction == 2) {
                // Source art faces right by default - mirror it for left-facing movement/attacks.
                g2d.drawImage(currentFrame, player.x + player.width, player.y, -player.width, player.height, null);
            } else {
                g2d.drawImage(currentFrame, player.x, player.y, player.width, player.height, null);
            }
        } else {
            // Fallback silhouette in the class's color until real sprites are added.
            g2d.setColor(player.characterClass.fallbackColor);
            g2d.fillRoundRect(player.x, player.y, player.width, player.height, 10, 10);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            String initial = player.characterClass.displayName.substring(0, 1);
            g2d.drawString(initial, player.x + player.width / 2 - 4, player.y + player.height / 2 + 4);
        }
    }
    
    // HP bar
    int barWidth = player.width + 8;
    int barX = player.x - 4;
    int barY = player.y - 14;
    g2d.setColor(new Color(0, 0, 0, 180));
    g2d.fillRoundRect(barX, barY, barWidth, 8, 4, 4);
    g2d.setColor(new Color(200, 50, 50));
    g2d.fillRoundRect(barX + 1, barY + 1, barWidth - 2, 6, 3, 3);
    g2d.setColor(new Color(100, 255, 100));
    int hpWidth = (int)((double)player.hp / player.maxHp * (barWidth - 2));
    g2d.fillRoundRect(barX + 1, barY + 1, hpWidth, 6, 3, 3);
}


    
    private void drawHUD(Graphics2D g2d) {
        GradientPaint hudGradient = new GradientPaint(
            0, 0, new Color(0, 0, 0, 200),
            0, 70, new Color(0, 0, 0, 150)
        );
        g2d.setPaint(hudGradient);
        g2d.fillRoundRect(5, 5, getWidth() - 10, 65, 15, 15);
        
        g2d.setColor(new Color(100, 150, 255, 150));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(5, 5, getWidth() - 10, 65, 15, 15);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2d.drawString("HP", 20, 28);
        
        int hpBarWidth = 150;
        g2d.setColor(new Color(100, 0, 0));
        g2d.fillRoundRect(50, 15, hpBarWidth, 18, 9, 9);
        
        GradientPaint hpGradient = new GradientPaint(
            50, 15, new Color(150, 255, 150),
            50, 33, new Color(50, 200, 50)
        );
        g2d.setPaint(hpGradient);
        int currentHpWidth = (int)((double)player.hp / player.maxHp * hpBarWidth);
        g2d.fillRoundRect(50, 15, currentHpWidth, 18, 9, 9);
        
        g2d.setColor(Color.WHITE);
        String hpText = player.hp + "/" + player.maxHp;
        g2d.drawString(hpText, 55, 28);
        
        g2d.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2d.drawString("Level " + player.level, 20, 50);
        
        int expBarWidth = 180;
        g2d.setColor(new Color(50, 50, 100));
        g2d.fillRoundRect(20, 55, expBarWidth, 10, 5, 5);
        
        GradientPaint expGradient = new GradientPaint(
            20, 55, new Color(255, 200, 100),
            20, 65, new Color(255, 150, 50)
        );
        g2d.setPaint(expGradient);
        int expWidth = (int)((double)player.experience / (player.level * 100) * expBarWidth);
        g2d.fillRoundRect(20, 55, expWidth, 10, 5, 5);
        
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2d.drawString("EXP: " + player.experience + "/" + (player.level * 100), 25, 63);
        
        g2d.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2d.drawString("Map: " + currentMap.name, 250, 28);
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int alive = countAliveEnemies();
        g2d.drawString("Enemies: " + alive + "/" + currentMap.enemies.size(), 250, 48);
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2d.setColor(new Color(200, 200, 200));
        g2d.drawString("[↑↓←→] Move  [Space] Attack  [E] Talk  [S] Save  [ESC] Menu", 420, 45);

        drawPartyStrip(g2d);
    }

    private void drawPartyStrip(Graphics2D g2d) {
        if (party == null) return;

        int slotSize = 26;
        int gap = 6;
        int startX = 10;
        int y = 78;

        g2d.setFont(new Font("SansSerif", Font.BOLD, 11));

        for (int i = 0; i < party.unlocked.size(); i++) {
            CharacterClass cls = party.unlocked.get(i);
            int x = startX + i * (slotSize + gap);
            boolean isActive = cls == party.active;

            g2d.setColor(isActive ? cls.fallbackColor : new Color(40, 40, 40, 200));
            g2d.fillRoundRect(x, y, slotSize, slotSize, 6, 6);

            g2d.setColor(isActive ? Color.WHITE : new Color(150, 150, 150));
            g2d.setStroke(new BasicStroke(isActive ? 2 : 1));
            g2d.drawRoundRect(x, y, slotSize, slotSize, 6, 6);

            g2d.setColor(Color.WHITE);
            g2d.drawString(String.valueOf(i + 1), x + 4, y + 12);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g2d.drawString(cls.displayName.substring(0, Math.min(3, cls.displayName.length())), x + 3, y + 22);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 11));
        }

        if (party.unlocked.size() < CharacterClass.values().length) {
            int x = startX + party.unlocked.size() * (slotSize + gap);
            g2d.setColor(new Color(60, 60, 60, 150));
            g2d.fillRoundRect(x, y, slotSize, slotSize, 6, 6);
            g2d.setColor(new Color(120, 120, 120));
            g2d.drawString("?", x + 9, y + 18);
        }
    }
    
    private void drawMessage(Graphics2D g2d) {
        int alpha = (int)(255 * (1 - (System.currentTimeMillis() - messageTime) / 3000.0));
        g2d.setColor(new Color(0, 0, 0, Math.min(alpha, 200)));
        g2d.fillRoundRect(150, 250, 500, 60, 20, 20);
        
        g2d.setColor(new Color(100, 200, 255, 180));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(150, 250, 500, 60, 20, 20);
        
        g2d.setColor(new Color(255, 255, 150, alpha));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
        FontMetrics fm = g2d.getFontMetrics();
        int textX = 400 - fm.stringWidth(message) / 2;
        g2d.drawString(message, textX, 285);
    }
    
    private void drawGameOver(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 220));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        for (int i = 5; i > 0; i--) {
            g2d.setColor(new Color(255, 0, 0, 30 * (6 - i)));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 56 + i * 2));
            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth("GAME OVER")) / 2;
            g2d.drawString("GAME OVER", x, 270);
        }
        
        g2d.setColor(Color.RED);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 56));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth("GAME OVER")) / 2;
        g2d.drawString("GAME OVER", x, 270);
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
        g2d.setColor(Color.WHITE);
        String sub = "Press ESC to return to menu";
        x = (getWidth() - g2d.getFontMetrics().stringWidth(sub)) / 2;
        g2d.drawString(sub, x, 320);
    }
    
    private void drawVictory(Graphics2D g2d) {
        g2d.setColor(new Color(0, 0, 0, 230));
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        int pulse = (int)(Math.sin(frameCount * 0.1) * 5);
        
        for (int i = 5; i > 0; i--) {
            g2d.setColor(new Color(255, 215, 0, 40 * (6 - i)));
            g2d.setFont(new Font("SansSerif", Font.BOLD, 60 + i * 2 + pulse));
            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth("VICTORY!")) / 2;
            g2d.drawString("VICTORY!", x, 230);
        }
        
        g2d.setColor(new Color(255, 215, 0));
        g2d.setFont(new Font("SansSerif", Font.BOLD, 60 + pulse));
        FontMetrics fm = g2d.getFontMetrics();
        int x = (getWidth() - fm.stringWidth("VICTORY!")) / 2;
        g2d.drawString("VICTORY!", x, 230);
        
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g2d.setColor(Color.WHITE);
        String[] lines = {
            "You have completed Dreambound!",
            "Final Level: " + player.level,
            "",
            "Press ESC to return to menu"
        };
        
        for (int i = 0; i < lines.length; i++) {
            x = (getWidth() - g2d.getFontMetrics().stringWidth(lines[i])) / 2;
            g2d.drawString(lines[i], x, 300 + i * 30);
        }
    }
    
    private boolean isFacingEnemy(Player player, Enemy enemy) {
        double dx = (enemy.x + enemy.width / 2.0) - (player.x + player.width / 2.0);
        double dy = (enemy.y + enemy.height / 2.0) - (player.y + player.height / 2.0);
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) return true;
        dx /= len;
        dy /= len;

        double facingX = 0, facingY = 0;
        switch (player.direction) {
            case 0: facingY = 1; break;  // down
            case 1: facingY = -1; break; // up
            case 2: facingX = -1; break; // left
            case 3: facingX = 1; break;  // right
        }

        double dot = dx * facingX + dy * facingY;
        return dot > 0.3; // roughly a 107-degree forward cone
    }

    private int countAliveEnemies() {
        int count = 0;
        for (Enemy enemy : currentMap.enemies) {
            if (!enemy.isDead) count++;
        }
        return count;
    }
    
    private boolean allEnemiesDead() {
        return countAliveEnemies() == 0;
    }
    
    private void showMessage(String msg) {
        this.message = msg;
        this.messageTime = System.currentTimeMillis();
    }
    
    private void createAttackEffect(int x, int y) {
        for (int i = 0; i < 8; i++) {
            double angle = Math.random() * Math.PI * 2;
            particles.add(new Particle(x, y, angle, 5, new Color(255, 255, 150), 20));
        }
    }
    
    private void createHitEffect(int x, int y) {
        for (int i = 0; i < 12; i++) {
            double angle = Math.random() * Math.PI * 2;
            particles.add(new Particle(x, y, angle, 4, new Color(255, 100, 100), 25));
        }
    }
    
    private void createExplosionEffect(int x, int y, Color color) {
        for (int i = 0; i < 20; i++) {
            double angle = Math.random() * Math.PI * 2;
            particles.add(new Particle(x, y, angle, 6, color, 30));
        }
    }
private void createHealEffect(int x, int y) {
    for (int i = 0; i < 20; i++) {
        double angle = Math.random() * Math.PI * 2;
        double speed = Math.random() * 3 + 1;
        particles.add(new Particle(
            x, y,
            (int)(Math.cos(angle) * speed),
            (int)(Math.sin(angle) * speed - 2),
            new Color(100, 255, 100),
            30
        ));
    }
}


    
    private void createPortalEffect(int x, int y) {
        for (int i = 0; i < 15; i++) {
            double angle = Math.random() * Math.PI * 2;
            particles.add(new Particle(x, y, angle, 3, new Color(150, 150, 255), 35));
        }
    }
    private void createDashEffect(int x, int y) {
    for (int i = 0; i < 25; i++) {
        double angle = Math.random() * Math.PI * 2;
        particles.add(new Particle(x, y, angle, 5, new Color(150, 200, 255), 20));
    }
}

private BufferedImage getSprite(BufferedImage sheet, int col, int row, int width, int height) {
    if (sheet == null) return null;
    return sheet.getSubimage(col * width, row * height, width, height);
}
    
    private static class Particle {
        double x, y;
        double dx, dy;
        Color color;
        int life;
        int maxLife;
        int size;
        
        Particle(int x, int y, double angle, int speed, Color color, int life) {
            this.x = x;
            this.y = y;
            this.dx = Math.cos(angle) * speed;
            this.dy = Math.sin(angle) * speed;
            this.color = color;
            this.life = life;
            this.maxLife = life;
            this.size = 4;
        }
        
        void update() {
            x += dx;
            y += dy;
            dy += 0.2;
            life--;
        }
        
        boolean isDead() {
            return life <= 0;
        }
        
        void draw(Graphics2D g2d) {
            int alpha = (int)(255 * ((double)life / maxLife));
            g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g2d.fillOval((int)x - size/2, (int)y - size/2, size, size);
        }
    }

    private static class HealthPickup {
    int x, y, size = 20;
    int healAmount;
    int lifetime = 300; // 5 seconds (300 frames at 60fps)
    
    HealthPickup(int x, int y, int healAmount) {
        this.x = x;
        this.y = y;
        this.healAmount = healAmount;
    }
    
    boolean playerTouching(Player player) {
        return Math.abs(player.x + player.width/2 - x) < 30 &&
               Math.abs(player.y + player.height/2 - y) < 30;
    }
    
    void draw(Graphics2D g2d) {
        int pulse = (int)(Math.sin(lifetime * 0.2) * 3);
        
        // Red cross
        g2d.setColor(new Color(255, 50, 50, 200));
        g2d.fillRoundRect(x - size/2, y - size/3, size, size*2/3, 5, 5);
        g2d.fillRoundRect(x - size/3, y - size/2, size*2/3, size, 5, 5);
        
        // White center
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(x - size/4, y - size/6, size/2, size/3, 3, 3);
        g2d.fillRoundRect(x - size/6, y - size/4, size/3, size/2, 3, 3);
        
        // Glow
        g2d.setColor(new Color(255, 100, 100, 50));
        g2d.fillOval(x - size/2 - pulse, y - size/2 - pulse,
                     size + pulse*2, size + pulse*2);
    }
}
}