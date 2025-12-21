package com.ismafilecompressor.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.Properties;

public class AppConfig {
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties props = new Properties();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                try (FileInputStream fis = new FileInputStream(configFile)) {
                    props.load(fis);
                }
                // Validate and fix output folder if it's invalid
                String outputFolder = props.getProperty("output.defaultFolder", "");
                if (!outputFolder.isEmpty()) {
                    // Check if path contains /root or \root (invalid for cloud deployments)
                    if (outputFolder.contains("/root") || outputFolder.contains("\\root") || 
                        outputFolder.contains("moham")) {
                        // Regenerate with proper path
                        props.setProperty("output.defaultFolder", getDefaultOutputFolder());
                        saveConfig();
                    }
                }
            } else {
                setDefaults();
                saveConfig();
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load config file, using defaults: " + e.getMessage());
            setDefaults();
        }
    }

    /**
     * Gets the OS-specific output directory for compressed files.
     * Returns a user-friendly path that exists and is accessible.
     */
    private static String getDefaultOutputFolder() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        String userHome = System.getProperty("user.home", "");
        String userName = System.getProperty("user.name", "");
        
        // Handle Windows
        if (osName.contains("win")) {
            // Windows: C:\Users\{username}\CompressedFiles
            if (userHome.isEmpty()) {
                // Fallback if user.home is not set
                return "C:\\Users\\" + (userName.isEmpty() ? "User" : userName) + "\\CompressedFiles";
            }
            // Use user.home and ensure backslashes
            return Paths.get(userHome, "CompressedFiles").toString();
        }
        
        // Handle macOS
        if (osName.contains("mac") || osName.contains("darwin")) {
            // macOS: /Users/{username}/CompressedFiles
            if (userHome.isEmpty()) {
                return "/Users/" + (userName.isEmpty() ? "user" : userName) + "/CompressedFiles";
            }
            return Paths.get(userHome, "CompressedFiles").toString();
        }
        
        // Handle Linux/Unix
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            // Linux: /home/{username}/CompressedFiles
            if (userHome.isEmpty()) {
                return "/home/" + (userName.isEmpty() ? "user" : userName) + "/CompressedFiles";
            }
            return Paths.get(userHome, "CompressedFiles").toString();
        }
        
        // Handle mobile devices (Android/iOS) - Note: This is server-side, so paths are on server
        // For mobile browsers accessing the web app, files are saved on the server
        // But we want user-friendly paths for display
        
        // Check if user.home points to /root (common on Linux servers like Railway)
        if (userHome.equals("/root") || userHome.equals("\\root")) {
            // For cloud servers (Railway, etc.), use the app directory instead of /root
            // This is more reliable and accessible
            String appDir = System.getProperty("user.dir", ".");
            
            // Try app directory first (most reliable for Railway)
            String[] serverPaths = {
                appDir + "/output/CompressedFiles",
                appDir + "/CompressedFiles",
                "/app/output/CompressedFiles",  // Railway Docker default
                "/app/CompressedFiles",         // Railway Docker default
                System.getProperty("java.io.tmpdir", "/tmp") + "/CompressedFiles"
            };
            
            for (String path : serverPaths) {
                try {
                    File dir = new File(path);
                    File parent = dir.getParentFile();
                    // Try to create parent directory if it exists or is app directory
                    if (parent != null && (parent.exists() || parent.getAbsolutePath().equals(appDir) || parent.getAbsolutePath().equals("/app"))) {
                        dir.mkdirs();
                        if (dir.exists() && dir.isDirectory()) {
                            return Paths.get(path).toString();
                        }
                    }
                } catch (Exception e) {
                    // Continue to next path
                }
            }
            
            // Last resort: use app directory even if we can't verify
            try {
                String fallbackPath = Paths.get(appDir, "CompressedFiles").toString();
                File fallbackDir = new File(fallbackPath);
                fallbackDir.mkdirs();
                return fallbackPath;
            } catch (Exception e) {
                // Continue to next fallback
            }
        }
        
        // For Android/iOS devices running the server (unlikely but possible)
        if (osName.contains("android")) {
            // Try common Android paths
            String[] androidPaths = {
                "/storage/emulated/0/Download/CompressedFiles",
                "/storage/emulated/0/Documents/CompressedFiles",
                "/sdcard/Download/CompressedFiles",
                userHome + "/Download/CompressedFiles",
                userHome + "/Documents/CompressedFiles"
            };
            
            for (String path : androidPaths) {
                try {
                    File dir = new File(path);
                    File parent = dir.getParentFile();
                    if (parent != null && (parent.exists() || parent.mkdirs())) {
                        dir.mkdirs();
                        if (dir.exists() && dir.isDirectory()) {
                            return Paths.get(path).toString();
                        }
                    }
                } catch (Exception e) {
                    // Continue to next path
                }
            }
        }
        
        // iOS or other Unix-like systems
        // Try common locations
        String[] commonPaths = {
            userHome + "/Documents/CompressedFiles",
            userHome + "/Downloads/CompressedFiles",
            userHome + "/CompressedFiles"
        };
        
        for (String path : commonPaths) {
            if (!path.isEmpty()) {
                try {
                    File dir = new File(path);
                    File parent = dir.getParentFile();
                    if (parent != null && (parent.exists() || parent.mkdirs())) {
                        dir.mkdirs();
                        if (dir.exists() && dir.isDirectory()) {
                            return Paths.get(path).toString();
                        }
                    }
                } catch (Exception e) {
                    // Continue to next path
                }
            }
        }
        
        // Final fallback: use user.home if available
        if (!userHome.isEmpty()) {
            try {
                String path = Paths.get(userHome, "CompressedFiles").toString();
                File dir = new File(path);
                dir.mkdirs();
                return path;
            } catch (Exception e) {
                // Continue to last resort
            }
        }
        
        // Last resort: current directory or temp directory
        try {
            String currentDir = System.getProperty("user.dir", ".");
            String path = Paths.get(currentDir, "CompressedFiles").toString();
            File dir = new File(path);
            dir.mkdirs();
            return path;
        } catch (Exception e) {
            return Paths.get(System.getProperty("java.io.tmpdir", "/tmp"), "CompressedFiles").toString();
        }
    }

    private static void setDefaults() {
        // Web Server
        props.setProperty("web.port", "8080");
        props.setProperty("web.enabled", "true");
        props.setProperty("web.maxFileSize", "104857600"); // 100MB

        // Compression
        props.setProperty("compression.defaultQuality", "75");
        props.setProperty("compression.defaultLevel", "balanced");
        props.setProperty("compression.threads", String.valueOf(Runtime.getRuntime().availableProcessors()));

        // Output - Use OS-aware path
        props.setProperty("output.defaultFolder", getDefaultOutputFolder());
        props.setProperty("output.keepOriginals", "true");

        // UI
        props.setProperty("ui.theme", "dark");
        props.setProperty("ui.animationSpeed", "normal");

        // Formats
        props.setProperty("formats.enabled", "jpg,png,pdf,webp,doc,docx,mp4,mp3,zip");
    }

    public static void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "FileCompressor Pro Configuration");
        } catch (Exception e) {
            System.err.println("Warning: Could not save config file: " + e.getMessage());
        }
    }

    // Getters
    public static int getWebPort() {
        return Integer.parseInt(props.getProperty("web.port", "8080"));
    }

    public static boolean isWebEnabled() {
        return Boolean.parseBoolean(props.getProperty("web.enabled", "true"));
    }

    public static long getMaxFileSize() {
        return Long.parseLong(props.getProperty("web.maxFileSize", "104857600"));
    }

    public static int getDefaultQuality() {
        return Integer.parseInt(props.getProperty("compression.defaultQuality", "75"));
    }

    public static String getDefaultLevel() {
        return props.getProperty("compression.defaultLevel", "balanced");
    }

    public static int getThreadCount() {
        return Integer.parseInt(props.getProperty("compression.threads",
                String.valueOf(Runtime.getRuntime().availableProcessors())));
    }

    public static String getOutputFolder() {
        String configuredFolder = props.getProperty("output.defaultFolder");
        String finalPath;
        String currentUserHome = System.getProperty("user.home", "");
        String osName = System.getProperty("os.name", "").toLowerCase();
        
        if (configuredFolder != null && !configuredFolder.trim().isEmpty()) {
            // Validate the configured folder - check for invalid paths
            boolean needsRegeneration = false;
            
            // Check for hardcoded username (like "moham")
            if (configuredFolder.contains("moham")) {
                needsRegeneration = true;
            }
            
            // Check for /root paths (common on Railway/Linux servers)
            if (configuredFolder.contains("\\root") || configuredFolder.contains("/root")) {
                needsRegeneration = true;
            }
            
            // Check if Windows path doesn't match current user
            if (osName.contains("win") && !configuredFolder.contains(currentUserHome) && !currentUserHome.isEmpty()) {
                needsRegeneration = true;
            }
            
            // Check if path is /root but we're on a server (should use app directory)
            if (configuredFolder.startsWith("/root") || configuredFolder.startsWith("\\root")) {
                needsRegeneration = true;
            }
            
            if (needsRegeneration) {
                // Regenerate with current user
                finalPath = getDefaultOutputFolder();
                props.setProperty("output.defaultFolder", finalPath);
                saveConfig();
            } else {
                finalPath = configuredFolder;
            }
        } else {
            // If not configured, get the default OS-aware path
            finalPath = getDefaultOutputFolder();
        }
        
        // Ensure the directory exists
        try {
            File outputDir = new File(finalPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            // Return the canonical path for better display
            return outputDir.getCanonicalPath();
        } catch (Exception e) {
            // If we can't create or get canonical path, return the original
            return finalPath;
        }
    }

    public static boolean keepOriginals() {
        return Boolean.parseBoolean(props.getProperty("output.keepOriginals", "true"));
    }

    public static String getTheme() {
        return props.getProperty("ui.theme", "dark");
    }

    public static String[] getEnabledFormats() {
        return props.getProperty("formats.enabled", "jpg,png,pdf").split(",");
    }

    // Setters
    public static void setWebPort(int port) {
        props.setProperty("web.port", String.valueOf(port));
        saveConfig();
    }

    public static void setWebEnabled(boolean enabled) {
        props.setProperty("web.enabled", String.valueOf(enabled));
        saveConfig();
    }

    public static void setDefaultQuality(int quality) {
        props.setProperty("compression.defaultQuality", String.valueOf(quality));
        saveConfig();
    }

    public static void setDefaultLevel(String level) {
        props.setProperty("compression.defaultLevel", level);
        saveConfig();
    }

    public static void setOutputFolder(String folder) {
        props.setProperty("output.defaultFolder", folder);
        saveConfig();
    }

    public static void setTheme(String theme) {
        props.setProperty("ui.theme", theme);
        saveConfig();
    }
}