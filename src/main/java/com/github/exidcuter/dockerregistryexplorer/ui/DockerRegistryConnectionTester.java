package com.github.exidcuter.dockerregistryexplorer.ui;

import org.tdfl.docker.DockerRegistry;
import org.tdfl.docker.model.LoginCredentials;

final class DockerRegistryConnectionTester implements RegistryConnectionTester {
    @Override
    public void testConnection(LoginCredentials credentials) {
        new DockerRegistry(credentials, true).getCatalog(0, 1);
    }
}
