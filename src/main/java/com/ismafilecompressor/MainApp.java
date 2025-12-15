package com.ismafilecompressor;

import com.ismafilecompressor.web.WebServer;

public class MainApp {
    private static WebServer webServer;

    public static void main(String[] args) {
        System.out.println("🚀 Starting ISMA FileCompressor...");
        
        // Read port from environment variable (for cloud hosting like Render)
        // Default to 8080 if not set
        String portEnv = System.getenv("PORT");
        int port = 8080;
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
                System.out.println("📡 Using PORT from environment: " + port);
            } catch (NumberFormatException e) {
                System.err.println("⚠️  Invalid PORT environment variable: " + portEnv + ", using default 8080");
            }
        } else {
            System.out.println("📡 Using default port: 8080");
        }
        
        try {
            // Start Web Server
            webServer = new WebServer(port);
            webServer.start();
            
            System.out.println("✅ Web server started successfully!");
            System.out.println("🌐 Web Interface available on port: " + port);
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Shutting down server...");
                if (webServer != null) {
                    webServer.stop();
                }
            }));
            
            // Keep the application running (for cloud hosting)
            // The server will run until the process is terminated
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            System.out.println("👋 Server interrupted. Shutting down...");
            Thread.currentThread().interrupt();
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