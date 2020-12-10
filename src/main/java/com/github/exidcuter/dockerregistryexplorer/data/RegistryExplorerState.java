package com.github.exidcuter.dockerregistryexplorer.data;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.OptionTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tdfl.docker.DockerRegistry;

import java.util.List;

@State(
        name = "com.github.exidcuter.dockerrepositoryexplorer.data.RegistryExplorerState",
        storages = {@Storage("RegistryExplorerPluginSettings.xml")}
)
public class RegistryExplorerState implements PersistentStateComponent<RegistryExplorerState> {
    @OptionTag(converter = DockerRegistryConverter.class)
    private List<DockerRegistry> dockerRepositories;

    public static RegistryExplorerState getInstance() {
        return ServiceManager.getService(RegistryExplorerState.class);
    }

    @Nullable
    @Override
    public RegistryExplorerState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull RegistryExplorerState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public List<DockerRegistry> getDockerRepositories() {
        return dockerRepositories;
    }

    public void setDockerRepositories(List<DockerRegistry> dockerRepositories) {
        this.dockerRepositories = dockerRepositories;
    }
}
