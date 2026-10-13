package org.datazuul.amadeos.mail.ui.swing;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;

/**
 * Provides authentic Motif 3D styling and theme colors for amadeOS Swing applications,
 * eliminating default Metal/Ocean blueish controls and replacing them with workstation 3D bevels.
 */
public final class AmadeUI {

    // Warm Motif Palette matching amadeOS DesktopTheme
    public static final Color BASE = new Color(0xCE, 0xC2, 0xBB);
    public static final Color HIGHLIGHT = Color.WHITE;
    public static final Color SOFT_HIGHLIGHT = new Color(0xEA, 0xE5, 0xE2);
    public static final Color SHADOW = new Color(0x8C, 0x82, 0x7A);
    public static final Color DARK_SHADOW = new Color(0x42, 0x3B, 0x35);
    public static final Color SUNKEN_BG = new Color(0xBA, 0xAF, 0xA8);
    public static final Color TEXT_ACTIVE = new Color(0x11, 0x11, 0x11);
    public static final Color TEXT_INACTIVE = new Color(0x75, 0x6E, 0x68);

    private AmadeUI() {
    }

    /**
     * Installs the amadeOS Motif 3D look-and-feel centrally into UIManager.
     */
    public static void install() {
        try {
            // Start with base CrossPlatform / Basic LAF
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
            org.slf4j.LoggerFactory.getLogger(AmadeUI.class).warn("Could not install cross-platform look and feel", e);
        }

        // Global Palette
        UIManager.put("control", new ColorUIResource(BASE));
        UIManager.put("controlHighlight", new ColorUIResource(HIGHLIGHT));
        UIManager.put("controlLtHighlight", new ColorUIResource(HIGHLIGHT));
        UIManager.put("controlShadow", new ColorUIResource(SHADOW));
        UIManager.put("controlDkShadow", new ColorUIResource(DARK_SHADOW));
        UIManager.put("controlText", new ColorUIResource(TEXT_ACTIVE));

        // Panels & Dialogs
        UIManager.put("Panel.background", new ColorUIResource(BASE));
        UIManager.put("OptionPane.background", new ColorUIResource(BASE));
        UIManager.put("OptionPane.messageForeground", new ColorUIResource(TEXT_ACTIVE));

        // Buttons
        UIManager.put("ButtonUI", AmadeButtonUI.class.getName());
        UIManager.put("Button.background", new ColorUIResource(BASE));
        UIManager.put("Button.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("Button.select", new ColorUIResource(SUNKEN_BG));
        UIManager.put("Button.focus", new ColorUIResource(SHADOW));
        UIManager.put("Button.border", new MotifButtonBorder(2));

        // Toggle Buttons
        UIManager.put("ToggleButtonUI", AmadeToggleButtonUI.class.getName());
        UIManager.put("ToggleButton.background", new ColorUIResource(BASE));
        UIManager.put("ToggleButton.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("ToggleButton.select", new ColorUIResource(SUNKEN_BG));
        UIManager.put("ToggleButton.border", new MotifButtonBorder(2));

        // Metal's Ocean painter expects two ratios followed by three colors.
        java.util.List<?> flatGrad = java.util.Arrays.asList(
                0.0, 0.0,
                new ColorUIResource(BASE),
                new ColorUIResource(BASE),
                new ColorUIResource(BASE)
        );
        UIManager.put("MenuBar.gradient", flatGrad);
        UIManager.put("MenuItem.gradient", flatGrad);
        UIManager.put("CheckBoxMenuItem.gradient", flatGrad);
        UIManager.put("RadioButtonMenuItem.gradient", flatGrad);
        UIManager.put("Button.gradient", flatGrad);
        UIManager.put("ToggleButton.gradient", flatGrad);
        UIManager.put("CheckBox.gradient", flatGrad);
        UIManager.put("RadioButton.gradient", flatGrad);
        UIManager.put("ScrollBar.gradient", java.util.List.of(
                0.3f, 0.2f,
                new ColorUIResource(HIGHLIGHT),
                new ColorUIResource(BASE),
                new ColorUIResource(SHADOW)));
        UIManager.put("Slider.gradient", flatGrad);

        // Menus
        UIManager.put("MenuBarUI", "javax.swing.plaf.basic.BasicMenuBarUI");
        UIManager.put("MenuBar.background", new ColorUIResource(BASE));
        UIManager.put("MenuBar.border", new MotifBevelBorder(true, 2));

        UIManager.put("MenuUI", "javax.swing.plaf.basic.BasicMenuUI");
        UIManager.put("Menu.background", new ColorUIResource(BASE));
        UIManager.put("Menu.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("Menu.selectionBackground", new ColorUIResource(SOFT_HIGHLIGHT));
        UIManager.put("Menu.selectionForeground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("Menu.border", BorderFactory.createEmptyBorder(2, 6, 2, 6));

        UIManager.put("MenuItemUI", "javax.swing.plaf.basic.BasicMenuItemUI");
        UIManager.put("MenuItem.background", new ColorUIResource(BASE));
        UIManager.put("MenuItem.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(SOFT_HIGHLIGHT));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("MenuItem.acceleratorForeground", new ColorUIResource(TEXT_INACTIVE));
        UIManager.put("MenuItem.acceleratorSelectionForeground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("MenuItem.border", BorderFactory.createEmptyBorder(2, 6, 2, 6));

        UIManager.put("CheckBoxMenuItemUI", "javax.swing.plaf.basic.BasicCheckBoxMenuItemUI");
        UIManager.put("CheckBoxMenuItem.background", new ColorUIResource(BASE));
        UIManager.put("CheckBoxMenuItem.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("CheckBoxMenuItem.selectionBackground", new ColorUIResource(SOFT_HIGHLIGHT));
        UIManager.put("CheckBoxMenuItem.selectionForeground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("CheckBoxMenuItem.acceleratorForeground", new ColorUIResource(TEXT_INACTIVE));
        UIManager.put("CheckBoxMenuItem.acceleratorSelectionForeground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("CheckBoxMenuItem.border", BorderFactory.createEmptyBorder(2, 6, 2, 6));
        UIManager.put("CheckBoxMenuItem.checkIcon", new MotifMenuCheckIcon());

        UIManager.put("RadioButtonMenuItemUI", "javax.swing.plaf.basic.BasicRadioButtonMenuItemUI");
        UIManager.put("RadioButtonMenuItem.background", new ColorUIResource(BASE));
        UIManager.put("RadioButtonMenuItem.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("RadioButtonMenuItem.selectionBackground", new ColorUIResource(SOFT_HIGHLIGHT));
        UIManager.put("RadioButtonMenuItem.selectionForeground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("RadioButtonMenuItem.acceleratorForeground", new ColorUIResource(TEXT_INACTIVE));
        UIManager.put("RadioButtonMenuItem.acceleratorSelectionForeground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("RadioButtonMenuItem.border", BorderFactory.createEmptyBorder(2, 6, 2, 6));
        UIManager.put("RadioButtonMenuItem.checkIcon", new MotifRadioButtonIcon());

        UIManager.put("PopupMenu.background", new ColorUIResource(BASE));
        UIManager.put("PopupMenu.border", new MotifBevelBorder(true, 2));

        UIManager.put("Separator.background", new ColorUIResource(SHADOW));
        UIManager.put("Separator.foreground", new ColorUIResource(HIGHLIGHT));
        UIManager.put("PopupMenuSeparator.background", new ColorUIResource(SHADOW));
        UIManager.put("PopupMenuSeparator.foreground", new ColorUIResource(HIGHLIGHT));

        // CheckBoxes & RadioButtons
        UIManager.put("CheckBoxUI", "javax.swing.plaf.basic.BasicCheckBoxUI");
        UIManager.put("CheckBox.background", new ColorUIResource(BASE));
        UIManager.put("CheckBox.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("CheckBox.focus", new ColorUIResource(SHADOW));
        UIManager.put("CheckBox.icon", new MotifCheckBoxIcon());

        UIManager.put("RadioButtonUI", "javax.swing.plaf.basic.BasicRadioButtonUI");
        UIManager.put("RadioButton.background", new ColorUIResource(BASE));
        UIManager.put("RadioButton.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("RadioButton.focus", new ColorUIResource(SHADOW));
        UIManager.put("RadioButton.icon", new MotifRadioButtonIcon());

        // Text Fields & Areas
        UIManager.put("TextField.background", new ColorUIResource(Color.WHITE));
        UIManager.put("TextField.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("TextField.selectionBackground", new ColorUIResource(new Color(0x4A, 0x6B, 0x82)));
        UIManager.put("TextField.selectionForeground", new ColorUIResource(Color.WHITE));
        UIManager.put("TextField.border", new MotifBevelBorder(false, 2));

        // ScrollPanes & ScrollBars
        UIManager.put("ScrollPane.background", new ColorUIResource(BASE));
        UIManager.put("ScrollPane.border", new MotifBevelBorder(false, 2));
        UIManager.put("ScrollBar.background", new ColorUIResource(SUNKEN_BG));
        UIManager.put("ScrollBar.thumb", new ColorUIResource(BASE));
        UIManager.put("ScrollBar.track", new ColorUIResource(SUNKEN_BG));

        // TabbedPane
        UIManager.put("TabbedPane.background", new ColorUIResource(BASE));
        UIManager.put("TabbedPane.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("TabbedPane.selected", new ColorUIResource(SOFT_HIGHLIGHT));
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(2, 2, 2, 2));

        // Lists, trees and tables of the mailer (header list, folder tree, account list)
        ColorUIResource selection = new ColorUIResource(SELECTION);
        ColorUIResource white = new ColorUIResource(Color.WHITE);
        UIManager.put("Table.background", white);
        UIManager.put("Table.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("Table.selectionBackground", selection);
        UIManager.put("Table.selectionForeground", white);
        UIManager.put("Table.gridColor", new ColorUIResource(SOFT_HIGHLIGHT));
        UIManager.put("TableHeader.background", new ColorUIResource(BASE));
        UIManager.put("TableHeader.foreground", new ColorUIResource(TEXT_ACTIVE));
        UIManager.put("Tree.background", white);
        UIManager.put("Tree.textBackground", white);
        UIManager.put("Tree.selectionBackground", selection);
        UIManager.put("Tree.selectionForeground", white);
        UIManager.put("Tree.selectionBorderColor", selection);
        UIManager.put("List.background", white);
        UIManager.put("List.selectionBackground", selection);
        UIManager.put("List.selectionForeground", white);
        UIManager.put("TextArea.background", white);
        UIManager.put("TextArea.selectionBackground", selection);
        UIManager.put("TextArea.selectionForeground", white);
        UIManager.put("PasswordField.background", white);
        UIManager.put("PasswordField.border", new MotifBevelBorder(false, 2));
        UIManager.put("ComboBox.background", new ColorUIResource(BASE));
        UIManager.put("ComboBox.selectionBackground", selection);
        UIManager.put("ComboBox.selectionForeground", white);
        UIManager.put("SplitPane.background", new ColorUIResource(BASE));
        UIManager.put("SplitPane.dividerSize", 8);
        UIManager.put("Spinner.background", new ColorUIResource(BASE));
        UIManager.put("ToolTip.background", new ColorUIResource(SOFT_HIGHLIGHT));
    }

    /** Selection colour of the amadeOS window titles. */
    public static final Color SELECTION = new Color(0x4A, 0x6B, 0x82);

    /** A sunken, white-backed scroll area as used for lists and text in CDE dialogs. */
    public static javax.swing.border.Border sunken() {
        return new MotifBevelBorder(false, 2);
    }

    /** A small filled circle in the given colour, used as account marker. */
    public static Icon colorDot(Color color, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x, y, size, size);
                g2.setColor(DARK_SHADOW);
                g2.drawOval(x, y, size - 1, size - 1);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Helper to paint standard 3D beveled borders.
     */
    public static void paintBevel(Graphics g, int x, int y, int width, int height, boolean raised, int thickness) {
        Graphics2D g2 = (Graphics2D) g;
        Color tl1 = raised ? HIGHLIGHT : DARK_SHADOW;
        Color tl2 = raised ? SOFT_HIGHLIGHT : SHADOW;
        Color br1 = raised ? DARK_SHADOW : HIGHLIGHT;
        Color br2 = raised ? SHADOW : SOFT_HIGHLIGHT;

        for (int i = 0; i < thickness; i++) {
            Color tl = (i == 0) ? tl1 : tl2;
            Color br = (i == 0) ? br1 : br2;

            g2.setColor(tl);
            g2.drawLine(x + i, y + i, x + width - 1 - i, y + i);
            g2.drawLine(x + i, y + i, x + i, y + height - 1 - i);

            g2.setColor(br);
            g2.drawLine(x + i, y + height - 1 - i, x + width - 1 - i, y + height - 1 - i);
            g2.drawLine(x + width - 1 - i, y + i, x + width - 1 - i, y + height - 1 - i);
        }
    }

    /**
     * Motif 3D Bevel Border drawing authentic 3D chiseled workstation borders.
     */
    public static class MotifBevelBorder extends AbstractBorder {
        private final boolean raised;
        private final int thickness;

        public MotifBevelBorder(boolean raised, int thickness) {
            this.raised = raised;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            paintBevel(g, x, y, width, height, raised, thickness);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            if (c instanceof javax.swing.JScrollPane) {
                return new Insets(thickness, thickness, thickness, thickness);
            }
            return new Insets(thickness + 3, thickness + 8, thickness + 3, thickness + 8);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            Insets ins = getBorderInsets(c);
            insets.top = ins.top;
            insets.left = ins.left;
            insets.bottom = ins.bottom;
            insets.right = ins.right;
            return insets;
        }
    }

    /**
     * Dynamic Motif 3D Button Border that reflects the pressed/selected state of the button
     * without mutating component borders or triggering repaint/layout thrashing during paint().
     */
    public static class MotifButtonBorder extends AbstractBorder {
        private final int thickness;

        public MotifButtonBorder(int thickness) {
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            boolean isSunken = false;
            if (c instanceof AbstractButton b) {
                ButtonModel model = b.getModel();
                if (model.isArmed() && model.isPressed()) {
                    isSunken = true;
                } else if (model.isSelected()) {
                    isSunken = true;
                }
            }
            paintBevel(g, x, y, width, height, !isSunken, thickness);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            Insets margin = (c instanceof AbstractButton b) ? b.getMargin() : null;
            if (margin != null) {
                return new Insets(thickness + margin.top, thickness + margin.left,
                        thickness + margin.bottom, thickness + margin.right);
            }
            return new Insets(thickness + 2, thickness + 6, thickness + 2, thickness + 6);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            Insets ins = getBorderInsets(c);
            insets.top = ins.top;
            insets.left = ins.left;
            insets.bottom = ins.bottom;
            insets.right = ins.right;
            return insets;
        }
    }

    /**
     * Authentic 3D Motif ButtonUI matching workstation window buttons.
     */
    public static class AmadeButtonUI extends BasicButtonUI {
        private static final AmadeButtonUI INSTANCE = new AmadeButtonUI();

        public static javax.swing.plaf.ComponentUI createUI(JComponent c) {
            return INSTANCE;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setRolloverEnabled(true);
            b.setBackground(BASE);
            b.setForeground(TEXT_ACTIVE);
            b.setBorder(new MotifButtonBorder(2));
            b.setFont(b.getFont().deriveFont(Font.BOLD, 12.0f));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean isPressed = model.isArmed() && model.isPressed();
            boolean isHovered = model.isRollover();

            Color bg;
            if (isPressed) {
                bg = SUNKEN_BG;
            } else if (isHovered) {
                bg = SOFT_HIGHLIGHT;
            } else {
                Color customBg = b.getBackground();
                bg = (customBg != null) ? customBg : BASE;
            }

            g2.setColor(bg);
            g2.fillRect(0, 0, c.getWidth(), c.getHeight());

            // If pressed, offset text & icon by 1px (classic Motif button click feedback)
            if (isPressed) {
                g2.translate(1, 1);
            }

            super.paint(g2, c);

            if (isPressed) {
                g2.translate(-1, -1);
            }
        }
    }

    /**
     * Authentic 3D Motif ToggleButtonUI (sunken when selected).
     */
    public static class AmadeToggleButtonUI extends BasicToggleButtonUI {
        private static final AmadeToggleButtonUI INSTANCE = new AmadeToggleButtonUI();

        public static javax.swing.plaf.ComponentUI createUI(JComponent c) {
            return INSTANCE;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            AbstractButton b = (AbstractButton) c;
            b.setOpaque(true);
            b.setFocusPainted(false);
            b.setRolloverEnabled(true);
            b.setBackground(BASE);
            b.setForeground(TEXT_ACTIVE);
            b.setBorder(new MotifButtonBorder(2));
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            ButtonModel model = b.getModel();
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean isSelected = model.isSelected();
            boolean isPressed = model.isArmed() && model.isPressed();
            boolean isHovered = model.isRollover();

            Color bg;
            if (isPressed || isSelected) {
                bg = SUNKEN_BG;
            } else if (isHovered) {
                bg = SOFT_HIGHLIGHT;
            } else {
                bg = BASE;
            }

            g2.setColor(bg);
            g2.fillRect(0, 0, c.getWidth(), c.getHeight());

            if (isPressed || isSelected) {
                g2.translate(1, 1);
            }

            super.paint(g2, c);

            if (isPressed || isSelected) {
                g2.translate(-1, -1);
            }
        }
    }

    /**
     * Authentic 3D Motif CheckBox Icon for menu items.
     */
    public static class MotifMenuCheckIcon implements javax.swing.Icon {
        private final int size = 12;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            paintBevel(g2, x, y, size, size, false, 1);
            g2.setColor(SUNKEN_BG);
            g2.fillRect(x + 1, y + 1, size - 2, size - 2);

            // BasicMenuItemUI paints the check icon for selected and unselected items alike.
            if (c instanceof AbstractButton b && b.isSelected()) {
                g2.setColor(TEXT_ACTIVE);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new java.awt.BasicStroke(1.5f));
                g2.drawLine(x + 2, y + 6, x + 5, y + 9);
                g2.drawLine(x + 5, y + 9, x + 9, y + 3);
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /**
     * Authentic 3D Motif CheckBox Icon for standard dialog and panel checkboxes.
     */
    public static class MotifCheckBoxIcon implements javax.swing.Icon {
        private final int size = 13;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            paintBevel(g2, x, y, size, size, false, 1);
            g2.setColor(SUNKEN_BG);
            g2.fillRect(x + 1, y + 1, size - 2, size - 2);

            if (c instanceof AbstractButton b && b.isSelected()) {
                g2.setColor(TEXT_ACTIVE);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new java.awt.BasicStroke(1.8f));
                g2.drawLine(x + 2, y + 6, x + 5, y + 10);
                g2.drawLine(x + 5, y + 10, x + 10, y + 3);
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    /**
     * Authentic 3D Motif RadioButton Icon.
     */
    public static class MotifRadioButtonIcon implements javax.swing.Icon {
        private final int size = 13;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int midX = x + size / 2;
            int midY = y + size / 2;

            g2.setColor(SUNKEN_BG);
            g2.fillOval(x + 1, y + 1, size - 2, size - 2);

            g2.setColor(DARK_SHADOW);
            g2.drawArc(x, y, size - 1, size - 1, 45, 180);
            g2.setColor(HIGHLIGHT);
            g2.drawArc(x, y, size - 1, size - 1, 225, 180);

            if (c instanceof AbstractButton b && b.isSelected()) {
                g2.setColor(TEXT_ACTIVE);
                g2.fillOval(midX - 2, midY - 2, 5, 5);
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
