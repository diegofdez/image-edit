package com.diegofdez.tools.imageedit.api;

import com.diegofdez.tools.imageedit.api.dto.FileRequest;
import com.diegofdez.tools.imageedit.api.dto.GetMetadataResponse;
import com.diegofdez.tools.imageedit.service.ImageEditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(path = "/")
@Slf4j
public class ImageEditController {

    private ImageEditService service;

    @Autowired
    public ImageEditController(ImageEditService service) {
        this.service = service;
    }

    @PostMapping(value = "/file/metadata")
    public GetMetadataResponse getImageMetadataFromFile(FileRequest request) {
        log.info("Get metadata from file");
        service.getImageMetadataFromFile(request);
        return null;
    }

    @GetMapping(value = "/url/metadata")
    public GetMetadataResponse getImageMetadataFromUrl(@RequestParam("url") String fileUrl) {
        log.info("Get metadata from url: {}", fileUrl);
        return null;
    }
}
