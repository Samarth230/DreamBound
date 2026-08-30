package com;

import java.awt.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameMap implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public int id;
    public String name;
    public List<Enemy> enemies;
    public List<Portal> portals;
    public List<Decoration> decorations = new ArrayList<>();
    public List<NPC> npcs = new ArrayList<>();
    public Color groundColor1;
    public Color groundColor2;
    public Color accentColor;
    public boolean isCheckpoint = false;
    public String groundTileKey = null; // null = old gradient/checker look (cave/ruins/dark themes)
    public int pathY = -1; // -1 = no path strip drawn

    public GameMap(int id, String name, Color c1, Color c2, Color accent) {
        this.id = id;
        this.name = name;
        this.groundColor1 = c1;
        this.groundColor2 = c2;
        this.accentColor = accent;
        this.enemies = new ArrayList<>();
        this.portals = new ArrayList<>();
    }
    
    public void addEnemy(Enemy enemy) {
        enemies.add(enemy);
    }
    
    public void addPortal(Portal portal) {
        portals.add(portal);
    }

    public void addNpc(NPC npc) {
        npcs.add(npc);
    }

    public void addDecoration(String key, int x, int y, int size) {
        decorations.add(new Decoration(key, x, y, size));
    }

    /** Scatters decorations from a theme's image pool at random (seeded, so it's stable across plays). */
    private void scatterDecorations(String category, String[] keys, int count, long seed) {
        scatterDecorations(category, keys, count, seed, 24, 44);
    }

    private void scatterDecorations(String category, String[] keys, int count, long seed, int minSize, int maxSize) {
        Random rng = new Random(seed);
        for (int i = 0; i < count; i++) {
            String key = category + "/" + keys[rng.nextInt(keys.length)];
            int x = 20 + rng.nextInt(760);
            int y = 90 + rng.nextInt(440); // keep clear of the HUD strip at the top
            int size = minSize + rng.nextInt(Math.max(1, maxSize - minSize));
            addDecoration(key, x, y, size);
        }
    }
    
    public void update(Player player, int panelWidth, int panelHeight) {
        for (Enemy enemy : enemies) {
            enemy.update(player, panelWidth, panelHeight);
        }
    }
    
    // Note: enemies, decorations, and the real ground-tile texture (when
    // groundTileKey is set) are drawn by GamePanel - it owns the sprite
    // caches. This only draws the gradient fallback (for cave/ruins/dark
    // levels with no matching tile art yet) and portals.
    public void draw(Graphics g, int panelWidth, int panelHeight) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (groundTileKey == null) {
            GradientPaint bgGradient = new GradientPaint(
                0, 0, groundColor1,
                0, panelHeight, groundColor2
            );
            g2d.setPaint(bgGradient);
            g2d.fillRect(0, 0, panelWidth, panelHeight);
            
            int tile = 32;
            for (int y = 0; y < panelHeight; y += tile) {
                for (int x = 0; x < panelWidth; x += tile) {
                    boolean checker = ((x / tile) + (y / tile)) % 2 == 0;
                    if (checker) {
                        g2d.setColor(new Color(
                            accentColor.getRed(),
                            accentColor.getGreen(),
                            accentColor.getBlue(),
                            30
                        ));
                        g2d.fillRect(x + 2, y + 2, tile - 4, tile - 4);
                    }
                }
            }
        }
        
        for (Portal portal : portals) {
            portal.draw(g2d);
        }
    }

    /** A static decoration image (bush/ruin/rock) placed on the map. Purely visual, no collision. */
    public static class Decoration implements Serializable {
        private static final long serialVersionUID = 1L;
        public String key; // e.g. "bushes/Bush_simple1_1"
        public int x, y, size;

        public Decoration(String key, int x, int y, int size) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }
    
    public static class Portal implements Serializable {
        private static final long serialVersionUID = 1L;
        public int x, y, width = 56, height = 56;
        public int targetMap;
        public int targetX, targetY;
        public Color color;
        public int requiredLevel = 0; // 0 = no level gate
        public String requiredFlag = null; // null = no flag gate
        public boolean requireCleared = false; // true = all enemies on this map must be dead
        private int animFrame = 0;
        
        public Portal(int x, int y, int targetMap, int targetX, int targetY, Color color) {
            this.x = x;
            this.y = y;
            this.targetMap = targetMap;
            this.targetX = targetX;
            this.targetY = targetY;
            this.color = color;
        }

        public Portal requireLevel(int level) {
            this.requiredLevel = level;
            return this;
        }

        public Portal requireFlag(String flag) {
            this.requiredFlag = flag;
            return this;
        }

        public Portal requireCleared() {
            this.requireCleared = true;
            return this;
        }
        
        public boolean playerTouching(Player player) {
            return player.x < x + width && player.x + player.width > x &&
                   player.y < y + height && player.y + player.height > y;
        }
        
        public void draw(Graphics2D g2d) {
            animFrame++;
            int glowPulse = (int)(Math.sin(animFrame * 0.08) * 4);

            int pillarW = 14;
            int archHeight = 18;
            int gateTop = y - 20;
            int gateBottom = y + height + 6;
            int gateHeight = gateBottom - gateTop;

            Color stoneDark = new Color(60, 55, 52);
            Color stone = new Color(90, 85, 80);
            Color stoneLight = new Color(120, 112, 105);

            // Ground shadow
            g2d.setColor(new Color(0, 0, 0, 90));
            g2d.fillOval(x - 6, gateBottom - 6, width + 12, 14);

            // Pillars
            g2d.setColor(stoneDark);
            g2d.fillRoundRect(x - pillarW, gateTop, pillarW, gateHeight, 6, 6);
            g2d.fillRoundRect(x + width, gateTop, pillarW, gateHeight, 6, 6);
            g2d.setColor(stone);
            g2d.fillRoundRect(x - pillarW + 2, gateTop + 2, pillarW - 4, gateHeight - 4, 5, 5);
            g2d.fillRoundRect(x + width + 2, gateTop + 2, pillarW - 4, gateHeight - 4, 5, 5);

            // Arch connecting the pillars
            g2d.setColor(stoneDark);
            g2d.fillRoundRect(x - pillarW, gateTop - archHeight, width + pillarW * 2, archHeight + 10, 10, 10);
            g2d.setColor(stoneLight);
            g2d.fillRoundRect(x - pillarW + 3, gateTop - archHeight + 3, width + pillarW * 2 - 6, archHeight + 4, 8, 8);

            // Carved texture lines on the pillars
            g2d.setColor(stoneDark);
            for (int i = 0; i < 3; i++) {
                int ly = gateTop + 14 + i * ((gateHeight - 28) / 3);
                g2d.drawLine(x - pillarW + 3, ly, x - 3, ly);
                g2d.drawLine(x + width + 3, ly, x + width + pillarW - 3, ly);
            }

            // Glowing doorway between the pillars, tinted by this gate's color
            GradientPaint glow = new GradientPaint(
                x, gateTop, new Color(color.getRed(), color.getGreen(), color.getBlue(), 220),
                x, gateBottom, color.darker()
            );
            g2d.setPaint(glow);
            g2d.fillRoundRect(x + 2, gateTop + 6, width - 4, gateHeight - 10, 10, 30);

            g2d.setColor(new Color(255, 255, 255, 90 + glowPulse * 6));
            g2d.fillRoundRect(x + width / 2 - 10 - glowPulse, gateTop + 14, 20 + glowPulse * 2, gateHeight - 26, 8, 20);

            // Drifting light motes rising through the doorway
            g2d.setColor(new Color(255, 255, 255, 160));
            for (int i = 0; i < 3; i++) {
                double t = ((animFrame * 0.6 + i * 40) % 100) / 100.0;
                int mx = x + width / 2 - 8 + (i * 7) - 7;
                int my = (int) (gateBottom - t * gateHeight);
                g2d.fillOval(mx, my, 3, 3);
            }

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics fm = g2d.getFontMetrics();
            String label = "Gate";
            int textX = x + (width - fm.stringWidth(label)) / 2;
            g2d.drawString(label, textX, gateBottom + 14);
        }
    }

    // ============================================================
    // Campaign v2 - 14 maps (id 0-13).
    //
    //   0  Village Outskirts    (start, confused, no answers yet)
    //   1  Dark Forest
    //   2  Ancient Cave
    //   3  Ruins Approach
    //   4  Raku's Lair          BOSS 1 (Raku, giant slime) -> unlocks Rogue+Mage
    //   5  Sanctuary Village    CHECKPOINT - Elder Wren's scripted greeting,
    //                           houses, answers finally start here -> unlocks Archer
    //   6  Overgrown Thicket    (bridge out of the village)
    //   7  The Crossroads       FORK: one gate to the alt path, one to the main path
    //   8  Bush Hollow          (main path)
    //   9  The Last Vigil       BOSS 2 (huge kiting Wraith) -> unlocks Tank
    //
    //  Alt path (branches off the Crossroads' alt gate, not part of 0-9):
    //  10  Rocky Pass
    //  11  Stone Wastes
    //  12  Ruined Throne        ALT BOSS (a different threat, no class unlock)
    //
    //  Side content (unrelated branch, unlocked via Elder Wren's quest at the village):
    //  13  The Sunken Archive
    // ============================================================

    private static final Color PORTAL_BACK = new Color(150, 255, 150);
    private static final Color PORTAL_FWD = new Color(120, 170, 255);
    private static final Color PORTAL_BOSS = new Color(255, 80, 80);
    private static final Color PORTAL_HIDDEN = new Color(170, 100, 220);
    private static final Color PORTAL_ALT = new Color(230, 180, 70);

    // Bushes/rocks/trees use the newer, better-proportioned pack (obj_XXX
    // files were auto-extracted and curated from Object.png). Ruins have no
    // equivalent in that pack yet, so those still use the original craftpix set.
    private static final String[] BUSH_KEYS = {
        "obj_040", "obj_055", "obj_056", "obj_057", "obj_058",
        "obj_059", "obj_060", "obj_061", "obj_062", "obj_063"
    };
    private static final String[] TREE_KEYS = {
        "obj_000", "obj_001", "obj_002", "obj_003", "obj_004"
    };
    private static final String[] RUIN_KEYS = {
        "Brown-gray_ruins1", "Brown-gray_ruins3", "Brown-gray_ruins4", "Brown_ruins2",
        "Brown_ruins5", "White_ruins2", "White_ruins4", "Blue-gray_ruins1", "Sand_ruins3"
    };
    private static final String[] ROCK_KEYS = {
        "obj_080", "obj_081", "obj_084", "obj_087", "obj_088",
        "obj_089", "obj_090", "obj_093", "obj_094", "obj_095"
    };

    // --- Level 1 (id 0) - confused, no NPCs yet ---
    public static GameMap createStartingArea() {
        GameMap map = new GameMap(0, "Village Outskirts", 
            new Color(110, 220, 110), new Color(80, 180, 80),
            new Color(90, 200, 90));
        map.groundTileKey = "grass";
        map.pathY = 270;
        
        map.addEnemy(new Enemy(200, 200, Enemy.EnemyType.SLIME));
        map.addEnemy(new Enemy(450, 300, Enemy.EnemyType.SLIME));
        map.addEnemy(new Enemy(600, 180, Enemy.EnemyType.SLIME));
        map.addEnemy(new Enemy(300, 450, Enemy.EnemyType.SLIME));
        map.addEnemy(new Enemy(650, 420, Enemy.EnemyType.SLIME));
        map.addEnemy(new Enemy(150, 300, Enemy.EnemyType.SLIME));
        map.scatterDecorations("pfw_bushes", BUSH_KEYS, 8, 100, 44, 76);
        map.scatterDecorations("pfw_trees", TREE_KEYS, 5, 900, 70, 110);
        
        map.addPortal(new Portal(740, 270, 1, 50, 300, PORTAL_FWD));
        
        return map;
    }
    
    // --- Level 2 (id 1) ---
    public static GameMap createForest() {
        GameMap map = new GameMap(1, "Dark Forest", 
            new Color(50, 120, 50), new Color(30, 80, 30),
            new Color(40, 100, 40));
        map.groundTileKey = "grass";
        map.pathY = 270;
        
        map.addEnemy(new Enemy(300, 200, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(500, 400, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(200, 450, Enemy.EnemyType.SLIME));
        map.addEnemy(new Enemy(600, 250, Enemy.EnemyType.SLIME));
        map.addEnemy(new Enemy(400, 150, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(150, 200, Enemy.EnemyType.SLIME));
        map.addEnemy(new Enemy(650, 450, Enemy.EnemyType.GOBLIN));
        map.scatterDecorations("pfw_bushes", BUSH_KEYS, 10, 101, 44, 76);
        map.scatterDecorations("pfw_trees", TREE_KEYS, 9, 901, 70, 120);
        
        map.addPortal(new Portal(10, 270, 0, 700, 300, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 2, 50, 300, PORTAL_FWD));
        
        return map;
    }
    
    // --- Level 3 (id 2) ---
    public static GameMap createCave() {
        GameMap map = new GameMap(2, "Ancient Cave", 
            new Color(70, 70, 80), new Color(40, 40, 50),
            new Color(60, 60, 70));
        
        map.addEnemy(new Enemy(300, 300, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(500, 200, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(400, 450, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(250, 150, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(600, 400, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(150, 400, Enemy.EnemyType.SLIME));
        map.scatterDecorations("pfw_rocks", ROCK_KEYS, 8, 102);
        
        map.addPortal(new Portal(10, 270, 1, 700, 300, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 3, 50, 300, PORTAL_FWD));
        
        return map;
    }

    // --- Level 4 (id 3) ---
    public static GameMap createRuinsApproach() {
        GameMap map = new GameMap(3, "Ruins Approach",
            new Color(150, 130, 90), new Color(100, 85, 60),
            new Color(130, 110, 75));

        map.addEnemy(new Enemy(280, 220, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(520, 380, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(400, 180, Enemy.EnemyType.SLIME_ELITE));
        map.addEnemy(new Enemy(600, 250, Enemy.EnemyType.SLIME_ELITE));
        map.addEnemy(new Enemy(150, 350, Enemy.EnemyType.SKELETON));
        map.scatterDecorations("ruins", RUIN_KEYS, 8, 103);

        map.addPortal(new Portal(10, 270, 2, 700, 300, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 4, 370, 500, PORTAL_BOSS).requireLevel(3));

        return map;
    }

    // --- Level 5 (id 4) - BOSS 1: Raku, a giant slime -> unlocks Rogue + Mage ---
    public static GameMap createRakusLair() {
        GameMap map = new GameMap(4, "Raku's Lair",
            new Color(100, 40, 90), new Color(60, 20, 55),
            new Color(80, 30, 72));
        
        map.addEnemy(new Enemy(350, 140, Enemy.EnemyType.RAKU));
        map.scatterDecorations("ruins", RUIN_KEYS, 5, 104);
        
        map.addPortal(new Portal(350, 520, 3, 700, 300, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 5, 50, 300, PORTAL_FWD).requireCleared());
        
        return map;
    }

    // --- Level 6 (id 5) - CHECKPOINT: Sanctuary Village -> unlocks Archer ---
    public static GameMap createSanctuaryVillage() {
        GameMap map = new GameMap(5, "Sanctuary Village",
            new Color(230, 200, 140), new Color(190, 160, 100),
            new Color(210, 180, 120));
        map.isCheckpoint = true;
        map.groundTileKey = "path";
        map.pathY = -1; // the whole floor already reads as "path" here

        // Placeholder houses (procedural - swap for real sprites once you have
        // them, nothing else needs to change) - one per NPC, "different houses."
        map.addDecoration("procedural:house", 90, 300, 110);
        map.addDecoration("procedural:house", 470, 330, 100);
        map.addDecoration("procedural:house", 610, 150, 95);
        map.scatterDecorations("pfw_bushes", BUSH_KEYS, 6, 110, 40, 64);
        map.scatterDecorations("pfw_trees", TREE_KEYS, 6, 910, 80, 120);

        map.addNpc(buildElderWren());
        map.addNpc(buildScholarYira());
        map.addNpc(buildOldSoldierPetra());

        map.addPortal(new Portal(10, 270, 4, 700, 300, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 6, 50, 300, PORTAL_FWD));
        // Hidden waystone to the Sunken Archive - opens once Elder Wren's quest is accepted.
        map.addPortal(new Portal(400, 480, 13, 400, 150, PORTAL_HIDDEN).requireFlag("acceptedArchiveQuest"));

        return map;
    }

    private static NPC buildElderWren() {
        NPC npc = new NPC("Elder Wren", 150, 380, new Color(200, 170, 110), null);

        npc.addNode(new DialogueNode("start",
            "Word travels fast when a stranger clears Raku's Lair alone.\n" +
            "Ask what you need to - you've earned that much, after everything between here and the outskirts.")
            .choice(new DialogueChoice("Where am I?", "askWorld"))
            .choice(new DialogueChoice("Why can't I remember how I got here?", "askSelf"))
            .choice(new DialogueChoice("What's still out there, past this village?", "askKeeps"))
            .choice(new DialogueChoice("Is there anything I can do to help?", "askArchiveOffer")
                .requiresAbsent("acceptedArchiveQuest"))
            .choice(new DialogueChoice("How is the Sunken Archive going?", "alreadyOnQuest")
                .requiresPresent("acceptedArchiveQuest"))
            .choice(new DialogueChoice("I'll get going.", "")));

        npc.addNode(new DialogueNode("askWorld",
            "This is Dreambound. Or what's left of it holding together.\n" +
            "Two keeps once anchored the barrier between our world and... elsewhere. " +
            "Both have gone quiet this past year. Raku's Lair was one. It's why you're standing here.")
            .choice(new DialogueChoice("Back to my questions.", "start")));

        npc.addNode(new DialogueNode("askSelf",
            "No one 'falls out of the sky' here, child. Not naturally.\n" +
            "The old stories call it being dream-bound - pulled in when the barrier " +
            "thins enough for someone from elsewhere to slip through. It's happened before. " +
            "Every time, it meant something was already going wrong.")
            .choice(new DialogueChoice("Back to my questions.", "start")));

        npc.addNode(new DialogueNode("askKeeps",
            "The second keep, the Ruined Throne, and further still, something worse - " +
            "wraith-shapes, thick enough now to call an army. Whatever commands them isn't what built either keep.\n" +
            "Clear what's ahead, and maybe the seam holds. Maybe it even tells us how to send you home.")
            .choice(new DialogueChoice("Back to my questions.", "start")));

        npc.addNode(new DialogueNode("askArchiveOffer",
            "...Maybe. There's an old records-hall, the Sunken Archive, sealed up beneath this very village. " +
            "If anything survived that explains what's actually happening to the seam, it's down there. " +
            "It's guarded - I won't pretend otherwise.\n" +
            "I can open the old waystone for you, right here. Your call.")
            .choice(new DialogueChoice("I'll go. Open the waystone.", "questAccepted")
                .setFlag("acceptedArchiveQuest"))
            .choice(new DialogueChoice("Not yet.", "start")));

        npc.addNode(new DialogueNode("questAccepted",
            "Brave, or something close to it. The waystone's open now, right here in the village.\n" +
            "Come back if you find anything. Or if you don't. Either tells us something.")
            .choice(new DialogueChoice("I'll be careful.", "")));

        npc.addNode(new DialogueNode("alreadyOnQuest",
            "The waystone here in the village is still open for you. " +
            "No shame in coming back empty-handed - the Archive's been swallowing " +
            "braver names than yours for longer than I've been old.")
            .choice(new DialogueChoice("Back to my questions.", "start")));

        return npc;
    }

    private static NPC buildScholarYira() {
        NPC npc = new NPC("Scholar Yira", 490, 380, new Color(140, 180, 220), null);

        npc.addNode(new DialogueNode("start",
            "Oh - you're the one who cleared Raku's Lair. I've been documenting the seam's " +
            "thinning for months. You're either very good news or very bad news for my research.")
            .choice(new DialogueChoice("What have you found?", "findings"))
            .choice(new DialogueChoice("What happens if the seam fails completely?", "failure"))
            .choice(new DialogueChoice("I should go.", "")));

        npc.addNode(new DialogueNode("findings",
            "Every collapse point traces back to the keeps losing their wardens. " +
            "No bodies, no battle - they just stopped answering. Something replaced them, " +
            "and whatever it is doesn't want to be found.")
            .choice(new DialogueChoice("Back.", "start")));

        npc.addNode(new DialogueNode("failure",
            "Best guess? The echoes get through cleanly instead of leaking. We already see " +
            "the weaker ones - wraith-shapes, at the edges of the old keeps. " +
            "A full collapse would mean a lot more of those, all at once, and something much larger behind them.")
            .choice(new DialogueChoice("Back.", "start")));

        return npc;
    }

    private static NPC buildOldSoldierPetra() {
        NPC npc = new NPC("Old Soldier Petra", 630, 200, new Color(150, 150, 160), null);

        npc.addNode(new DialogueNode("start",
            "Don't mind me. I watch the road past the thicket - someone has to, since the wardens stopped.\n" +
            "You've got a fight ahead of you if you keep going that way. A big one.")
            .choice(new DialogueChoice("What's out there?", "warning"))
            .choice(new DialogueChoice("Any advice?", "advice"))
            .choice(new DialogueChoice("Thanks.", "")));

        npc.addNode(new DialogueNode("warning",
            "Something wearing a wraith's shape, but bigger. Smarter, too - it doesn't just charge you. " +
            "It keeps its distance and puts things through the air at you instead. Learned that the hard way.")
            .choice(new DialogueChoice("Back.", "start")));

        npc.addNode(new DialogueNode("advice",
            "Close the gap fast, or don't bother chasing it at all. " +
            "It'll keep backing off and shooting as long as you let it. Corner it, or outlast it.")
            .choice(new DialogueChoice("Back.", "start")));

        return npc;
    }
    
    // --- Level 7 (id 6) - bridge out of the village ---
    public static GameMap createOvergrownThicket() {
        GameMap map = new GameMap(6, "Overgrown Thicket",
            new Color(40, 110, 45), new Color(20, 70, 25),
            new Color(35, 95, 40));
        map.groundTileKey = "grass";
        map.pathY = 270;

        map.addEnemy(new Enemy(250, 200, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(450, 350, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(600, 200, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(350, 450, Enemy.EnemyType.SLIME_ELITE));
        map.addEnemy(new Enemy(550, 450, Enemy.EnemyType.SLIME_ELITE));
        map.addEnemy(new Enemy(150, 300, Enemy.EnemyType.GOBLIN));
        map.scatterDecorations("pfw_bushes", BUSH_KEYS, 14, 105, 44, 76);
        map.scatterDecorations("pfw_trees", TREE_KEYS, 11, 905, 70, 120);

        map.addPortal(new Portal(10, 270, 5, 700, 300, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 7, 50, 300, PORTAL_FWD));

        return map;
    }

    // --- Level 8 (id 7) - THE FORK ---
    public static GameMap createCrossroads() {
        GameMap map = new GameMap(7, "The Crossroads",
            new Color(120, 110, 95), new Color(80, 72, 62),
            new Color(100, 90, 78));
        map.groundTileKey = "grass";
        map.pathY = 270;

        map.addEnemy(new Enemy(300, 380, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(500, 180, Enemy.EnemyType.SLIME_ELITE));
        map.scatterDecorations("pfw_rocks", ROCK_KEYS, 6, 700);
        map.scatterDecorations("pfw_trees", TREE_KEYS, 4, 701, 70, 110);

        map.addPortal(new Portal(10, 270, 6, 700, 300, PORTAL_BACK));
        // Two ways forward from here, both clearly visible - a real choice.
        map.addPortal(new Portal(600, 130, 10, 50, 300, PORTAL_ALT));   // alt path -> Rocky Pass
        map.addPortal(new Portal(600, 430, 8, 50, 300, PORTAL_FWD));    // main path -> Bush Hollow

        return map;
    }

    // --- Level 9 (id 8) - main path ---
    public static GameMap createBushHollow() {
        GameMap map = new GameMap(8, "Bush Hollow",
            new Color(60, 130, 60), new Color(35, 90, 35),
            new Color(50, 110, 50));
        map.groundTileKey = "grass";
        map.pathY = 270;

        map.addEnemy(new Enemy(300, 200, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(550, 350, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(400, 250, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(250, 450, Enemy.EnemyType.SLIME_ROYAL));
        map.addEnemy(new Enemy(600, 180, Enemy.EnemyType.SLIME_ROYAL));
        map.addEnemy(new Enemy(150, 250, Enemy.EnemyType.GOBLIN));
        map.scatterDecorations("pfw_bushes", BUSH_KEYS, 18, 107, 44, 80);
        map.scatterDecorations("pfw_trees", TREE_KEYS, 6, 907, 70, 110);

        map.addPortal(new Portal(10, 270, 7, 600, 430, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 9, 370, 500, PORTAL_BOSS).requireLevel(8));

        return map;
    }

    // --- Level 10 (id 9) - BOSS 2: huge kiting Wraith -> unlocks Tank ---
    public static GameMap createWraithBossRoom() {
        GameMap map = new GameMap(9, "The Last Vigil",
            new Color(45, 30, 60), new Color(20, 12, 30),
            new Color(35, 22, 48));

        map.addEnemy(new Enemy(400, 250, Enemy.EnemyType.BOSS_WRAITH));
        map.scatterDecorations("ruins", RUIN_KEYS, 8, 900);

        map.addPortal(new Portal(350, 520, 8, 740, 270, PORTAL_BACK));

        return map;
    }

    // --- Alt path level 1 (id 10) ---
    public static GameMap createRockyPass() {
        GameMap map = new GameMap(10, "Rocky Pass",
            new Color(110, 100, 90), new Color(70, 62, 55),
            new Color(95, 85, 75));

        map.addEnemy(new Enemy(300, 200, Enemy.EnemyType.SLIME_ELITE));
        map.addEnemy(new Enemy(500, 300, Enemy.EnemyType.SLIME_ELITE));
        map.addEnemy(new Enemy(400, 450, Enemy.EnemyType.SLIME_ELITE));
        map.addEnemy(new Enemy(250, 400, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(600, 200, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(650, 450, Enemy.EnemyType.SLIME_ELITE));
        map.scatterDecorations("pfw_rocks", ROCK_KEYS, 12, 106);

        map.addPortal(new Portal(10, 270, 7, 600, 130, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 11, 50, 300, PORTAL_ALT));

        return map;
    }

    // --- Alt path level 2 (id 11) ---
    public static GameMap createStoneWastes() {
        GameMap map = new GameMap(11, "Stone Wastes",
            new Color(90, 80, 75), new Color(55, 48, 45),
            new Color(75, 65, 60));

        map.addEnemy(new Enemy(280, 180, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(550, 200, Enemy.EnemyType.GOBLIN));
        map.addEnemy(new Enemy(350, 350, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(550, 400, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(250, 450, Enemy.EnemyType.SLIME_ROYAL));
        map.addEnemy(new Enemy(650, 300, Enemy.EnemyType.SLIME_ROYAL));
        map.addEnemy(new Enemy(400, 200, Enemy.EnemyType.SKELETON));
        map.scatterDecorations("pfw_rocks", ROCK_KEYS, 14, 108);

        map.addPortal(new Portal(10, 270, 10, 740, 270, PORTAL_BACK));
        map.addPortal(new Portal(740, 270, 12, 370, 500, PORTAL_ALT).requireLevel(7));

        return map;
    }

    // --- Alt path boss (id 12) - a different threat, no class unlock ---
    public static GameMap createRuinedThrone() {
        GameMap map = new GameMap(12, "Ruined Throne",
            new Color(90, 30, 60), new Color(50, 15, 35),
            new Color(75, 25, 50));

        map.addEnemy(new Enemy(370, 150, Enemy.EnemyType.BOSS));
        map.scatterDecorations("ruins", RUIN_KEYS, 6, 109);

        map.addPortal(new Portal(350, 520, 11, 740, 270, PORTAL_BACK));

        return map;
    }

    // --- Side path (id 13) - optional, unlocked by Elder Wren's quest ---
    // Reached via the hidden waystone inside Sanctuary Village (id 5), not
    // part of the 0-12 chain.
    public static GameMap createSunkenArchive() {
        GameMap map = new GameMap(13, "The Sunken Archive",
            new Color(60, 45, 80), new Color(30, 20, 45),
            new Color(50, 38, 68));

        map.addEnemy(new Enemy(300, 220, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(500, 320, Enemy.EnemyType.SKELETON));
        map.addEnemy(new Enemy(400, 420, Enemy.EnemyType.SLIME_ELITE));
        map.addEnemy(new Enemy(250, 380, Enemy.EnemyType.SLIME_ELITE));
        map.scatterDecorations("ruins", RUIN_KEYS, 10, 200);

        map.addNpc(buildArchivistGhost());

        map.addPortal(new Portal(400, 480, 5, 400, 150, PORTAL_HIDDEN));

        return map;
    }

    private static NPC buildArchivistGhost() {
        NPC npc = new NPC("Archivist's Ghost", 400, 180, new Color(180, 200, 230), null);

        npc.addNode(new DialogueNode("start",
            "...another living one. Careful where you tread - the ink here still bites.\n" +
            "I kept these records long after the wardens stopped listening. Ask, if you've come this far.")
            .choice(new DialogueChoice("What happened to the wardens?", "wardens"))
            .choice(new DialogueChoice("What are you?", "ghost"))
            .choice(new DialogueChoice("Is there a way to close the seam for good?", "sealHint"))
            .choice(new DialogueChoice("Thank you. I should go.", "")));

        npc.addNode(new DialogueNode("wardens",
            "They didn't fall in battle. One record calls it 'the quiet exchange' - " +
            "something on the other side offered to hold the seam in their place. " +
            "The wardens said yes. That was the mistake.")
            .choice(new DialogueChoice("Back.", "start")));

        npc.addNode(new DialogueNode("ghost",
            "A record, same as the rest of this place - just one that can still talk. " +
            "I stayed because someone had to remember what actually happened here. " +
            "You're the first in a long while who thought to ask.")
            .choice(new DialogueChoice("Back.", "start")));

        npc.addNode(new DialogueNode("sealHint",
            "The old seal needed two wardens, willing, on both keeps at once. " +
            "You won't find willing ones anymore. But the seam doesn't care who holds it - " +
            "only that someone does. Clear what's ahead yourself, and see what's left holding the door.")
            .choice(new DialogueChoice("Back.", "start")));

        return npc;
    }
}
