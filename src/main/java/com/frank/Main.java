package com.frank;

import com.google.photos.library.v1.PhotosLibraryClient;
import com.google.photos.library.v1.PhotosLibrarySettings;
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient;
import com.google.photos.types.proto.MediaItem;
import com.google.auth.oauth2.UserCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.photos.library.v1.proto.SearchMediaItemsRequest;
import com.google.photos.library.v1.proto.SearchMediaItemsResponse;

import java.io.*;
import java.net.URL;
import java.util.List;

public class Main {

    private static final String CREDENTIALS_FILE_PATH = "./credentials.json";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    public static void main(String[] args) throws Exception {
        // Load user credentials from the credentials.json file
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(CREDENTIALS_FILE_PATH))
                .createScoped(List.of("https://www.googleapis.com/auth/photoslibrary.readonly"));

        PhotosLibrarySettings settings = PhotosLibrarySettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();

        try (PhotosLibraryClient photosLibraryClient = PhotosLibraryClient.initialize(settings)) {
            // Create a request to search media items
            SearchMediaItemsRequest request = SearchMediaItemsRequest.newBuilder()
                    .setPageSize(10) // Adjust as needed
                    .build();

            // Execute the request
            SearchMediaItemsResponse response = photosLibraryClient.searchMediaItemsCallable().call(request);

            List<MediaItem> mediaItems = response.getMediaItemsList();

            if (mediaItems != null) {
                for (MediaItem item : mediaItems) {
                    String baseUrl = item.getBaseUrl();
                    String mimeType = item.getMimeType();
                    String filename = item.getFilename();
                    String downloadUrl = mimeType.startsWith("video/") ? baseUrl + "=dv" : baseUrl + "=d";

                    downloadMedia(downloadUrl, filename);
                }
            } else {
                System.out.println("No media items found.");
            }
        }
    }

    private static void downloadMedia(String url, String filename) throws IOException {
        try (InputStream in = new URL(url).openStream();
             OutputStream out = new FileOutputStream(filename)) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            System.out.println("Downloaded: " + filename);
        }
    }
}
