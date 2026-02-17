package com.vmmanager.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LoggerUtil {
    private static final SimpleDateFormat dateFormat = 
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static PrintWriter fileWriter;
    
    static {
        try {
            fileWriter = new PrintWriter(new FileWriter("app.log", true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static void info(String message) {
        log("INFO", message);
    }
    
    public static void warn(String message) {
        log("WARN", message);
    }
    
    public static void error(String message) {
        log("ERROR", message);
    }
    
    public static void error(String message, Exception e) {
        log("ERROR", message + " - " + e.getMessage());
        e.printStackTrace();
    }
    
    public static void debug(String message) {
        log("DEBUG", message);
    }
    
    private static void log(String level, String message) {
        String timestamp = dateFormat.format(new Date());
        String logMessage = String.format("[%s] %s: %s", timestamp, level, message);
        
        // In ra console
        System.out.println(logMessage);
        
        // Ghi ra file
        if (fileWriter != null) {
            fileWriter.println(logMessage);
            fileWriter.flush();
        }
    }
}