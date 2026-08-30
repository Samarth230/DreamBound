package com;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** One node in an NPC's dialogue tree: a line of text plus the choices leading out of it. */
public class DialogueNode implements Serializable {
    private static final long serialVersionUID = 1L;

    public String id;
    public String text;
    public List<DialogueChoice> choices = new ArrayList<>();

    public DialogueNode(String id, String text) {
        this.id = id;
        this.text = text;
    }

    public DialogueNode choice(DialogueChoice c) {
        choices.add(c);
        return this;
    }
}
