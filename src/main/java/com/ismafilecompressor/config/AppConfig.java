package com.ismafilecompressor.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
            } else {
                setDefaults();
                saveConfig();
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load config file, using defaults: " + e.getMessage());
            setDefaults();
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

        // Output
        props.setProperty("output.defaultFolder", System.getProperty("user.home") + "/CompressedFiles");
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
        return props.getProperty("output.defaultFolder",
                System.getProperty("user.home") + "/CompressedFiles");
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