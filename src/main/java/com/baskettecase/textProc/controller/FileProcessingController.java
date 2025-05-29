package com.baskettecase.textProc.controller;

import com.baskettecase.textProc.service.FileProcessingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller for handling file processing information web interface.
 * Provides endpoints to view processed files and stream information.
 */
@Controller
@RequestMapping("/files")
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
    @GetMapping
    public String listProcessedFiles(Model model) {
        model.addAttribute("inputStream", fileProcessingService.getInputStreamName());
        model.addAttribute("outputStream", fileProcessingService.getOutputStreamName());
        model.addAttribute("files", fileProcessingService.getAllProcessedFiles());
        return "files";
    }
}
