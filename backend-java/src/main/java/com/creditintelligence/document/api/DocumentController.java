package com.creditintelligence.document.api;

import com.creditintelligence.document.application.DocumentService;
import com.creditintelligence.document.application.DocumentView;
import com.creditintelligence.document.domain.DocumentType;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/proposals/{proposalId}/documents")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentView> upload(@PathVariable UUID proposalId,
            @RequestParam DocumentType type, @RequestParam(required = false) String period,
            @RequestParam MultipartFile file) throws IOException {
        var result = service.upload(proposalId, type, period, file.getContentType(), file.getBytes());
        return ResponseEntity.created(URI.create("/api/v1/proposals/" + proposalId + "/documents/" + result.id()))
                .body(result);
    }

    @GetMapping
    public List<DocumentView> list(@PathVariable UUID proposalId) {
        return service.list(proposalId);
    }
}
