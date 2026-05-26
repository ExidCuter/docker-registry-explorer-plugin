package com.github.exidcuter.dockerregistryexplorer.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ManifestDetailsFormatterTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void formatsSchemaTwoManifestFieldsWithoutLegacyModelBinding() throws Exception {
        String manifestJson = """
                {
                  "schemaVersion": 2,
                  "mediaType": "application/vnd.docker.distribution.manifest.v2+json",
                  "config": {
                    "mediaType": "application/vnd.docker.container.image.v1+json",
                    "digest": "sha256:config",
                    "size": 3016
                  },
                  "layers": [
                    {
                      "mediaType": "application/vnd.docker.image.rootfs.diff.tar.gzip",
                      "digest": "sha256:layer",
                      "size": 62679166
                    }
                  ]
                }
                """;

        String details = ManifestDetailsFormatter.format(
                objectMapper.readTree(manifestJson),
                "https://registry.example.com",
                "example/image",
                "latest"
        );

        assertTrue(details.contains("Schema version: 2"));
        assertTrue(details.contains("Media type: application/vnd.docker.distribution.manifest.v2+json"));
        assertTrue(details.contains("Config: sha256:config"));
        assertTrue(details.contains("Layers: 1"));
        assertTrue(details.contains("sha256:layer"));
    }
}
