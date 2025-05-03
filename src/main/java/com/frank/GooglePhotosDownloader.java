package com.frank;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.auth.oauth2.UserCredentials;
import com.google.gson.JsonObject;
import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.types.proto.MediaItem;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;


public class GooglePhotosDownloader {

    public static final String EXISTING_FILE_LIST_NAME = "exisingPhotosList.txt";
    public static final List<String> EXISTING_FILE_IDS = getIDsFromFile(EXISTING_FILE_LIST_NAME);

    private static List<String> getIDsFromFile(String fileName) {
        System.out.println("Getting IDs from " + fileName);

        java.io.File file = new java.io.File(fileName);

        if (!file.exists()) {
            System.out.println("File does not exist: " + file.getAbsolutePath() + ", a new file will be created later.");

            return Collections.emptyList();
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();

            if (line != null && !line.trim().isEmpty()) {
                return Arrays.asList(line.split(","));
            }
        }
        catch (IOException e) {
            System.out.println("Exception while reading file: " + e.getMessage());
        }

        return Collections.emptyList();
    }

    public static void createPhotosCredentialFile(String credentialsPath, String refreshToken, String outputPath) throws IOException {
        // Parse credentials.json
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
                new FileReader(credentialsPath)
        );

        String clientId = clientSecrets.getDetails().getClientId();
        String clientSecret = clientSecrets.getDetails().getClientSecret();

        // Construct the new credentials format
        JsonObject authUser = new JsonObject();
        authUser.addProperty("client_id", clientId);
        authUser.addProperty("client_secret", clientSecret);
        authUser.addProperty("refresh_token", refreshToken);
        authUser.addProperty("type", "authorized_user");

        // Write to file
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(authUser.toString());
            System.out.println("Saved photos credentials to " + outputPath);
        }
    }

    private static String getRefreshToken() throws GeneralSecurityException, IOException {
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, new FileReader("./credentials.json"));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                jsonFactory,
                clientSecrets,
                Collections.singletonList("https://www.googleapis.com/auth/photoslibrary.readonly")
        )
                .setDataStoreFactory(new FileDataStoreFactory(new File("refreshTokens")))
                .setAccessType("offline") // This is crucial for refresh token
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");

        return credential.getRefreshToken();
    }

    private static void printProgress(long bytesDownloaded, long totalBytes) {
        double progress = (double) bytesDownloaded / totalBytes;

        int progressPercentage = (int) Math.min(progress * 100, 100);
        int filledLength = Math.min(progressPercentage, 100);
        int emptyLength = 100 - filledLength;

        String progressBar = "=".repeat(filledLength) + " ".repeat(emptyLength);

        System.out.print("\r[" + progressBar + "] " + progressPercentage + "%");
    }

    private static String formatFileSize(long sizeInBytes) {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        }
        else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.2f KB", sizeInBytes / 1024.0);
        }
        else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", sizeInBytes / (1024.0 * 1024));
        }
        else {
            return String.format("%.2f GB", sizeInBytes / (1024.0 * 1024 * 1024));
        }
    }

    public static void updateTrackerFile(String fileId) {
        try (
                FileWriter writer = new FileWriter(EXISTING_FILE_LIST_NAME, true)
        ) {
            writer.write(fileId + ",");
        }
        catch (IOException e) {
            System.err.println("Error writing to tracker file: " + e.getMessage());

            throw new RuntimeException("Error writing to tracker file: ");
        }
    }

    public static boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }

        try {
            Double.parseDouble(str);

            return true;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    private static void downloadMediaItems(PhotosLibraryClient photosLibraryClient, String downloadPath) throws IOException {
        if (downloadPath == null || downloadPath.isEmpty()) {
            return;
        }

        File folder = new File(downloadPath);

        if (folder.exists() && !folder.isDirectory()) {
            return;
        }

        Files.createDirectories(Paths.get(downloadPath));

        String downloadFileLimit = System.getenv("DOWNLOAD_FILE_LIMIT");

        int limit = isNumeric(downloadFileLimit) ? Integer.parseInt(downloadFileLimit) : -1;

        int count = 0;

        for (MediaItem item : photosLibraryClient.listMediaItems().iterateAll()) {
            if (limit != -1 && count >= limit) {
                System.out.println("Download limit reached. Limit: " + limit + " files. Run again to continue or, edit / remove the download limit from the configuration.");

                break;
            }

            String fileId = item.getId();
            String filename = item.getFilename();

            if (!EXISTING_FILE_IDS.isEmpty() && EXISTING_FILE_IDS.contains(fileId)) {
                System.out.println("File already exists: " + filename + " with id: " + fileId + ". Skipping");

                continue;
            }

            String baseUrl = item.getBaseUrl();
            String mimeType = item.getMimeType();

            String suffix = mimeType.startsWith("video") ? "=dv" : "=d";
            String downloadUrl = baseUrl + suffix;
            String outputFile = downloadPath + filename;


            URL url = new URL(downloadUrl);
            URLConnection connection = url.openConnection();

            long totalBytes = connection.getContentLengthLong();

            System.out.println("Downloading to " + outputFile + " (" + formatFileSize(totalBytes) + ")");
            System.out.println("From: " + downloadUrl);

            try (
                InputStream in = new URL(downloadUrl).openStream();
                OutputStream out = new FileOutputStream(outputFile)
            ) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long downloaded = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);

                    downloaded += bytesRead;

                    if (totalBytes > 0) {
                        printProgress(downloaded, totalBytes);
                    }
                }

                if (totalBytes > 0) {
                    System.out.print(" (done)\n");
                }
                else {
                    System.out.println("Downloaded (unknown size): " + outputFile);
                }

                updateTrackerFile(fileId);
                count++;

                System.out.println();
            }
        }
    }

    public static void main(String[] args) throws IOException, GeneralSecurityException {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Do you want to proceed to download from google photos? (y/n)? ");
        String response = scanner.nextLine();

        if (!response.equalsIgnoreCase("Y") && !response.equalsIgnoreCase("yes")) {
            System.out.println("Quitting...");

            return;
        }

        String inputCredentials = "./credentials.json";
        File inputCredentialsFile = new File(inputCredentials);

        if (!inputCredentialsFile.exists()) {
            return;
        }

        String refreshToken = getRefreshToken();
        String outputFile = "./photosCredentials.json";

        createPhotosCredentialFile(inputCredentials, refreshToken, outputFile);

        File outputCredsFile = new File(outputFile);

        if (!outputCredsFile.exists()) {
            return;
        }

        UserCredentials userCredentials = UserCredentials.fromStream(new FileInputStream("photosCredentials.json"));

        PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder().setCredentialsProvider(() -> userCredentials).build();

        String downloadPath = System.getenv("DESTINATION_PATH") + "/googlePhotos/";

        try (PhotosLibraryClient photosLibraryClient = PhotosLibraryClient.initialize(settings)) {
            downloadMediaItems(photosLibraryClient, downloadPath);
        }
    }
}


