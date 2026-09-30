package github.mralmostcool.chunk_norris.ingestion.dto;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;

public record DocumentUploadResponse(
        @NotNull(message = "File must not be null") MultipartFile file) {

}
