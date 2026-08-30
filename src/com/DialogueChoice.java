package com;

import java.io.Serializable;

/**
 * A choice the player can pick in a dialogue box.
 *
 * nextNodeId: null/empty ends the conversation.
 * setFlag: story flag to set on Player when this choice is picked (nullable).
 * requiresFlagAbsent / requiresFlagPresent: if set, this choice only appears
 *   when that flag is (not) already set - lets a conversation change once
 *   you've already accepted a quest, etc.
 */
public class DialogueChoice implements Serializable {
    private static final long serialVersionUID = 1L;

    public String label;
    public String nextNodeId;
    public String setFlag;
    public String requiresFlagAbsent;
    public String requiresFlagPresent;

    public DialogueChoice(String label, String nextNodeId) {
        this.label = label;
        this.nextNodeId = nextNodeId;
    }

    public DialogueChoice setFlag(String flag) {
        this.setFlag = flag;
        return this;
    }

    public DialogueChoice requiresAbsent(String flag) {
        this.requiresFlagAbsent = flag;
        return this;
    }

    public DialogueChoice requiresPresent(String flag) {
        this.requiresFlagPresent = flag;
        return this;
    }

    public boolean isAvailable(Player player) {
        if (requiresFlagAbsent != null && player.hasFlag(requiresFlagAbsent)) return false;
        if (requiresFlagPresent != null && !player.hasFlag(requiresFlagPresent)) return false;
        return true;
    }
}
