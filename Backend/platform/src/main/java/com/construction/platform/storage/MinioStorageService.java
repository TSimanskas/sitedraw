package com.construction.platform.storage;

import com.construction.platform.config.AppProperties;
import com.construction.platform.exception.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public MinioStorageService(S3Client s3Client, AppProperties appProperties) {
        this.s3Client = s3Client;
        this.bucket = appProperties.storage().minio().bucket();
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException exception) {
            // Bucket is ready.
        }
    }

    @Override
    public void upload(String key, InputStream inputStream, long contentLength, String contentType) {
        validateKey(key);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
        } catch (S3Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }
    }

    @Override
    public void copy(String sourceKey, String destinationKey) {
        validateKey(sourceKey);
        validateKey(destinationKey);

        if (!exists(sourceKey)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Source file not found");
        }

        try {
            CopyObjectRequest request = CopyObjectRequest.builder()
                    .sourceBucket(bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(bucket)
                    .destinationKey(destinationKey)
                    .build();

            s3Client.copyObject(request);
        } catch (S3Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to copy file");
        }
    }

    @Override
    public InputStream download(String key) {
        validateKey(key);

        if (!exists(key)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }

        try {
            return s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file");
        }
    }

    @Override
    public boolean exists(String key) {
        validateKey(key);

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to check file");
        }
    }

    @Override
    public void delete(String key) {
        validateKey(key);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return;
            }
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete file");
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.contains("..")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid storage key");
        }
    }
}
