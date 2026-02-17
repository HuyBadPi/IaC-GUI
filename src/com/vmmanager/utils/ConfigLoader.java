package com.vmmanager.utils;

import java.io.*;
import java.util.Properties;

public class ConfigLoader {
    private static ConfigLoader instance;
    private Properties properties;
    
    private ConfigLoader() {
        properties = new Properties();
        loadProperties();
    }
    
    public static ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }
    
    private void loadProperties() {
        // Thử đọc từ nhiều vị trí khác nhau
        String[] possiblePaths = {
            "config.properties",                    // Project root
            "./config.properties",                  // Current directory
            "../config.properties",                 // Parent directory
            System.getProperty("user.dir") + "/config.properties", // Absolute path
            "src/config.properties",                 // In src folder
        };
        
        boolean loaded = false;
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                try (InputStream input = new FileInputStream(file)) {
                    properties.load(input);
                    LoggerUtil.info("📁 Đã load config từ: " + file.getAbsolutePath());
                    loaded = true;
                    break;
                } catch (IOException e) {
                    LoggerUtil.warn("⚠️ Không thể đọc config từ: " + path);
                }
            }
        }
        
        if (!loaded) {
            LoggerUtil.warn("⚠️ Không tìm thấy file config, dùng config mặc định");
            setDefaultProperties();
            
            // Tạo file config mẫu
            createSampleConfig();
        }
    }
    
    private void createSampleConfig() {
        try {
            File sampleFile = new File("config.properties.sample");
            try (PrintWriter writer = new PrintWriter(new FileWriter(sampleFile))) {
                writer.println("# ============================================");
                writer.println("# PROXMOX VM MANAGER - SAMPLE CONFIGURATION");
                writer.println("# ============================================");
                writer.println("# Copy this file to config.properties and edit it");
                writer.println();
                writer.println("# Proxmox Configuration");
                writer.println("proxmox.host=192.168.1.100");
                writer.println("proxmox.user=root");
                writer.println("proxmox.password=your_password");
                writer.println("proxmox.realm=pam");
                writer.println("proxmox.node=pve");
                writer.println();
                writer.println("# Ansible Configuration");
                writer.println("ansible.ssh.user=root");
                writer.println("ansible.ssh.key=~/.ssh/id_rsa");
                writer.println();
                writer.println("# Application Settings");
                writer.println("use.real.api=false");
                writer.println("api.timeout.seconds=30");
                writer.println("debug.mode=false");
            }
            LoggerUtil.info("📝 Đã tạo file config mẫu: config.properties.sample");
        } catch (IOException e) {
            LoggerUtil.error("❌ Không thể tạo file config mẫu", e);
        }
    }
    
    private void setDefaultProperties() {
        properties.setProperty("proxmox.host", "192.168.1.100");
        properties.setProperty("proxmox.user", "root");
        properties.setProperty("proxmox.password", "password");
        properties.setProperty("proxmox.realm", "pam");
        properties.setProperty("proxmox.node", "pve");
        properties.setProperty("ansible.ssh.user", "root");
        properties.setProperty("ansible.ssh.key", "~/.ssh/id_rsa");
        properties.setProperty("use.real.api", "false");
        properties.setProperty("api.timeout.seconds", "30");
        properties.setProperty("debug.mode", "false");
    }
    
    /**
     * Lấy giá trị String từ config
     * @param key Tên key
     * @param defaultValue Giá trị mặc định nếu không tìm thấy
     * @return Giá trị String
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Lấy giá trị boolean từ config
     * @param key Tên key
     * @param defaultValue Giá trị mặc định nếu không tìm thấy
     * @return Giá trị boolean
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        
        // Xử lý các giá trị boolean phổ biến
        value = value.trim().toLowerCase();
        if (value.equals("true") || value.equals("yes") || value.equals("1") || value.equals("on")) {
            return true;
        }
        if (value.equals("false") || value.equals("no") || value.equals("0") || value.equals("off")) {
            return false;
        }
        
        // Nếu không parse được, trả về default
        LoggerUtil.warn("⚠️ Giá trị không hợp lệ cho key '" + key + "': " + value + ", dùng default: " + defaultValue);
        return defaultValue;
    }
    
    /**
     * Lấy giá trị int từ config
     * @param key Tên key
     * @param defaultValue Giá trị mặc định nếu không tìm thấy
     * @return Giá trị int
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LoggerUtil.warn("⚠️ Không thể parse số cho key '" + key + "': " + value + ", dùng default: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Lấy giá trị long từ config
     * @param key Tên key
     * @param defaultValue Giá trị mặc định nếu không tìm thấy
     * @return Giá trị long
     */
    public long getLongProperty(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LoggerUtil.warn("⚠️ Không thể parse số cho key '" + key + "': " + value + ", dùng default: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Lấy giá trị double từ config
     * @param key Tên key
     * @param defaultValue Giá trị mặc định nếu không tìm thấy
     * @return Giá trị double
     */
    public double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            LoggerUtil.warn("⚠️ Không thể parse số cho key '" + key + "': " + value + ", dùng default: " + defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Kiểm tra xem key có tồn tại không
     * @param key Tên key
     * @return true nếu key tồn tại
     */
    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * Lấy tất cả các key bắt đầu bằng prefix
     * @param prefix Tiền tố
     * @return Mảng các key
     */
    public String[] getKeysWithPrefix(String prefix) {
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .toArray(String[]::new);
    }
    
    /**
     * Set giá trị cho key (chỉ trong memory, không ghi file)
     * @param key Tên key
     * @param value Giá trị
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
    
    /**
     * Lưu config ra file
     * @param filePath Đường dẫn file
     * @return true nếu lưu thành công
     */
    public boolean saveToFile(String filePath) {
        try (OutputStream output = new FileOutputStream(filePath)) {
            properties.store(output, "Proxmox VM Manager Configuration");
            LoggerUtil.info("💾 Đã lưu config vào: " + filePath);
            return true;
        } catch (IOException e) {
            LoggerUtil.error("❌ Không thể lưu config", e);
            return false;
        }
    }
    
    /**
     * Reload config từ file
     */
    public void reload() {
        properties.clear();
        loadProperties();
        LoggerUtil.info("🔄 Đã reload config");
    }
    
    /**
     * In tất cả config hiện tại (debug)
     */
    public void printAllProperties() {
        LoggerUtil.info("📋 Current configuration:");
        properties.forEach((key, value) -> {
            // Che giấu password khi log
            if (key.toString().contains("password")) {
                LoggerUtil.debug("  " + key + " = ********");
            } else {
                LoggerUtil.debug("  " + key + " = " + value);
            }
        });
    }
}