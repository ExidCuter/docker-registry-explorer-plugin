package com.github.exidcuter.dockerregistryexplorer.ui;

import org.tdfl.docker.model.LoginCredentials;

interface RegistryConnectionTester {
    void testConnection(LoginCredentials credentials) throws Exception;
}
