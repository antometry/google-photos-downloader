# Google Photos Downloader

This Java application authenticates with the Google Photos API, downloads the photos and videos to a specified directory on your machine with download progress tracking.

---

## 📌 Features

- Authenticates with Google using OAuth2
- Supports refresh tokens to avoid re-authentication
- Lists media info (download path, name, MIME type, URL)
- Downloads media (photos/videos) with progress bar
- Automatically handles directory creation
- Automatically identifies and skips files that have already downloaded in the previous run with a tracking file.
---

## 🧰 Prerequisites

- Java 17 or higher
- Maven
- A Google Cloud project with the **Photos Library API** enabled
- Internet connection for API access

---

## 🔐 One-Time Setup: .bat file config and Generating Credentials

1. Download the `.zip` file from the release tab and extract it.
2. Open the .bat file in an editor and edit the `DESTINATION_PATH` variable. If left empty, the folder where the .bat runs is considered. (**Note:** Do not provide `/` at the end)
3. If you want to limit the number of downloads per run, provide a number > 0. if 0 or less is provided, the limit is not considered.
4. Save the .bat file.
5. Go to [Google Cloud Console](https://console.cloud.google.com/)
6. Create a new project (or use an existing one)
7. Enable **Photos Library API**
8. Go to **OAuth consent screen** and configure it.
9. Go to **Credentials** tab:
    - Click **Create Credentials** → **OAuth Client ID**
    - Choose **Desktop app**
    - Download the `credentials.json` file (**Note:** Rename the file if necessary)
    - If you chose `external user`, you may want to add your email address to the list of `test users` in the console.

10. Save `credentials.json` in the folder where you extracted before.

---

## ▶️ Process
- Run the .bat file
- login if a browser window is popped.
- You should see the download start.
- Verify if the files are downloaded in your `DESTINATION_PATH`