package com.github.exidcuter.dockerregistryexplorer.ui;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Request;
import okhttp3.Response;
import org.tdfl.docker.DockerRegistry;
import org.tdfl.docker.error.AuthException;
import org.tdfl.docker.error.NotFoundException;
import org.tdfl.docker.error.RegistryErrorException;
import org.tdfl.docker.model.Catalog;
import org.tdfl.docker.model.RegistryErrors;
import org.tdfl.docker.model.Tags;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

final class DockerRegistryOperations implements RegistryOperations {
    private static final String MANIFEST_ACCEPT_HEADER = String.join(
            ", ",
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.oci.image.index.v1+json",
            "application/vnd.docker.distribution.manifest.v2+json",
            "application/vnd.docker.distribution.manifest.list.v2+json",
            "application/vnd.docker.distribution.manifest.v1+json"
    );

    @Override
    public List<String> loadRepositories(DockerRegistry registry) {
        Catalog catalog = registry.getCatalog(0, 1000);
        return catalog.getRepositories() == null ? Collections.emptyList() : catalog.getRepositories();
    }

    @Override
    public List<String> loadTags(DockerRegistry registry, String repositoryName) {
        Tags tags = registry.getTags(repositoryName, 0, 1000);
        return tags.getTags() == null ? Collections.emptyList() : tags.getTags();
    }

    @Override
    public JsonNode loadManifest(DockerRegistry registry, String repositoryName, String tag) throws Exception {
        Request request = new Request.Builder()
                .url(registry.getLoginCredentials().getRegistryURL() + "/v2/" + repositoryName + "/manifests/" + tag)
                .addHeader("Accept", MANIFEST_ACCEPT_HEADER)
                .get()
                .build();

        try (Response response = registry.getClient().newCall(request).execute()) {
            if (response.code() == 200) {
                return registry.getObjectMapper().readTree(body(response));
            }
            if (response.code() == 401) {
                throw new AuthException(registry.getLoginCredentials());
            }
            if (response.code() == 404) {
                throw new NotFoundException("Manifest not found: " + repositoryName + ":" + tag);
            }
            throw registryError(registry, response);
        }
    }

    @Override
    public void deleteTag(DockerRegistry registry, String repositoryName, String tag) {
        registry.deleteImage(repositoryName, tag);
    }

    private static String body(Response response) throws IOException {
        return response.body() == null ? "" : response.body().string();
    }

    private static RegistryErrorException registryError(DockerRegistry registry, Response response) throws IOException {
        RegistryErrors errors = registry.getObjectMapper().readValue(body(response), RegistryErrors.class);
        if (errors.getErrors() == null || errors.getErrors().isEmpty()) {
            return new RegistryErrorException("Manifest request failed with HTTP " + response.code());
        }
        org.tdfl.docker.model.Error error = errors.getErrors().get(0);
        return new RegistryErrorException("Error: [" + error.getCode() + "] " + error.getMessage());
    }
}
