package com.demo.resortslite.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Service for Google Cloud Storage operations.
 * Replaces local file system access with cloud storage for containerized environments.
 */
@Service
public class GcsStorageService {

    @Autowired(required = false)
    private Storage storage;

    @Value("${gcs.bucket.name}")
    private String bucketName;

    /**
     * Upload content to GCS bucket
     * @param fileName Name of the file in GCS
     * @param content Content to upload
     * @return GCS object path
     */
    public String uploadFile(String fileName, String content) {
        if (storage == null) {
            // Fallback for local development without GCS credentials
            return "gs://" + bucketName + "/" + fileName + " (simulated - GCS not configured)";
        }
        
        BlobId blobId = BlobId.of(bucketName, fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType("application/octet-stream")
                .build();
        
        storage.create(blobInfo, content.getBytes(StandardCharsets.UTF_8));
        return "gs://" + bucketName + "/" + fileName;
    }

    /**
     * Download content from GCS bucket
     * @param fileName Name of the file in GCS
     * @return File content as string
     */
    public String downloadFile(String fileName) throws IOException {
        if (storage == null) {
            throw new IOException("GCS storage not configured");
        }
        
        BlobId blobId = BlobId.of(bucketName, fileName);
        Blob blob = storage.get(blobId);
        
        if (blob == null) {
            throw new IOException("File not found: " + fileName);
        }
        
        return new String(blob.getContent(), StandardCharsets.UTF_8);
    }

    /**
     * Check if file exists in GCS bucket
     * @param fileName Name of the file in GCS
     * @return true if file exists
     */
    public boolean fileExists(String fileName) {
        if (storage == null) {
            return false;
        }
        
        BlobId blobId = BlobId.of(bucketName, fileName);
        Blob blob = storage.get(blobId);
        return blob != null && blob.exists();
    }

    /**
     * Get GCS path for a file
     * @param fileName Name of the file
     * @return Full GCS path
     */
    public String getGcsPath(String fileName) {
        return "gs://" + bucketName + "/" + fileName;
    }
}
