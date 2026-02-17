package com.vmmanager.config;

import com.google.gson.Gson;
import java.nio.file.*;
import java.io.IOException;

public class ConfigService {

    private static final Path FILE =
        Paths.get(System.getProperty("user.home"), ".vmmanager.json");

    private static final Gson gson = new Gson();

    public static GlobalConfig load() {
        try {
            if(Files.exists(FILE)){
                String json = Files.readString(FILE);
                return gson.fromJson(json, GlobalConfig.class);
            }
        } catch (Exception ignored){}
        return new GlobalConfig();
    }

    public static void save(GlobalConfig cfg){
        try {
            Files.writeString(FILE, gson.toJson(cfg));
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
