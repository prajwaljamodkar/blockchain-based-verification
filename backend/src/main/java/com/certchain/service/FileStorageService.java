package com.certchain.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Manages off-chain storage of certificate PDF files.
 *
 * <p>Files are stored on the local file system under the directory
 * specified by {@code storage.upload-dir} in application.yml.
 * Each file is named by its SHA-256 hash to guarantee uniqueness
 * and enable content-addressable retrieval.</p>
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path storageDir;

    public FileStorageService(@Value("${storage.upload-dir}") String uploadDir) {
        this.storageDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(storageDir);
            log.info("Certificate storage directory: {}", storageDir);
        } catch (IOException e) {
            throw new RuntimeException(
                "Could not create storage directory: " + storageDir, e);
        }
    }

    /**
     * Store a certificate PDF. The file is named {@code <hash>.pdf}.
     *
     * @param file the uploaded PDF
     * @param hash hex-encoded SHA-256 hash of the file
     * @return the relative path (e.g., "abc123...def.pdf")
     * @throws IOException if writing fails
     */
    public String store(MultipartFile file, String hash) throws IOException {
        String filename = hash + ".pdf";
        Path targetPath = storageDir.resolve(filename).normalize();

        // Security: prevent path traversal
        if (!targetPath.startsWith(storageDir)) {
            throw new SecurityException("Cannot store file outside of upload directory");
        }

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Stored certificate: {}", filename);
        return filename;
    }

    /**
     * Load a stored certificate as a Spring {@link Resource}.
     *
     * @param hash hex-encoded SHA-256 hash
     * @return the file as a downloadable resource
     * @throws IOException if the file doesn't exist or can't be read
     */
    public Resource loadAsResource(String hash) throws IOException {
        String filename = hash + ".pdf";
        Path filePath = storageDir.resolve(filename).normalize();

        if (!filePath.startsWith(storageDir)) {
            throw new SecurityException("Cannot access file outside of upload directory");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new IOException("File not found or not readable: " + filename);
        } catch (MalformedURLException e) {
            throw new IOException("Malformed file path: " + filename, e);
        }
    }

    /**
     * Check if a file with the given hash exists on disk.
     */
    public boolean exists(String hash) {
        Path filePath = storageDir.resolve(hash + ".pdf").normalize();
        return Files.exists(filePath);
    }
}
