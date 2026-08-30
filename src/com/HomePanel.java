package com;



import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

public class HomePanel extends JPanel {
    private final CardLayout cardLayout;
    private final JPanel container;
    private GamePanel gamePanel;
    private final JLabel saveStatus;
    private int animFrame = 0;
    private Timer animTimer;

    public HomePanel(CardLayout cardLayout, JPanel container) {
        this.cardLayout = cardLayout;
        this.container = container;

        setLayout(new GridBagLayout());
        setBackground(new Color(15, 15, 25));
        GridBagConstraints gbc = new GridBagConstraints();
        
        animTimer = new Timer(50, e -> {
            animFrame++;
            repaint();
        });
        animTimer.start();

        JLabel title = new JLabel("DREAMBOUND");
        title.setFont(new Font("SansSerif", Font.BOLD, 58));
        title.setForeground(new Color(120, 180, 255));
        gbc.gridx = 0; gbc.gridy = 0; gbc.insets = new Insets(40,0,10,0);
        add(title, gbc);
        
        JLabel subtitle = new JLabel("A 2D Action RPG Adventure");
        subtitle.setFont(new Font("SansSerif", Font.ITALIC, 18));
        subtitle.setForeground(new Color(150, 170, 220));
        gbc.gridy = 1; gbc.insets = new Insets(0,0,40,0);
        add(subtitle, gbc);

        JButton newBtn = new JButton("⚔ New Game");
        styleButton(newBtn, new Color(80, 120, 200));
        newBtn.addActionListener(e -> {
            if (gamePanel != null) gamePanel.startNewGame();
            cardLayout.show(container, "GAME");
            SwingUtilities.invokeLater(() -> gamePanel.requestFocusInWindow());
        });

        JButton loadBtn = new JButton("📂 Load Game");
        styleButton(loadBtn, new Color(100, 160, 100));
        loadBtn.addActionListener(e -> {
            if (gamePanel != null) {
                boolean ok = gamePanel.loadGame();
                if (ok) {
                    cardLayout.show(container, "GAME");
                    SwingUtilities.invokeLater(() -> gamePanel.requestFocusInWindow());
                } else {
                    JOptionPane.showMessageDialog(this, 
                        "No saved game found.\nStart a new game to begin your adventure!", 
                        "Load Failed", 
                        JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        JButton exitBtn = new JButton("✕ Exit");
        styleButton(exitBtn, new Color(180, 80, 80));
        exitBtn.addActionListener(e -> System.exit(0));

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new GridLayout(0,1,0,15));
        buttons.add(newBtn);
        buttons.add(loadBtn);
        buttons.add(exitBtn);

        gbc.gridy = 2;
        add(buttons, gbc);

        saveStatus = new JLabel(getSaveStatusText());
        saveStatus.setForeground(new Color(180, 180, 200));
        saveStatus.setFont(new Font("SansSerif", Font.PLAIN, 13));
        gbc.gridy = 3; gbc.insets = new Insets(35,0,0,0);
        add(saveStatus, gbc);
        
        JPanel instructionsPanel = new JPanel();
        instructionsPanel.setOpaque(false);
        instructionsPanel.setLayout(new BoxLayout(instructionsPanel, BoxLayout.Y_AXIS));
        
        JLabel inst1 = new JLabel("Fight enemies across 4 unique maps");
        JLabel inst2 = new JLabel("Level up and defeat the final boss!");
        JLabel inst3 = new JLabel("Arrow keys to move • Space to attack");
        
        for (JLabel lbl : new JLabel[]{inst1, inst2, inst3}) {
            lbl.setForeground(new Color(160, 160, 180));
            lbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
            lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            instructionsPanel.add(lbl);
        }
        
        gbc.gridy = 4; gbc.insets = new Insets(25,0,20,0);
        add(instructionsPanel, gbc);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                saveStatus.setText(getSaveStatusText());
            }
        });
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        GradientPaint bgGradient = new GradientPaint(
            0, 0, new Color(15, 15, 25),
            0, getHeight(), new Color(30, 20, 40)
        );
        g2d.setPaint(bgGradient);
        g2d.fillRect(0, 0, getWidth(), getHeight());
        
        g2d.setColor(new Color(100, 150, 255, 50));
        for (int i = 0; i < 20; i++) {
            int x = (int)((i * 73 + animFrame * 2) % getWidth());
            int y = (int)((i * 97 + animFrame) % getHeight());
            int size = 2 + (i % 3);
            g2d.fillOval(x, y, size, size);
        }
    }
    
    private void styleButton(JButton btn, Color baseColor) {
        btn.setPreferredSize(new Dimension(240, 50));
        btn.setFont(new Font("SansSerif", Font.BOLD, 18));
        btn.setFocusPainted(false);
        btn.setBackground(baseColor);
        btn.setForeground(Color.WHITE);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(baseColor.brighter(), 2, true),
            BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));
        
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(baseColor.brighter());
                btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
            public void mouseExited(MouseEvent e) {
                btn.setBackground(baseColor);
                btn.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
            public void mousePressed(MouseEvent e) {
                btn.setBackground(baseColor.darker());
            }
            public void mouseReleased(MouseEvent e) {
                btn.setBackground(baseColor.brighter());
            }
        });
    }

    private String getSaveStatusText() {
        if (GamePanel.SAVE_FILE.exists()) {
            return "Save file detected";
        } else {
            return "No save file found";
        }
    }

    public void setGamePanel(GamePanel gp) {
        this.gamePanel = gp;
    }
}