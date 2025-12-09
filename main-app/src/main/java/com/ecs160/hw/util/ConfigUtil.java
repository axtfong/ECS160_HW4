package com.ecs160.hw.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigUtil {
    
    private static final String CONFIG_FILE = "config.properties";
    private static final String ENV_VAR_NAME = "GITHUB_API_KEY";

    public static String getGitHubApiKey() {
        // try env first
        String apiKey = System.getenv(ENV_VAR_NAME);
        
        // use properties file if no env
        if (apiKey == null || apiKey.isEmpty()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
                props.load(fis);
                apiKey = props.getProperty("github.api.key");
            } catch (IOException e) {
                System.out.println("No configuration file found. Please set up GITHUB_API_KEY environment variable or config.properties file.");
            }
        }
        
        return apiKey;
    }
}