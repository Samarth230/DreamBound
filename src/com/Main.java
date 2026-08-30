package com;



import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Dreambound - Action RPG");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(800, 600);
            frame.setResizable(false);

            CardLayout cardLayout = new CardLayout();
            JPanel container = new JPanel(cardLayout);

            HomePanel home = new HomePanel(cardLayout, container);
            GamePanel game = new GamePanel(cardLayout, container);

            home.setGamePanel(game);

            container.add(home, "HOME");
            container.add(game, "GAME");

            frame.add(container);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}