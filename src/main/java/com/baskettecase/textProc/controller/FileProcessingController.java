package com.baskettecase.textProc.controller;

import com.baskettecase.textProc.service.FileProcessingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller for handling file processing information web interface.
 * Provides endpoints to view processed files and stream information.
 */
@Controller
public class FileProcessingController {

    private final FileProcessingService fileProcessingService;

    public FileProcessingController(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    /**
     * Displays a list of all processed files with their details.
     * @param model The model to add attributes to for the view.
     * @return The name of the Thymeleaf template to render.
     */
    @GetMapping({"/", "/files"})
    public String listProcessedFiles(Model model) {
        model.addAttribute("inputStream", fileProcessingService.getInputStreamName());
        model.addAttribute("outputStream", fileProcessingService.getOutputStreamName());
        model.addAttribute("files", fileProcessingService.getAllProcessedFiles());
        return "files";
    }

    /**
     * Serves processed text files for display in the browser.
     * Files are stored in /tmp with a standardized naming convention.
     * @param filename The safe filename to retrieve
     * @return The processed text content for display
     */
    @GetMapping("/processed-text/{filename}")
    public ResponseEntity<String> getProcessedText(@PathVariable String filename) {
        try {
            // Create the expected temp file path
            String safeFilename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path filePath = Paths.get("/tmp", "textproc_" + safeFilename + "_chunked.txt");
            
            if (!Files.exists(filePath)) {
                return new ResponseEntity<>("File not found: " + filename, HttpStatus.NOT_FOUND);
            }
            
            String content = Files.readString(filePath);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("Cache-Control", "no-cache");
            
            return new ResponseEntity<>(content, headers, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>("Error reading file: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
