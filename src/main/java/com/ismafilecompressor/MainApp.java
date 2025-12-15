package com.ismafilecompressor;

import com.ismafilecompressor.web.WebServer;
import java.util.Scanner;

public class MainApp {
    private static WebServer webServer;
    private static final int WEB_PORT = 8080;

    public static void main(String[] args) {
        System.out.println("🚀 Starting ISMA FileCompressor...");
        
        try {
            // Start Web Server
            webServer = new WebServer(WEB_PORT);
            webServer.start();
            
            System.out.println("✅ Web server started successfully!");
            System.out.println("🌐 Web Interface: http://localhost:" + WEB_PORT);
            System.out.println("📝 Open your browser and navigate to the URL above");
            System.out.println("⏹️  Press 'q' and Enter to stop the server");
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Shutting down server...");
                if (webServer != null) {
                    webServer.stop();
                }
            }));
            
            // Wait for user input to stop
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                if ("q".equalsIgnoreCase(input.trim())) {
                    break;
                }
            }
            scanner.close();
            
        } catch (Exception e) {
            System.err.println("❌ Failed to start web server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (webServer != null) {
                webServer.stop();
            }
            System.out.println("👋 Server stopped. Goodbye!");
        }
    }
}