package com.distributed.p2pFileTransfer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileDownloadCallable implements Callable<FileDownloadResult> {
    private final Storage fileStorage;
    private final Logger logger;
    private Node source;
    private String fileName;
    private String strippedFileName;
    private String destination;

    FileDownloadCallable(Node source, String filename, String destination, Storage fileStorage, String loggerName) {
        this.source = source;
        this.fileName = filename;
        this.strippedFileName = fileName.replaceAll(" ", "_");
        this.destination = destination;
        this.fileStorage = fileStorage;
        this.logger = Logger.getLogger(loggerName);
    }

    /**
     * Create a callable for making a GET request to download the file
     *
     * @return FileDownloadResult Download status
     * @throws IOException
     */
    @Override
    public FileDownloadResult call() throws IOException {
        FileDownloadResult result;
        synchronized (fileStorage){
            Boolean fileExists = fileStorage.checkFileExists(fileName,destination);
            // Check if file is already available locally
            if (!fileExists){
                URL url = new URL("http://" + source.getIpAddress().getHostAddress() + ":" + source.getPort() + "/file/" + strippedFileName);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                String fileHash = httpURLConnection.getHeaderField("Hash");
                int fileSize = httpURLConnection.getContentLength();
                fileStorage.makeCacheSpace(fileSize);
                if (fileHash != null) {
                    result = downloadFile(httpURLConnection, fileHash, fileSize, destination);
                }
                else {
                    result = new FileDownloadResult("Hash not available", 1);
                }
            }
            else {
                result = new FileDownloadResult("File already exists locally", 1);
            }
        }
        return result;
    }

    /**
     * Download the file and verify the hash
     *
     * @param httpURLConnection
     * @param fileHash          Hash of the file sent from the server
     * @param fileSize          Size of the file
     * @return FileDownloadResult Download status
     * @throws IOException
     */
    public FileDownloadResult downloadFile(HttpURLConnection httpURLConnection, String fileHash, int fileSize, String destination) throws IOException {
        InputStream inputStream = httpURLConnection.getInputStream();
        // Byte reader
        // Get download destination path
        String saveFilePath = destination + File.separator + fileName;

        FileOutputStream outputStream = new FileOutputStream(saveFilePath);
        int bytesRead;
        byte[] buffer = new byte[fileSize];
        if (httpURLConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            if (inputStream.available() != 0) {
                // Check again to see whether the file has been downloaded from another node
                Boolean fileExists = fileStorage.checkFileExists(fileName,destination);
                if (!fileExists){
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.close();
                    fileStorage.updateDirectoryListing(destination, fileName);
                    this.logger.log(Level.INFO, String.format("Downloaded %s", saveFilePath));
                    String hexHash = this.fileStorage.getFileHash(new File(saveFilePath));
                    if (hexHash.equals(fileHash)) {
                        return new FileDownloadResult("File download successful", 0);
                    } else {
                        return new FileDownloadResult("Hash do not match", 1);
                    }
                }
                else{
                    return new FileDownloadResult("File has been already downloaded", 1);
                }
            } else {
                return new FileDownloadResult("Input stream empty", 0);
            }
        } else {
            return new FileDownloadResult(httpURLConnection.getResponseMessage(), 1);
        }
    }

}
