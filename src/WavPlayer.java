import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JFileChooser;
import javax.sound.sampled.SourceDataLine;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WavPlayer {
    private static List<File> wavFiles;
    private static JTable wavTable;
//    private static Clip currentClip;

    private static SourceDataLine currentLine;


    private static int currentPlayingIndex = 0;
    private static double[] gains;

    private static boolean pausePressed = false;

    private static boolean manualNavigation = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static List<File> loadWavFiles(String folderPath) {
        List<File> files = new ArrayList<>();
        try {
            Files.walk(Paths.get(folderPath))
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".wav"))
                    .forEach(path -> files.add(path.toFile()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }

    private static boolean mouseClicked = false;

    private static CustomTableModel createTableModel() {
        String[] columnNames = {"Wav File", "Gain"};
        Object[][] data;

        if (wavFiles != null) {
            data = new Object[wavFiles.size()][2];
            for (int i = 0; i < wavFiles.size(); i++) {
                data[i][0] = wavFiles.get(i).getName();
                data[i][1] = 0;
            }
        } else {
            data = new Object[0][2];
        }

        return new CustomTableModel(data, columnNames);
    }

    private static File currentFolder;

    private static void saveToFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save File");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        // Set default file name to the opened folder's name with a .txt extension
        if (currentFolder != null) {
            String defaultFileName = currentFolder.getName() + ".txt";
            fileChooser.setSelectedFile(new File(defaultFileName));
        }

        int result = fileChooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            try (PrintWriter writer = new PrintWriter(new FileWriter(fileToSave))) {
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < wavFiles.size(); i++) {
                    String fileName = wavFiles.get(i).getName();
                    double gain = gains[i];
                    lines.add(fileName + " - " + new DecimalFormat("#.#").format(gain));
                }

                // Sort the lines alphabetically
                Collections.sort(lines);

                // Write the sorted lines to the file
                for (String line : lines) {
                    writer.println(line);
                }

                // Close the PrintWriter
            } catch (IOException e) {
                e.printStackTrace();

                // Show an error message dialog
                JOptionPane.showMessageDialog(null, "An error occurred while saving the file. Please try again.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Wav Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openFolderItem = new JMenuItem("Open Folder");

        openFolderItem.addActionListener(e -> {
            JFileChooser folderChooser = new JFileChooser();
            folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int returnValue = folderChooser.showOpenDialog(null);
            if (returnValue == JFileChooser.APPROVE_OPTION) {
                currentFolder = folderChooser.getSelectedFile();

                wavFiles = loadWavFiles(currentFolder.getAbsolutePath());
                gains = new double[wavFiles.size()];
                wavTable.setModel(createTableModel());

                // Set custom cell renderer
                CustomTableCellRenderer customRenderer = new CustomTableCellRenderer();
                for (int i = 0; i < wavTable.getColumnCount(); i++) {
                    wavTable.getColumnModel().getColumn(i).setCellRenderer(customRenderer);
                }

                shuffleTable();
                wavTable.repaint();
            }
        });

        fileMenu.add(openFolderItem);

        JMenuItem saveMenuItem = new JMenuItem("Save");
        saveMenuItem.addActionListener(e -> saveToFile());
        fileMenu.add(saveMenuItem);

        JMenuItem importMenuItem = new JMenuItem("Import");
        importMenuItem.addActionListener(e -> importFromJson());
        fileMenu.add(importMenuItem);

        JMenuItem exportMenuItem = new JMenuItem("Export");
        exportMenuItem.addActionListener(e -> exportToJson());
        fileMenu.add(exportMenuItem);

        menuBar.add(fileMenu);
        frame.setJMenuBar(menuBar);

        wavTable = createWavTable();
        wavTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = wavTable.getSelectedRow();
                if (row >= 0) {
                    currentPlayingIndex = row;
                    mouseClicked = true;
                    try {
                        if (currentLine != null && currentLine.isRunning()) {
                            currentLine.stop();
                        }
                        playWav(currentPlayingIndex);
                    } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(wavTable);
        frame.add(scrollPane);

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(new KeyEventDispatcher() {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent e) {

                        if (e.getID() == KeyEvent.KEY_RELEASED) {

                            try {
                                switch (e.getKeyCode()) {

                                    case KeyEvent.VK_SPACE:
                                        if (currentLine == null) {
                                            playWav(currentPlayingIndex);
                                        } else if (currentLine.isRunning()) {
                                            currentLine.stop();
                                            pausePressed = true;
                                        } else {
                                            currentLine.start();
                                            pausePressed = false;
                                        }
                                        break;

                                    case KeyEvent.VK_DOWN:
                                        manualNavigation = true;
                                        if (currentPlayingIndex < wavFiles.size() - 1) {
                                            nextWav();
                                        }
                                        break;
                                    case KeyEvent.VK_UP:
                                        manualNavigation = true;
                                        if (currentPlayingIndex > 0) {
                                            previousWav();
                                        }
                                        break;
                                    case KeyEvent.VK_LEFT:
                                        changeGain(-0.1);
                                        break;
                                    case KeyEvent.VK_RIGHT:
                                        changeGain(0.1);
                                        break;
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }

                        }

                        return false;
                    }
                });

        frame.setVisible(true);
    }

    private static String unescapeJsonString(String input) {
        StringBuilder output = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escaped) {
                if (c == '"' || c == '\\' || c == '/') {
                    output.append(c);
                } else if (c == 'b') {
                    output.append('\b');
                } else if (c == 'f') {
                    output.append('\f');
                } else if (c == 'n') {
                    output.append('\n');
                } else if (c == 'r') {
                    output.append('\r');
                } else if (c == 't') {
                    output.append('\t');
                } else {
                    output.append('\\').append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                output.append(c);
            }
        }
        return output.toString();
    }

    private static String escapeJsonString(String input) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"' || c == '\\' || c == '/') {
                output.append('\\');
            }
            output.append(c);
        }
        return output.toString();
    }

    public static void importFromJson() {
        Component parent = null;
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter("JSON Files", "json");
        fileChooser.setFileFilter(filter);

        int returnValue = fileChooser.showOpenDialog(parent);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();

            try {
                String jsonString = new String(Files.readAllBytes(selectedFile.toPath()));
                Pattern folderPathPattern = Pattern.compile("\"folderPath\"\\s*:\\s*\"(.*?)\"");
                Matcher folderPathMatcher = folderPathPattern.matcher(jsonString);
                if (folderPathMatcher.find()) {
                    String folderPath = unescapeJsonString(folderPathMatcher.group(1));

                    Path folderPathObj = Paths.get(folderPath);
                    wavFiles = loadWavFiles(folderPathObj.toString());
                    gains = new double[wavFiles.size()];
                    wavTable.setModel(createTableModel());

                    // Set custom cell renderer
                    CustomTableCellRenderer customRenderer = new CustomTableCellRenderer();
                    for (int i = 0; i < wavTable.getColumnCount(); i++) {
                        wavTable.getColumnModel().getColumn(i).setCellRenderer(customRenderer);
                    }

                    shuffleTable();
                }

                Pattern filesPattern = Pattern.compile("\"files\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
                Matcher filesMatcher = filesPattern.matcher(jsonString);
                if (filesMatcher.find()) {
                    String filesArray = filesMatcher.group(1);
                    Pattern filePattern = Pattern.compile("\\{\\s*\"fileName\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"Gain\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\}");

                    Matcher fileMatcher = filePattern.matcher(filesArray);

                    DefaultTableModel tableModel = (DefaultTableModel) wavTable.getModel();
                    while (fileMatcher.find()) {
                        String name = unescapeJsonString(fileMatcher.group(1));
                        double gain = Double.parseDouble(fileMatcher.group(2));
                        for (int i = 0; i < tableModel.getRowCount(); i++) {
                            if (name.equals(tableModel.getValueAt(i, 0))) {
                                tableModel.setValueAt(gain, i, 1);
                                break;
                            }
                        }
                    }
                }

                wavTable.repaint();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void exportToJson() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export to JSON");
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        // Set default file name to the opened folder's name with a .json extension
        if (currentFolder != null) {
            String defaultFileName = currentFolder.getName() + ".json";
            fileChooser.setSelectedFile(new File(defaultFileName));
        }

        int result = fileChooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();

            try (FileWriter writer = new FileWriter(fileToSave)) {
                StringBuilder jsonString = new StringBuilder();
                jsonString.append("{\n");
                jsonString.append("  \"folderPath\": \"").append(escapeJsonString(currentFolder.getAbsolutePath())).append("\",\n");
                jsonString.append("  \"files\": [\n");

                for (int i = 0; i < wavFiles.size(); i++) {
                    String fileName = escapeJsonString(wavFiles.get(i).getName());
                    double gain = gains[i];
                    jsonString.append("    {\n");
                    jsonString.append("      \"fileName\": \"").append(fileName).append("\",\n");
                    jsonString.append("      \"Gain\": ").append(new DecimalFormat("#.##").format(gain)).append("\n");
                    jsonString.append("    }");

                    if (i < wavFiles.size() - 1) {
                        jsonString.append(",\n");
                    } else {
                        jsonString.append("\n");
                    }
                }

                jsonString.append("  ]\n");
                jsonString.append("}");

                writer.write(jsonString.toString());

            } catch (IOException e) {
                e.printStackTrace();

                // Show an error message dialog
                JOptionPane.showMessageDialog(null, "An error occurred while exporting to JSON. Please try again.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }


    private static JTable createWavTable() {

        CustomTableModel model = createTableModel();

//        CustomTableModel model = new CustomTableModel(data, columnNames);
        JTable table = new JTable(model);
        table.setCellSelectionEnabled(false);
        table.setFocusable(false);
        CustomTableCellRenderer customRenderer = new CustomTableCellRenderer();
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(customRenderer);
        }
        return table;
    }

    private static void playWav(int index) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        if (currentLine != null) {
            currentLine.stop();
            currentLine.close();
        }

        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(wavFiles.get(index));
        AudioFormat format = audioInputStream.getFormat();
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        currentLine = (SourceDataLine) AudioSystem.getLine(info);
        currentLine.open(format);

        currentPlayingIndex = index;
        applyGain();
        currentLine.start();
        wavTable.repaint();

        Thread playbackThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            int bytesRead = 0;
            try {
                while ((bytesRead = audioInputStream.read(buffer, 0, buffer.length)) != -1) {
                    currentLine.write(buffer, 0, bytesRead);
                }
                currentLine.drain();
                audioInputStream.close();

                if (!mouseClicked && !manualNavigation && !pausePressed) {
                    try {
                        if (currentPlayingIndex < wavFiles.size() - 1) {
                            nextWav();
                        } else {
                            currentPlayingIndex = 0;
                            shuffleTable();
                            playWav(currentPlayingIndex);
                        }
                    } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
                        ex.printStackTrace();
                    }
                }
                manualNavigation = false;
                pausePressed = false;
                mouseClicked = false;

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        playbackThread.start();
    }

    private static void shuffleTable() {
        List<Integer> indices = new ArrayList<>(wavFiles.size());
        for (int i = 0; i < wavFiles.size(); i++) {
            indices.add(i);
        }
        Collections.shuffle(indices);

        List<File> shuffledWavFiles = new ArrayList<>(wavFiles.size());
        double[] shuffledGains = new double[wavFiles.size()];

        for (int i = 0; i < indices.size(); i++) {
            int shuffledIndex = indices.get(i);
            shuffledWavFiles.add(wavFiles.get(shuffledIndex));
            shuffledGains[i] = gains[shuffledIndex];
            wavTable.setValueAt(shuffledWavFiles.get(i).getName(), i, 0);
            wavTable.setValueAt( new DecimalFormat("#.#").format(shuffledGains[i]), i, 1);
        }

        wavFiles = shuffledWavFiles;
        gains = shuffledGains;
    }

    private static void nextWav() throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        if (currentPlayingIndex < wavFiles.size() - 1) {
            currentPlayingIndex++;
            playWav(currentPlayingIndex);
            wavTable.setRowSelectionInterval(currentPlayingIndex, currentPlayingIndex);
        }
    }

    private static void previousWav() throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        if (currentPlayingIndex > 0) {
            currentPlayingIndex--;
            playWav(currentPlayingIndex);
            wavTable.setRowSelectionInterval(currentPlayingIndex, currentPlayingIndex);
        }
    }

    private static void changeGain(double delta) {
        gains[currentPlayingIndex] += delta;
        applyGain();
        wavTable.setValueAt(new DecimalFormat("#.#").format(gains[currentPlayingIndex]), currentPlayingIndex, 1);
    }

    private static void applyGain() {
        if (currentLine != null) {
            try {
                FloatControl gainControl = (FloatControl) currentLine.getControl(FloatControl.Type.MASTER_GAIN);
                float gainValue = (float) gains[currentPlayingIndex];
                float dB = (Math.min(gainControl.getMaximum(), Math.max(gainControl.getMinimum(), gainValue)));
                gainControl.setValue(dB);
            } catch (IllegalArgumentException ex) {
                ex.printStackTrace();
            }
        }
    }

    static class CustomTableCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (row == currentPlayingIndex) {
                cell.setBackground(Color.YELLOW);
            } else {
                cell.setBackground(table.getBackground());
            }
            return cell;
        }
    }

    static class CustomTableModel extends DefaultTableModel {
        CustomTableModel(Object[][] data, Object[] columnNames) {
            super(data, columnNames);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }
}


