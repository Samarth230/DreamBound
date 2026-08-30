package com;

import java.awt.Color;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class NPC implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public int x, y;
    public int width = 48, height = 48;
    public String spriteFolder; // null = no art yet, draws a fallback silhouette
    public Color fallbackColor;
    public Map<String, DialogueNode> nodes = new HashMap<>();
    public String startNodeId = "start";

    public NPC(String name, int x, int y, Color fallbackColor, String spriteFolder) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.fallbackColor = fallbackColor;
        this.spriteFolder = spriteFolder;
    }

    public NPC addNode(DialogueNode node) {
        nodes.put(node.id, node);
        return this;
    }

    public DialogueNode getStartNode() {
        return nodes.get(startNodeId);
    }

    public boolean playerInRange(Player player, int range) {
        int dx = (x + width / 2) - (player.x + player.width / 2);
        int dy = (y + height / 2) - (player.y + player.height / 2);
        return Math.sqrt(dx * dx + dy * dy) < range;
    }
}
