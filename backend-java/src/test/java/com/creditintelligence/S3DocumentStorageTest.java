package com.creditintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.creditintelligence.document.infrastructure.S3DocumentStorage;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3DocumentStorageTest {
    private static final String BUCKET = "synthetic-documents";
    private final S3ClientBuilder builder = mock(S3ClientBuilder.class, RETURNS_SELF);
    private final S3Client client = mock(S3Client.class);

    @Test
    void configuresLocalObjectStorageAndCreatesAMissingBucket() {
        var endpoint = URI.create("http://minio.test:9000");
        var lookup = HeadBucketRequest.builder().bucket(BUCKET).build();
        when(client.headBucket(lookup)).thenThrow(S3Exception.builder().statusCode(404).build());

        initialize(endpoint.toString(), "test-access", "test-secret");

        verify(builder).region(Region.US_EAST_1);
        verify(builder).endpointOverride(endpoint);
        verify(builder).forcePathStyle(true);
        var provider = ArgumentCaptor.forClass(AwsCredentialsProvider.class);
        verify(builder).credentialsProvider(provider.capture());
        var credentials = provider.getValue().resolveCredentials();
        assertThat(credentials.accessKeyId()).isEqualTo("test-access");
        assertThat(credentials.secretAccessKey()).isEqualTo("test-secret");
        var bucketOperations = org.mockito.Mockito.inOrder(client);
        bucketOperations.verify(client).headBucket(lookup);
        bucketOperations.verify(client).createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @Test
    void usesSdkDefaultsAndLeavesAnExistingBucketInPlace() {
        initialize("", "", "");

        verify(builder, never()).endpointOverride(any(URI.class));
        verify(builder, never()).credentialsProvider(any(AwsCredentialsProvider.class));
        verify(client).headBucket(HeadBucketRequest.builder().bucket(BUCKET).build());
        verify(client, never()).createBucket(any(CreateBucketRequest.class));
    }

    private void initialize(String endpoint, String accessKey, String secretKey) {
        when(builder.build()).thenReturn(client);
        // Replace the SDK factory before construction so even a mutated endpoint cannot use the network.
        try (var sdk = mockStatic(S3Client.class)) {
            sdk.when(S3Client::builder).thenReturn(builder);
            new S3DocumentStorage(BUCKET, endpoint, Region.US_EAST_1.id(), accessKey, secretKey);
        }
    }
}
