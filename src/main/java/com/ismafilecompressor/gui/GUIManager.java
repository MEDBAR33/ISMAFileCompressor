package com.ismafilecompressor.gui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class GUIManager {
    private final Stage primaryStage;

    public GUIManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void showIntro() {
        try {
            // For now, just show a basic window
            // In a full implementation, you would load an FXML file
            primaryStage.setTitle("FileCompressor Pro");
            primaryStage.setWidth(800);
            primaryStage.setHeight(600);
            primaryStage.show();
        } catch (Exception e) {
            System.err.println("Failed to show GUI: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

