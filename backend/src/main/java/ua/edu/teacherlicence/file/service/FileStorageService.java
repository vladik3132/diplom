package ua.edu.teacherlicence.file.service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Abstraction for file storage (Google Drive or local filesystem).
 */
public interface FileStorageService {

    /**
     * Upload a file and return a storage reference (Drive file ID or local path).
     *
     * @param content    file content stream
     * @param fileName   original file name
     * @param mimeType   MIME type (e.g. "application/pdf")
     * @param folderPath structured folder path (e.g. "Кафедра_ІТ/Іванов_Петро/Публікації")
     * @return storage reference (driveFileId or localPath)
     */
    String upload(InputStream content, String fileName, String mimeType, String folderPath) throws IOException;

    /**
     * Download file content by its storage reference.
     */
    byte[] download(String fileReference) throws IOException;

    /**
     * Delete a file by its storage reference.
     */
    void delete(String fileReference) throws IOException;
}
