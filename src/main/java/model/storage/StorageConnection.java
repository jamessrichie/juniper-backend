package model.storage;

import java.io.*;
import java.util.*;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.*;
import com.azure.storage.common.*;

import org.springframework.util.ResourceUtils;


public class StorageConnection {

    // Storage connection
    private final BlobServiceClient conn;

    /**
     * Creates a connection to the storage server specified in storage.credentials
     */
    public StorageConnection() throws IOException {
        conn = openConnection();
    }

    /**
     * Returns a connection to the storage server specified in storage.credentials
     */
    private static BlobServiceClient openConnection() throws IOException {
        Properties configProps = new Properties();
        configProps.load(new FileInputStream(ResourceUtils.getFile("classpath:credentials/storage.credentials")));

        String accountName = configProps.getProperty("STS_ACCOUNT_NAME");
        String key = configProps.getProperty("STS_KEY");

        StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, key);
        return new BlobServiceClientBuilder()
            .endpoint(String.format("https://%s.blob.core.windows.net/", accountName))
            .credential(credential)
            .buildClient();
    }

    /**
     * Creates a container with the specified name and returns its client.
     * If container already exists, returns the client for the existing container
     *
     * @param containerName container name
     * @return container client
     */
    public BlobContainerClient storage_createContainer(String containerName) {
        BlobContainerClient containerClient = conn.getBlobContainerClient(containerName);
        containerClient.createIfNotExists();
        return containerClient;
    }

    /**
     * Deletes the container with the specified name
     *
     * @param containerName container name
     */
    public void storage_deleteContainer(String containerName) {
        BlobContainerClient containerClient = conn.getBlobContainerClient(containerName);
        containerClient.delete();
    }

    /**
     * Gets the container with the specified name
     *
     * @param containerName container name
     * @return container client if container exists. otherwise, return null
     */
    public BlobContainerClient storage_getContainer(String containerName) {
        BlobContainerClient containerClient = conn.getBlobContainerClient(containerName);
        return (containerClient.exists()) ? containerClient : null;
    }

    /**
     * Uploads data to a blob with the specified name in the supplied container.
     * If no blob exists, create a blob. If a blob exists, overwrite the data.
     *
     * @param containerClient container client. must correspond to an existing container
     * @param blobName blob name
     * @param blobData data to be uploaded
     * @return blob client
     */
    public BlobClient storage_uploadBlob(BlobContainerClient containerClient, String blobName, String blobData) {
        if (containerClient == null || !containerClient.exists()) {
            throw new RuntimeException("Must supply an existing container client");
        }
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.upload(BinaryData.fromString(blobData), true);
        return blobClient;
    }

    /**
     * Deletes the blob in the supplied container
     *
     * @param containerClient container client. must correspond to an existing container
     * @param blobName blob name
     */
    public void storage_deleteBlob(BlobContainerClient containerClient, String blobName) {
        if (containerClient == null || !containerClient.exists()) {
            throw new RuntimeException("Must supply an existing container client");
        }
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        blobClient.delete();
    }

    /**
     * Gets the blob with the specified name in the supplied container
     *
     * @param containerClient container client. must correspond to an existing container
     * @param blobName blob name
     * @return blob client if blob exists. otherwise, return null
     */
    public BlobClient storage_getBlob(BlobContainerClient containerClient, String blobName) {
        if (containerClient == null || !containerClient.exists()) {
            throw new RuntimeException("Must supply an existing container client");
        }
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        return (blobClient.exists()) ? blobClient : null;
    }

}
