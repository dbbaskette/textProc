package com.baskettecase.textProc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Service for handling HDFS operations.
 * Provides functionality to interact with HDFS via WebHDFS REST API.
 */
@Service
public class HdfsService {
    private static final Logger logger = LoggerFactory.getLogger(HdfsService.class);
    
    @Value("${app.hdfs.base-url:http://35.196.56.130:9870/webhdfs/v1}")
    private String hdfsBaseUrl;
    
    @Value("${app.hdfs.processed-files-path:/processed_files}")
    private String processedFilesPath;
    
    /**
     * Deletes the processed files directory from HDFS.
     * This is used during reset operations to clean up all processed files.
     * 
     * @return true if the directory was successfully deleted, false otherwise
     */
    public boolean deleteProcessedFilesDirectory() {
        try {
            // Construct the WebHDFS DELETE URL for the processed files directory
            String deleteUrl = hdfsBaseUrl + processedFilesPath + "?op=DELETE&recursive=true";
            
            logger.info("Attempting to delete processed files directory: {}", deleteUrl);
            
            URL url = new URL(deleteUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                logger.info("Successfully deleted processed files directory from HDFS");
                return true;
            } else {
                logger.warn("Failed to delete processed files directory. Response code: {}", responseCode);
                return false;
            }
            
        } catch (IOException e) {
            logger.error("Error deleting processed files directory from HDFS", e);
            return false;
        }
    }
    
    /**
     * Checks if the processed files directory exists in HDFS.
     * 
     * @return true if the directory exists, false otherwise
     */
    public boolean processedFilesDirectoryExists() {
        try {
            // Construct the WebHDFS GETFILESTATUS URL for the processed files directory
            String statusUrl = hdfsBaseUrl + processedFilesPath + "?op=GETFILESTATUS";
            
            URL url = new URL(statusUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            return responseCode == 200;
            
        } catch (IOException e) {
            logger.debug("Error checking if processed files directory exists", e);
            return false;
        }
    }
    
    /**
     * Creates the processed files directory in HDFS if it doesn't exist.
     * 
     * @return true if the directory was created or already exists, false otherwise
     */
    public boolean createProcessedFilesDirectory() {
        try {
            // First check if directory already exists
            if (processedFilesDirectoryExists()) {
                logger.debug("Processed files directory already exists");
                return true;
            }
            
            // Construct the WebHDFS MKDIRS URL for the processed files directory
            String mkdirUrl = hdfsBaseUrl + processedFilesPath + "?op=MKDIRS";
            
            logger.info("Creating processed files directory: {}", mkdirUrl);
            
            URL url = new URL(mkdirUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                logger.info("Successfully created processed files directory in HDFS");
                return true;
            } else {
                logger.warn("Failed to create processed files directory. Response code: {}", responseCode);
                return false;
            }
            
        } catch (IOException e) {
            logger.error("Error creating processed files directory in HDFS", e);
            return false;
        }
    }
} 