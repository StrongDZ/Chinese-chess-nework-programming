#ifndef CONFIG_LOADER_H
#define CONFIG_LOADER_H

#include <string>
#include <map>
#include <vector>
#include <algorithm>
#include <fstream>
#include <iostream>
#include <sstream>

/**
 * @brief Simple .env file loader for configuration management
 * 
 * Loads key-value pairs from .env file and provides type-safe getters.
 * Supports string, int, bool types and default values.
 */
class ConfigLoader {
private:
    std::map<std::string, std::string> config;
    bool loaded;
    
    // Trim whitespace from string
    static std::string trim(const std::string& str) {
        size_t first = str.find_first_not_of(" \t\r\n");
        if (first == std::string::npos) return "";
        size_t last = str.find_last_not_of(" \t\r\n");
        return str.substr(first, (last - first + 1));
    }
    
public:
    ConfigLoader() : loaded(false) {}
    
    /**
     * @brief Load configuration from .env file
     * @param envPath Path to .env file (default: ".env")
     * @return true if loaded successfully, false otherwise
     */
    bool load(const std::string& envPath = ".env") {
        std::ifstream file(envPath);
        if (!file.is_open()) {
            std::cerr << "Failed to open .env file: " << envPath << std::endl;
            std::cerr << "Please copy .env.example to .env and configure it." << std::endl;
            return false;
        }
        
        std::string line;
        int lineNum = 0;
        
        while (std::getline(file, line)) {
            lineNum++;
            line = trim(line);
            
            // Skip empty lines and comments
            if (line.empty() || line[0] == '#') {
                continue;
            }
            
            // Find '=' separator
            size_t equalPos = line.find('=');
            if (equalPos == std::string::npos) {
                std::cerr << "Warning: Invalid line " << lineNum << " in .env: " << line << std::endl;
                continue;
            }
            
            std::string key = trim(line.substr(0, equalPos));
            std::string value = trim(line.substr(equalPos + 1));
            
            // Remove quotes from value if present
            if (value.length() >= 2) {
                if ((value.front() == '"' && value.back() == '"') ||
                    (value.front() == '\'' && value.back() == '\'')) {
                    value = value.substr(1, value.length() - 2);
                }
            }
            
            config[key] = value;
        }
        
        file.close();
        loaded = true;
        
        std::cout << "Loaded " << config.size() << " configuration entries from " << envPath << std::endl;
        return true;
    }
    
    /**
     * @brief Check if configuration was loaded successfully
     */
    bool isLoaded() const {
        return loaded;
    }
    
    /**
     * @brief Get string value from configuration
     * @param key Configuration key
     * @param defaultValue Default value if key not found
     * @return Configuration value or default
     */
    std::string getString(const std::string& key, const std::string& defaultValue = "") const {
        auto it = config.find(key);
        if (it != config.end()) {
            return it->second;
        }
        return defaultValue;
    }
    
    /**
     * @brief Get integer value from configuration
     * @param key Configuration key
     * @param defaultValue Default value if key not found or conversion fails
     * @return Configuration value or default
     */
    int getInt(const std::string& key, int defaultValue = 0) const {
        auto it = config.find(key);
        if (it != config.end()) {
            try {
                return std::stoi(it->second);
            } catch (const std::exception& e) {
                std::cerr << "Warning: Invalid integer for key '" << key << "': " << it->second << std::endl;
            }
        }
        return defaultValue;
    }
    
    /**
     * @brief Get boolean value from configuration
     * @param key Configuration key
     * @param defaultValue Default value if key not found
     * @return Configuration value or default
     * 
     * Accepts: true/false, 1/0, yes/no, on/off (case-insensitive)
     */
    bool getBool(const std::string& key, bool defaultValue = false) const {
        auto it = config.find(key);
        if (it != config.end()) {
            std::string value = it->second;
            // Convert to lowercase
            std::transform(value.begin(), value.end(), value.begin(), ::tolower);
            
            if (value == "true" || value == "1" || value == "yes" || value == "on") {
                return true;
            }
            if (value == "false" || value == "0" || value == "no" || value == "off") {
                return false;
            }
        }
        return defaultValue;
    }
    
    /**
     * @brief Check if a key exists in configuration
     */
    bool hasKey(const std::string& key) const {
        return config.find(key) != config.end();
    }
    
    /**
     * @brief Get all configuration keys
     */
    std::vector<std::string> getKeys() const {
        std::vector<std::string> keys;
        for (const auto& pair : config) {
            keys.push_back(pair.first);
        }
        return keys;
    }
    
    /**
     * @brief Print all configuration (for debugging)
     * WARNING: This may expose sensitive data! Use only in development.
     */
    void printConfig() const {
        std::cout << "\n=== Configuration ===" << std::endl;
        for (const auto& pair : config) {
            // Hide sensitive values
            std::string value = pair.second;
            if (pair.first.find("PASSWORD") != std::string::npos ||
                pair.first.find("SECRET") != std::string::npos ||
                pair.first.find("CONNECTION_STRING") != std::string::npos) {
                value = "***HIDDEN***";
            }
            std::cout << pair.first << " = " << value << std::endl;
        }
        std::cout << "=====================\n" << std::endl;
    }
};

#endif // CONFIG_LOADER_H
