package com.ismafilecompressor.gui;

import com.ismafilecompressor.model.*;
import com.ismafilecompressor.service.CompressionService;
import com.ismafilecompressor.util.FileManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainController {
    @FXML private BorderPane rootPane;
    @FXML private ListView<File> fileListView;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private ComboBox<String> qualityComboBox;
    @FXML private CheckBox resizeCheckBox;
    @FXML private TextField maxWidthField;
    @FXML private TextField maxHeightField;
    @FXML private CheckBox convertPngCheckBox;
    @FXML private TextField outputFolderField;
    @FXML private Button browseOutputButton;
    @FXML private Button compressButton;
    @FXML private Button clearButton;
    @FXML private Button addFilesButton;
    @FXML private StackPane overlayPane;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label loadingLabel;
    @FXML private VBox resultsContainer;
    @FXML private Label resultsLabel;

    private final CompressionService compressionService;
    private final ObservableList<File> fileList;
    private final ExecutorService executorService;
    private CompressionOptions currentOptions;
    private Stage stage;

    public MainController() {
        this.compressionService = new CompressionService();
        this.fileList = FXCollections.observableArrayList();
        this.executorService = Executors.newSingleThreadExecutor();
        this.currentOptions = new CompressionOptions();
    }

    @FXML
    public void initialize() {
        // Setup file list
        fileListView.setItems(fileList);
        fileListView.setCellFactory(lv -> new FileListCell());

        // Setup drag and drop
        setupDragAndDrop();

        // Setup quality combo box
        qualityComboBox.getItems().addAll(
                "Maximum Compression",
                "Balanced",
                "Best Quality",
                "Custom"
        );
        qualityComboBox.setValue("Balanced");
        qualityComboBox.setOnAction(e -> updateQualitySettings());

        // Setup output folder
        outputFolderField.setText(currentOptions.getOutputDirectory());
        browseOutputButton.setOnAction(e -> browseOutputFolder());

        // Setup buttons
        addFilesButton.setOnAction(e -> addFiles());
        clearButton.setOnAction(e -> clearFiles());
        compressButton.setOnAction(e -> compressFiles());

        // Setup resize controls
        resizeCheckBox.selectedProperty().addListener((obs, old, newVal) -> {
            maxWidthField.setDisable(!newVal);
            maxHeightField.setDisable(!newVal);
        });

        // Initialize with defaults
        updateQualitySettings();

        // Hide overlay initially
        overlayPane.setVisible(false);
        resultsContainer.setVisible(false);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void setupDragAndDrop() {
        fileListView.setOnDragOver(event -> {
            if (event.getGestureSource() != fileListView &&
                    event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        fileListView.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasFiles()) {
                List<File> validFiles = new ArrayList<>();
                for (File file : db.getFiles()) {
                    if (FileManager.isSupportedFile(file)) {
                        validFiles.add(file);
                    }
                }

                if (!validFiles.isEmpty()) {
                    fileList.addAll(validFiles);
                    success = true;
                    updateUI();
                }
            }

            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void updateQualitySettings() {
        String selected = qualityComboBox.getValue();

        switch (selected) {
            case "Maximum Compression":
                currentOptions.setCompressionLevel(CompressionOptions.CompressionLevel.MAXIMUM);
                resizeCheckBox.setSelected(true);
                convertPngCheckBox.setSelected(true);
                maxWidthField.setText("1280");
                maxHeightField.setText("720");
                break;

            case "Balanced":
                currentOptions.setCompressionLevel(CompressionOptions.CompressionLevel.BALANCED);
                resizeCheckBox.setSelected(false);
                convertPngCheckBox.setSelected(true);
                maxWidthField.setText("1920");
                maxHeightField.setText("1080");
                break;

            case "Best Quality":
                currentOptions.setCompressionLevel(CompressionOptions.CompressionLevel.BEST_QUALITY);
                resizeCheckBox.setSelected(false);
                convertPngCheckBox.setSelected(false);
                maxWidthField.setText("3840");
                maxHeightField.setText("2160");
                break;

            case "Custom":
                // Keep current settings
                break;
        }

        // Update current options
        currentOptions.setResizeImages(resizeCheckBox.isSelected());
        currentOptions.setMaxWidth(Integer.parseInt(maxWidthField.getText()));
        currentOptions.setMaxHeight(Integer.parseInt(maxHeightField.getText()));
        currentOptions.setConvertPngToJpeg(convertPngCheckBox.isSelected());
    }

    private void browseOutputFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Folder");

        File currentFolder = new File(outputFolderField.getText());
        if (currentFolder.exists()) {
            chooser.setInitialDirectory(currentFolder);
        }

        File selected = chooser.showDialog(stage);
        if (selected != null) {
            outputFolderField.setText(selected.getAbsolutePath());
            currentOptions.setOutputDirectory(selected.getAbsolutePath());
        }
    }

    private void addFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Files to Compress");

        // Add extension filters
        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter(
                "All Supported Files",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.tiff", "*.tif", "*.webp",
                "*.pdf", "*.doc", "*.docx", "*.ppt", "*.pptx", "*.xls", "*.xlsx",
                "*.mp3", "*.wav", "*.flac", "*.aac", "*.ogg",
                "*.mp4", "*.avi", "*.mkv", "*.mov", "*.wmv",
                "*.zip", "*.rar", "*.7z", "*.tar", "*.gz"
        );

        chooser.getExtensionFilters().add(allFilter);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp", "*.tiff", "*.tif", "*.webp"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Documents", "*.pdf"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Office Documents", "*.doc", "*.docx", "*.ppt", "*.pptx", "*.xls", "*.xlsx"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.flac", "*.aac", "*.ogg"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.avi", "*.mkv", "*.mov", "*.wmv"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Archive Files", "*.zip", "*.rar", "*.7z", "*.tar", "*.gz"));

        List<File> selectedFiles = chooser.showOpenMultipleDialog(stage);
        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            fileList.addAll(selectedFiles);
            updateUI();
        }
    }

    private void clearFiles() {
        fileList.clear();
        updateUI();
    }

    private void compressFiles() {
        if (fileList.isEmpty()) {
            showAlert("No Files", "Please add files to compress.");
            return;
        }

        // Update options from UI
        updateCurrentOptions();

        // Show loading overlay
        showLoading(true, "Compressing " + fileList.size() + " file(s)...");

        // Disable compress button
        compressButton.setDisable(true);

        // Start compression in background thread
        executorService.submit(() -> {
            try {
                CompressionResult result = compressionService.compressFiles(
                        new ArrayList<>(fileList), currentOptions);

                Platform.runLater(() -> {
                    showLoading(false, "");
                    compressButton.setDisable(false);
                    showResults(result);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showLoading(false, "");
                    compressButton.setDisable(false);
                    showAlert("Compression Error", "Failed to compress files: " + e.getMessage());
                });
            }
        });
    }

    private void updateCurrentOptions() {
        currentOptions.setResizeImages(resizeCheckBox.isSelected());
        try {
            currentOptions.setMaxWidth(Integer.parseInt(maxWidthField.getText()));
            currentOptions.setMaxHeight(Integer.parseInt(maxHeightField.getText()));
        } catch (NumberFormatException e) {
            // Use defaults if invalid
            currentOptions.setMaxWidth(1920);
            currentOptions.setMaxHeight(1080);
        }
        currentOptions.setConvertPngToJpeg(convertPngCheckBox.isSelected());

        String outputDir = outputFolderField.getText();
        if (outputDir != null && !outputDir.trim().isEmpty()) {
            currentOptions.setOutputDirectory(outputDir);
        }
    }

    private void updateUI() {
        // Update UI based on file list
        if (fileList.isEmpty()) {
            compressButton.setDisable(true);
        } else {
            compressButton.setDisable(false);
        }
    }

    private void showLoading(boolean show, String message) {
        Platform.runLater(() -> {
            overlayPane.setVisible(show);
            if (show) {
                loadingLabel.setText(message);
            }
        });
    }

    private void showResults(CompressionResult result) {
        resultsContainer.setVisible(true);
        resultsLabel.setText(String.format(
                "Compressed %d files. Saved: %s (%.1f%%)",
                result.getFilesProcessed() - result.getFilesFailed(),
                result.getFormattedTotalSaved(),
                result.getOverallCompressionRatio()
        ));
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Inner class for custom ListCell
    private static class FileListCell extends ListCell<File> {
        @Override
        protected void updateItem(File file, boolean empty) {
            super.updateItem(file, empty);
            if (empty || file == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(file.getName() + " (" + FileManager.formatFileSize(file.length()) + ")");
            }
        }
    }
}