package com.rspsi.tools;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public final class MinimapExportDialog extends JDialog {

    public static final int SCALE_GAME = 64;
    public static final int SCALE_FULL = 256;

    private final JRadioButton rbGame = new JRadioButton("Game Scale (64x64px Regions)", true);
    private final JRadioButton rbFull = new JRadioButton("Full Scale (256x256px Regions)");

    private final JCheckBox useHash = new JCheckBox("Enter bounds as region hash (rx<<8 | ry)");
    private final JTextField minRx = new JTextField("1", 5);
    private final JTextField minRy = new JTextField("16", 5);
    private final JTextField maxRx = new JTextField("98", 5);
    private final JTextField maxRy = new JTextField("162", 5);

    private final JTextField minHash = new JTextField("", 8);
    private final JTextField maxHash = new JTextField("", 8);

    private boolean ok;

    public static final class Result {
        public final int minRx, minRy, maxRx, maxRy, tileSize;
        private Result(int minRx, int minRy, int maxRx, int maxRy, int tileSize) {
            this.minRx = minRx; this.minRy = minRy; this.maxRx = maxRx; this.maxRy = maxRy; this.tileSize = tileSize;
        }
    }

    public static Optional<Result> show(Component parent) {
        MinimapExportDialog d = new MinimapExportDialog(parent);
        d.setVisible(true);
        if (!d.ok) return Optional.empty();
        return Optional.of(d.readResult());
    }

    private MinimapExportDialog(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent), "Minimap Export", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4); c.anchor = GridBagConstraints.WEST; c.fill = GridBagConstraints.HORIZONTAL;

        ButtonGroup grp = new ButtonGroup();
        grp.add(rbGame); grp.add(rbFull);

        c.gridx=0; c.gridy=0; c.gridwidth=2;
        p.add(new JLabel("Output Scale:"), c);
        c.gridy=1; p.add(rbGame, c);
        c.gridy=2; p.add(rbFull, c);

        JPanel boundsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints bc = new GridBagConstraints();
        bc.insets = new Insets(2,2,2,2); bc.anchor = GridBagConstraints.WEST;
        bc.gridx=0; bc.gridy=0; boundsPanel.add(new JLabel("min rx:"), bc);
        bc.gridx=1; boundsPanel.add(minRx, bc);
        bc.gridx=2; boundsPanel.add(new JLabel("min ry:"), bc);
        bc.gridx=3; boundsPanel.add(minRy, bc);

        bc.gridx=0; bc.gridy=1; boundsPanel.add(new JLabel("max rx:"), bc);
        bc.gridx=1; boundsPanel.add(maxRx, bc);
        bc.gridx=2; boundsPanel.add(new JLabel("max ry:"), bc);
        bc.gridx=3; boundsPanel.add(maxRy, bc);

        JPanel hashPanel = new JPanel(new GridBagLayout());
        GridBagConstraints hc = new GridBagConstraints();
        hc.insets = new Insets(2,2,2,2); hc.anchor = GridBagConstraints.WEST;
        hc.gridx=0; hc.gridy=0; hashPanel.add(new JLabel("min hash:"), hc);
        hc.gridx=1; hashPanel.add(minHash, hc);
        hc.gridx=2; hashPanel.add(new JLabel("max hash:"), hc);
        hc.gridx=3; hashPanel.add(maxHash, hc);

        c.gridx=0; c.gridy=3; c.gridwidth=2;
        p.add(new JLabel("Bounds:"), c);
        c.gridy=4; p.add(boundsPanel, c);

        c.gridy=5;
        p.add(useHash, c);
        c.gridy=6;
        p.add(hashPanel, c);

        Runnable toggle = () -> {
            boolean byHash = useHash.isSelected();
            for (Component comp : boundsPanel.getComponents()) comp.setEnabled(!byHash);
            for (Component comp : hashPanel.getComponents()) comp.setEnabled(byHash);
        };
        useHash.addActionListener(e -> toggle.run());
        toggle.run();

        JButton bOk = new JButton("OK");
        JButton bCancel = new JButton("Cancel");
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(bCancel); btns.add(bOk);

        bOk.addActionListener(e -> {
            try {
                readResult();
                ok = true;
                dispose();
            }
            catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid input", JOptionPane.ERROR_MESSAGE);
            }
        });
        bCancel.addActionListener(e -> { ok = false; dispose(); });

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(p, BorderLayout.CENTER);
        getContentPane().add(btns, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }

    private Result readResult() {
        int tileSize = rbFull.isSelected() ? SCALE_FULL : SCALE_GAME;

        int aMinRx, aMinRy, aMaxRx, aMaxRy;
        if (useHash.isSelected()) {
            int h0 = parseInt(minHash.getText().trim(), "min hash");
            int h1 = parseInt(maxHash.getText().trim(), "max hash");
            int rx0 = (h0 >> 8) & 0x3FF, ry0 = h0 & 0xFF;
            int rx1 = (h1 >> 8) & 0x3FF, ry1 = h1 & 0xFF;
            aMinRx = Math.min(rx0, rx1); aMaxRx = Math.max(rx0, rx1);
            aMinRy = Math.min(ry0, ry1); aMaxRy = Math.max(ry0, ry1);
        } else {
            aMinRx = parseInt(minRx.getText().trim(), "min rx");
            aMinRy = parseInt(minRy.getText().trim(), "min ry");
            aMaxRx = parseInt(maxRx.getText().trim(), "max rx");
            aMaxRy = parseInt(maxRy.getText().trim(), "max ry");
            if (aMinRx > aMaxRx || aMinRy > aMaxRy)
                throw new IllegalArgumentException("min must be <= max for rx/ry.");
        }
        return new Result(aMinRx, aMinRy, aMaxRx, aMaxRy, tileSize);
    }

    private static int parseInt(String s, String label) {
        try {
            return Integer.decode(s);
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Bad integer for " + label + ": " + s);
        }
    }
}
