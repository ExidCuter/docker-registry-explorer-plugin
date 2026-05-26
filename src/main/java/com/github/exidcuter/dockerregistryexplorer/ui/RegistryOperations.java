package com.github.exidcuter.dockerregistryexplorer.ui;

import com.fasterxml.jackson.databind.JsonNode;
import org.tdfl.docker.DockerRegistry;

import java.util.List;

interface RegistryOperations {
    List<String> loadRepositories(DockerRegistry registry) throws Exception;

    List<String> loadTags(DockerRegistry registry, String repositoryName) throws Exception;

    JsonNode loadManifest(DockerRegistry registry, String repositoryName, String tag) throws Exception;

    void deleteTag(DockerRegistry registry, String repositoryName, String tag) throws Exception;
}
