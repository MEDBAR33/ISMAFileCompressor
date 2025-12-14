package com.ismafilecompressor.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;

public class GUIManager {
    private final Stage primaryStage;

    public GUIManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void showIntro() {
        try {
            // Create a simple welcome screen
            VBox root = new VBox(20);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(40));
            root.setStyle("-fx-background-color: #f5f5f5;");

            // Title
            Label titleLabel = new Label("ISMA FileCompressor");
            titleLabel.setFont(new Font("Arial", 32));
            titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #4361ee;");

            // Subtitle
            Label subtitleLabel = new Label("Smart File Compression Tool");
            subtitleLabel.setFont(new Font("Arial", 18));
            subtitleLabel.setStyle("-fx-text-fill: #666;");

            // Info text
            Label infoLabel = new Label(
                "The web interface is now running!\n" +
                "Open your browser to start compressing files."
            );
            infoLabel.setFont(new Font("Arial", 14));
            infoLabel.setTextAlignment(TextAlignment.CENTER);
            infoLabel.setStyle("-fx-text-fill: #333;");

            // Web link
            Hyperlink webLink = new Hyperlink("http://localhost:8080");
            webLink.setFont(new Font("Arial", 16));
            webLink.setStyle("-fx-text-fill: #4361ee; -fx-underline: true;");
            webLink.setOnAction(e -> {
                try {
                    Desktop.getDesktop().browse(new URI("http://localhost:8080"));
                } catch (Exception ex) {
                    System.err.println("Failed to open browser: " + ex.getMessage());
                }
            });

            // Open browser button
            Button openBrowserButton = new Button("Open in Browser");
            openBrowserButton.setStyle(
                "-fx-background-color: #4361ee; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 14px; " +
                "-fx-padding: 10px 20px; " +
                "-fx-background-radius: 5px;"
            );
            openBrowserButton.setOnAction(e -> {
                try {
                    Desktop.getDesktop().browse(new URI("http://localhost:8080"));
                } catch (Exception ex) {
                    System.err.println("Failed to open browser: " + ex.getMessage());
                }
            });

            // Status label
            Label statusLabel = new Label("✓ Web server is running on port 8080");
            statusLabel.setFont(new Font("Arial", 12));
            statusLabel.setStyle("-fx-text-fill: #28a745;");

            root.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                infoLabel,
                webLink,
                openBrowserButton,
                statusLabel
            );

            Scene scene = new Scene(root, 600, 400);
            primaryStage.setTitle("ISMA FileCompressor");
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
        } catch (Exception e) {
            System.err.println("Failed to show GUI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

