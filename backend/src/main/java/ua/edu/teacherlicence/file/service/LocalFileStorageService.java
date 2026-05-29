package ua.edu.teacherlicence.file.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Dev fallback: stores files on local filesystem under ./uploads/.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.google-drive.enabled", havingValue = "false", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final Path UPLOADS_DIR = Paths.get("./uploads").toAbsolutePath().normalize();

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(UPLOADS_DIR);
        log.info("Local file storage initialized at: {}", UPLOADS_DIR);
    }

    @Override
    public String upload(InputStream content, String fileName, String mimeType, String folderPath) throws IOException {
        Path folder = UPLOADS_DIR.resolve(folderPath.replace("\\", "/"));
        Files.createDirectories(folder);

        String storedName = UUID.randomUUID().toString().substring(0, 8) + "_" + fileName;
        Path filePath = folder.resolve(storedName);
        Files.copy(content, filePath);

        log.info("File saved locally: {}", filePath);
        return UPLOADS_DIR.relativize(filePath).toString().replace("\\", "/");
    }

    @Override
    public byte[] download(String fileReference) throws IOException {
        Path filePath = UPLOADS_DIR.resolve(fileReference);
        if (!Files.exists(filePath)) {
            throw new IOException("File not found: " + fileReference);
        }
        return Files.readAllBytes(filePath);
    }

    @Override
    public void delete(String fileReference) throws IOException {
        Path filePath = UPLOADS_DIR.resolve(fileReference);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("File deleted: {}", filePath);
        }
    }
}
