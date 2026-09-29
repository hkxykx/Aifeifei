package com.fly.controller;

import com.fly.security.RequiresAuth;
import com.fly.service.UploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 图片上传（对应原 app/api/upload.py） */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/image")
    @RequiresAuth
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) {
        return uploadService.uploadImage(file);
    }
}
