
package com.example.fileorganizer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OrganizerGUI extends JFrame {
    private JTextField sourceField;
    private JTextField targetField;
    private JComboBox<String> modeCombo;
    private JCheckBox moveBox;
    private JCheckBox copyBox;
    private JCheckBox dryRunBox;
    private JTextArea logArea;

    public OrganizerGUI() {
        super("File Organizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(600, 400);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new GridLayout(0, 1));

        JPanel sourcePanel = new JPanel(new BorderLayout());
        sourcePanel.add(new JLabel("Source Folder:"), BorderLayout.WEST);
        sourceField = new JTextField();
        JButton srcBtn = new JButton("Browse");
        srcBtn.addActionListener(e -> chooseFolder(sourceField));
        sourcePanel.add(sourceField, BorderLayout.CENTER);
        sourcePanel.add(srcBtn, BorderLayout.EAST);
        topPanel.add(sourcePanel);

        JPanel targetPanel = new JPanel(new BorderLayout());
        targetPanel.add(new JLabel("Target Folder:"), BorderLayout.WEST);
        targetField = new JTextField();
        JButton tgtBtn = new JButton("Browse");
        tgtBtn.addActionListener(e -> chooseFolder(targetField));
        targetPanel.add(targetField, BorderLayout.CENTER);
        targetPanel.add(tgtBtn, BorderLayout.EAST);
        topPanel.add(targetPanel);

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modePanel.add(new JLabel("Mode:"));
        modeCombo = new JComboBox<>(new String[]{"by-extension", "by-date", "by-type"});
        modePanel.add(modeCombo);
        topPanel.add(modePanel);

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        moveBox = new JCheckBox("Move", true);
        copyBox = new JCheckBox("Copy");
        dryRunBox = new JCheckBox("Dry Run");
        ButtonGroup bg = new ButtonGroup();
        bg.add(moveBox);
        bg.add(copyBox);
        optionsPanel.add(moveBox);
        optionsPanel.add(copyBox);
        optionsPanel.add(dryRunBox);
        topPanel.add(optionsPanel);

        JButton runBtn = new JButton("Run Organizer");
        runBtn.addActionListener(this::runOrganizer);
        topPanel.add(runBtn);

        add(topPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void chooseFolder(JTextField targetField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void runOrganizer(ActionEvent e) {
        String source = sourceField.getText().trim();
        if (source.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a source folder.");
            return;
        }
        String target = targetField.getText().trim();
        String mode = (String) modeCombo.getSelectedItem();

        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("--source"); args.add(source);
        if (!target.isEmpty()) { args.add("--target"); args.add(target); }
        args.add("--mode"); args.add(mode);
        if (copyBox.isSelected()) args.add("--copy");
        else args.add("--move");
        if (dryRunBox.isSelected()) args.add("--dry-run");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(ps);
        System.setErr(ps);
        try {
            Main.Config cfg = Main.Config.parseArgs(args.toArray(new String[0]));
            new Main.Organizer(cfg).run();
        } catch (Exception ex) {
            ex.printStackTrace(ps);
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        logArea.setText(baos.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OrganizerGUI().setVisible(true));
    }
}
