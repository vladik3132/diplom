package ua.edu.teacherlicence.file.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Google Drive storage via Service Account.
 * Creates folder hierarchy: rootFolder / departmentName / teacherName / documentType /
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.google-drive.enabled", havingValue = "true")
public class GoogleDriveService implements FileStorageService {

    @Value("${app.google-drive.credentials-path}")
    private String credentialsPath;

    @Value("${app.google-drive.root-folder-id}")
    private String rootFolderId;

    private Drive driveService;

    /** Cache: folderPath -> folderId to avoid repeated lookups */
    private final Map<String, String> folderIdCache = new ConcurrentHashMap<>();

    @PostConstruct
    void init() throws Exception {
        GoogleCredentials credentials = GoogleCredentials
                .fromStream(Files.newInputStream(Paths.get(credentialsPath)))
                .createScoped(Collections.singletonList(DriveScopes.DRIVE));

        driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("TeacherLicence")
                .build();

        log.info("Google Drive service initialized. Root folder: {}", rootFolderId);
    }

    @Override
    public String upload(InputStream content, String fileName, String mimeType, String folderPath) throws IOException {
        String parentFolderId = getOrCreateFolderPath(folderPath);

        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(List.of(parentFolderId));

        com.google.api.client.http.InputStreamContent mediaContent =
                new com.google.api.client.http.InputStreamContent(mimeType, content);

        File uploaded = driveService.files().create(fileMetadata, mediaContent)
                .setSupportsAllDrives(true)
                .setFields("id, name")
                .execute();

        log.info("File uploaded to Google Drive: {} (id: {})", uploaded.getName(), uploaded.getId());
        return uploaded.getId();
    }

    @Override
    public byte[] download(String driveFileId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        driveService.files().get(driveFileId)
                .setSupportsAllDrives(true)
                .executeMediaAndDownloadTo(out);
        return out.toByteArray();
    }

    @Override
    public void delete(String driveFileId) throws IOException {
        try {
            // On Shared Drives, files().delete() requires fileOrganizer role.
            // Use trash (update trashed=true) which works with regular editor access.
            File trashedMeta = new File();
            trashedMeta.setTrashed(true);
            driveService.files().update(driveFileId, trashedMeta)
                    .setSupportsAllDrives(true)
                    .execute();
            log.info("File trashed on Google Drive: {}", driveFileId);
        } catch (Exception e) {
            log.warn("Failed to trash file on Drive: {} — {}", driveFileId, e.getMessage());
        }
    }

    /**
     * Navigate/create folder hierarchy from root.
     * E.g. "Кафедра_ІТ/Іванов_Петро/Публікації" → creates 3 nested folders.
     */
    private String getOrCreateFolderPath(String folderPath) throws IOException {
        if (folderIdCache.containsKey(folderPath)) {
            return folderIdCache.get(folderPath);
        }

        String[] parts = folderPath.split("/");
        String currentParentId = rootFolderId;

        StringBuilder pathBuilder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!pathBuilder.isEmpty()) pathBuilder.append("/");
            pathBuilder.append(part);
            String currentPath = pathBuilder.toString();

            if (folderIdCache.containsKey(currentPath)) {
                currentParentId = folderIdCache.get(currentPath);
            } else {
                currentParentId = getOrCreateFolder(currentParentId, part);
                folderIdCache.put(currentPath, currentParentId);
            }
        }

        return currentParentId;
    }

    private String getOrCreateFolder(String parentId, String folderName) throws IOException {
        // Search for existing folder
        String query = String.format(
                "mimeType='application/vnd.google-apps.folder' and name='%s' and '%s' in parents and trashed=false",
                folderName.replace("'", "\\'"), parentId);

        FileList result = driveService.files().list()
                .setQ(query)
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setFields("files(id, name)")
                .execute();

        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            return result.getFiles().get(0).getId();
        }

        // Create new folder
        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        folderMetadata.setParents(List.of(parentId));

        File folder = driveService.files().create(folderMetadata)
                .setSupportsAllDrives(true)
                .setFields("id, name")
                .execute();

        log.info("Created folder on Drive: {} (id: {})", folder.getName(), folder.getId());
        return folder.getId();
    }
}
