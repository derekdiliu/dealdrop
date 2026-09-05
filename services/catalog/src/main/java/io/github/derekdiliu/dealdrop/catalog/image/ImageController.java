package io.github.derekdiliu.dealdrop.catalog.image;

import io.github.derekdiliu.dealdrop.catalog.image.ImageStorage.PresignedUpload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/admin/images")
class ImageController {

    private final ImageStorage storage;

    ImageController(ImageStorage storage) {
        this.storage = storage;
    }

    /**
     * Returns a presigned URL to upload one image. The returned key is then set as a deal's imageKey.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PresignedUpload createUpload(@Valid @RequestBody UploadRequest request) {
        return storage.presignUpload(request.contentType(), request.contentLength());
    }

    record UploadRequest(@NotBlank String contentType, @NotNull Long contentLength) {
    }
}
