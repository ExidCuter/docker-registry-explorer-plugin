package com.github.exidcuter.dockerregistryexplorer.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddEditDialogWrapperValidationTest {
    @Test
    void acceptsRegistryUrlsAllowedByTheDialog() {
        assertTrue(AddEditDialogWrapper.isValidRegistryUrl("https://registry.example.com"));
        assertTrue(AddEditDialogWrapper.isValidRegistryUrl("http://registry.example.com:5000"));
    }

    @Test
    void rejectsBlankOrMalformedRegistryUrls() {
        assertFalse(AddEditDialogWrapper.isValidRegistryUrl(""));
        assertFalse(AddEditDialogWrapper.isValidRegistryUrl("registry.example.com"));
        assertFalse(AddEditDialogWrapper.isValidRegistryUrl(null));
    }
}
