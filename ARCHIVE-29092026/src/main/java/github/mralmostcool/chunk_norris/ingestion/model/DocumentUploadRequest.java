package github.mralmostcool.chunk_norris.ingestion.model;

import org.springframework.web.multipart.MultipartFile;

public record DocumentUploadRequest(
        MultipartFile file) {

}
