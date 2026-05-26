package com.github.exidcuter.dockerregistryexplorer.ui;

import com.fasterxml.jackson.databind.JsonNode;

final class ManifestDetailsFormatter {
    private ManifestDetailsFormatter() {
    }

    static String format(JsonNode manifest, String registryUrl, String repositoryName, String tag) {
        StringBuilder details = new StringBuilder()
                .append("Registry: ").append(registryUrl).append("\n")
                .append("Image: ").append(text(manifest, "name", repositoryName)).append("\n")
                .append("Tag: ").append(text(manifest, "tag", tag)).append("\n")
                .append("Schema version: ").append(text(manifest, "schemaVersion", "Unknown")).append("\n")
                .append("Media type: ").append(text(manifest, "mediaType", "Unknown")).append("\n")
                .append("Architecture: ").append(text(manifest, "architecture", "Unknown")).append("\n");

        appendDescriptor(details, "Config", manifest.get("config"));

        JsonNode layers = manifest.get("layers");
        if (layers != null && layers.isArray()) {
            appendV2Layers(details, layers);
        } else {
            appendV1Layers(details, manifest.get("fsLayers"));
        }

        JsonNode manifests = manifest.get("manifests");
        if (manifests != null && manifests.isArray()) {
            details.append("Platform manifests: ").append(manifests.size()).append("\n");
            for (int index = 0; index < manifests.size(); index++) {
                appendDescriptor(details, "  " + (index + 1) + ".", manifests.get(index));
            }
        }

        details.append("History entries: ").append(arraySize(manifest.get("history"))).append("\n")
                .append("Signatures: ").append(arraySize(manifest.get("signatures"))).append("\n");
        return details.toString();
    }

    private static void appendV2Layers(StringBuilder details, JsonNode layers) {
        details.append("Layers: ").append(layers.size()).append("\n");
        for (int index = 0; index < layers.size(); index++) {
            JsonNode layer = layers.get(index);
            details.append("  ")
                    .append(index + 1)
                    .append(". ")
                    .append(text(layer, "digest", "Unknown"))
                    .append("  ")
                    .append(text(layer, "mediaType", "Unknown"))
                    .append("  ")
                    .append(size(layer))
                    .append("\n");
        }
    }

    private static void appendV1Layers(StringBuilder details, JsonNode layers) {
        int size = arraySize(layers);
        details.append("Layers: ").append(size).append("\n");
        if (layers == null || !layers.isArray()) {
            return;
        }
        for (int index = 0; index < layers.size(); index++) {
            details.append("  ")
                    .append(index + 1)
                    .append(". ")
                    .append(text(layers.get(index), "blobSum", "Unknown"))
                    .append("\n");
        }
    }

    private static void appendDescriptor(StringBuilder details, String label, JsonNode descriptor) {
        if (descriptor == null || !descriptor.isObject()) {
            return;
        }
        details.append(label)
                .append(": ")
                .append(text(descriptor, "digest", "Unknown"))
                .append("  ")
                .append(text(descriptor, "mediaType", "Unknown"))
                .append("  ")
                .append(size(descriptor))
                .append("\n");
    }

    private static int arraySize(JsonNode value) {
        return value != null && value.isArray() ? value.size() : 0;
    }

    private static String size(JsonNode descriptor) {
        JsonNode size = descriptor.get("size");
        return size == null || size.isNull() ? "Unknown size" : size.asLong() + " bytes";
    }

    private static String text(JsonNode node, String property, String defaultValue) {
        JsonNode value = node == null ? null : node.get(property);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }
}
