package com.ismafilecompressor;

import com.ismafilecompressor.gui.GUIManager;
import com.ismafilecompressor.web.WebServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class MainApp extends Application {
    private WebServer webServer;
    private static final int WEB_PORT = 8080;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Start JavaFX GUI
        GUIManager gui = new GUIManager(primaryStage);
        gui.showIntro();

        // Start Web Server in background
        startWebServer();

        System.out.println("🚀 Application Started!");
        System.out.println("🌐 Web Interface: http://localhost:" + WEB_PORT);
        System.out.println("💻 Desktop App: JavaFX Interface");
    }

    private void startWebServer() {
        try {
            webServer = new WebServer(WEB_PORT);
            new Thread(() -> {
                webServer.start();
            }, "web-server").start();
        } catch (Exception e) {
            System.err.println("Failed to start web server: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        // Cleanup
        if (webServer != null) {
            webServer.stop();
        }
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}