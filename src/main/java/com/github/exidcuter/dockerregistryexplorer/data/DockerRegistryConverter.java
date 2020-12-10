package com.github.exidcuter.dockerregistryexplorer.data;

import com.intellij.util.xmlb.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tdfl.docker.DockerRegistry;
import org.tdfl.docker.model.LoginCredentials;

import java.util.ArrayList;
import java.util.List;

public class DockerRegistryConverter extends Converter<List<DockerRegistry>> {
    @Override
    public @Nullable List<DockerRegistry> fromString(@NotNull String value) {
        List<DockerRegistry> repositoryList = new ArrayList<>();
        String[] strings = value.split("\n");

        for (String row : strings) {
            String[] data = row.split(";");

            if (row.length() > 0) {
                repositoryList.add(new DockerRegistry(LoginCredentials.builder()
                        .username(data[0])
                        .password(data[1])
                        .registryURL(data[2])
                        .build()
                ));
            }
        }

        return repositoryList;
    }

    @Override
    public @Nullable String toString(@NotNull List<DockerRegistry> value) {
        StringBuilder sb = new StringBuilder();

        for (DockerRegistry dr : value) {
            LoginCredentials loginCredentials = dr.getLoginCredentials();

            sb.append(loginCredentials.getUsername())
                    .append(";")
                    .append(loginCredentials.getPassword())
                    .append(";")
                    .append(loginCredentials.getRegistryURL())
                    .append("\n");
        }


        return sb.toString();
    }
}
