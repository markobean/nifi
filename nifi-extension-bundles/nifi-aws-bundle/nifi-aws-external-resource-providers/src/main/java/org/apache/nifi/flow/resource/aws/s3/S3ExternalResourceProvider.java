/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.flow.resource.aws.s3;

import org.apache.nifi.flow.resource.ExternalResourceDescriptor;
import org.apache.nifi.flow.resource.ExternalResourceProvider;
import org.apache.nifi.flow.resource.ExternalResourceProviderInitializationContext;
import org.apache.nifi.flow.resource.ImmutableExternalResourceDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class S3ExternalResourceProvider implements ExternalResourceProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3ExternalResourceProvider.class);

//    private static final String RESOURCES_PARAMETER = "resources";
    private static final String CREDENTIALS_FILE = "credentials.file";
    private static final String ACCESS_KEY_ID = "access.key.id";
    private static final String SECRET_ACCESS_KEY = "secret.access.key";
    private static final String BUCKET_NAME = "bucket.name";
    private static final String PRIMARY_REGION = "region";
    private static final String ALTERNATE_REGION = "region.alternate";
    private static final String PATH_REGEX = "path.regex";
    private static final String PRIMARY = "primary";
    private static final String ALTERNATE = "alternate";

    private record NarListingEntry(String fileName, Instant lastModified) {}


    private volatile String bucketName = null;
    private volatile String primaryRegion = null;
    private volatile String alternateRegion = null;
    private volatile String pathRegex = null;
//    private volatile String credentialsFile = null;
    private volatile String accessKeyId = null;
    private volatile String secretAccessKey = null;
    // ssl? hopefully not, but can this be used if running without a keystore? consider that invalid configuration?
    // List of primary and alternate clients (alternate is optional)
    private volatile S3Client primaryClient = null;
    private volatile S3Client alternateClient = null;
    private volatile List<String> resources = null;

    private volatile Pattern pathPattern = null;
    private volatile boolean initialized = false;


    @Override
    public void initialize(final ExternalResourceProviderInitializationContext context) {
        bucketName = Objects.requireNonNull(context.getProperties().get(BUCKET_NAME));
        primaryRegion = Objects.requireNonNull(context.getProperties().get(PRIMARY_REGION));
        alternateRegion = context.getProperties().get(ALTERNATE_REGION);
        if (bucketName.isEmpty() || primaryRegion.isEmpty()) {
            throw new IllegalArgumentException("Bucket and region name must be provided in nifi.properties");
        }
        String pathRegex = context.getProperties().get(PATH_REGEX);

        // TODO: parse credentials file for equivalent info as accessKeyId and secretAccessKey provide directly
//        credentialsFile = context.getProperties().get(CREDENTIALS_FILE);
//        // TODO: allow access key id/secret access key properties; changes if conditions
//        if (credentialsFile == null || credentialsFile.isEmpty()) {
//            throw new IllegalArgumentException("Credentials file [or ...] must be provided in nifi.properties");
//        }
        accessKeyId = context.getProperties().get(ACCESS_KEY_ID);
        secretAccessKey = context.getProperties().get(SECRET_ACCESS_KEY);
        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        primaryClient = S3Client.builder()
                .region(Region.of(primaryRegion))
                .credentialsProvider(credentialsProvider)
                .build();
        if (alternateRegion != null && !alternateRegion.isEmpty()) {
            alternateClient = S3Client.builder()
                    .region(Region.of(alternateRegion))
                    .credentialsProvider(credentialsProvider)
                    .build();
        }

        // Optional: establish regex pattern for NAR file name
        if (pathRegex != null && !pathRegex.isEmpty()) {
            pathPattern = Pattern.compile(pathRegex);
        }

        this.initialized = true;

    }

    @Override
    public Collection<ExternalResourceDescriptor> listResources() throws IOException {
        if (!initialized) {
            throw new IllegalStateException("Provider is not initialized");
        }

        final S3Client client = getClient();
        if (client == null) {
            throw new IllegalStateException("Unable to access S3bucket " + bucketName + " in any configured region");
        }

        final List<NarListingEntry> narObjects = new ArrayList<>();
        String continuationToken = null;
        do {
            final ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1000);
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }
            final ListObjectsV2Response response = client.listObjectsV2(requestBuilder.build());
            for (final S3Object object : response.contents()) {
                final String key = object.key();
                if (key != null && pathPattern.matcher(key).matches()) {
                    narObjects.add(new NarListingEntry(key, object.lastModified()));
                }
            }
            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        // TEMP
        List<String> narFileNames = narObjects.stream().map(narObject -> narObject.fileName()).collect(Collectors.toList());

        final List<ExternalResourceDescriptor> result = narObjects.stream()
                .map(S3ExternalResourceProvider::convertNarObjectToResourceDescriptor)
                .collect(Collectors.toList());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("The following NARs were found: {}", result.stream()
                    .map(ExternalResourceDescriptor::getLocation)
                    .collect(Collectors.joining(", ")));
        }

        return result;
    }

    @Override
    public InputStream fetchExternalResource(final ExternalResourceDescriptor descriptor) throws IOException {
        final S3Client client = getClient();
        if (client == null) {
            throw new IllegalStateException("Could not fetch " + descriptor.getLocation() + ". Unable to access S3 bucket " + bucketName + " in any configured region");
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Fetching resource {} from S3 bucket {} in region {}", descriptor.getLocation(), bucketName, client.serviceClientConfiguration().region());
        }

        return client.getObject(b -> b.bucket(bucketName).key(descriptor.getLocation()));
    }

    private static List<S3Object> listMatchingObjects(
            final S3Client s3Client,
            final String bucketName,
            final String pathRegex
    ) {
        final Pattern pattern = Pattern.compile(pathRegex);

        final List<S3Object> matches = new ArrayList<>();
        String continuationToken = null;

        do {
            final ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(1000);

            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken); // TODO: is continuation token needed, or hard limit?
            }

            final ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

            for (final S3Object object : response.contents()) {
                final String key = object.key();
                if (key != null && pattern.matcher(key).matches()) {
                    matches.add(object);
                }
            }

            continuationToken = response.nextContinuationToken();
        } while (continuationToken != null);

        return matches;
    }

    // Return primary client if available; failover to alternate, if necessary
    private S3Client getClient() {
        try {
            probeBucket(primaryClient, bucketName);
            return primaryClient;
        } catch (final S3Exception e) {
            if (alternateClient != null) {
                try {
                    probeBucket(alternateClient, bucketName);
                    return alternateClient;
                } catch (final S3Exception e2) {
                    LOGGER.warn("Unable to access S3 bucket {} in any configured region", bucketName, e2);
                }
            }
        }
        return null;
    }

    private static void probeBucket(final S3Client client, final String bucketName) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (final S3Exception e) {
            LOGGER.warn("S3 bucket {} in region {} does not exist or is inaccessible", bucketName, client.serviceClientConfiguration().region(), e);
            throw e;
        } catch (final SdkClientException e) {
            LOGGER.warn("Error probing S3 bucket {} in region {}", bucketName, client.serviceClientConfiguration().region(), e);
        }
    }

    private static ExternalResourceDescriptor convertNarObjectToResourceDescriptor(final NarListingEntry narListingEntry) {
        return new ImmutableExternalResourceDescriptor(narListingEntry.fileName(), narListingEntry.lastModified().toEpochMilli());
    }
}
