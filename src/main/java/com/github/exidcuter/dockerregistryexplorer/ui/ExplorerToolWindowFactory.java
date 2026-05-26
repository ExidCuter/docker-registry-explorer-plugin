package com.github.exidcuter.dockerregistryexplorer.ui;

import com.github.exidcuter.dockerregistryexplorer.data.RegistryExplorerState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

public class ExplorerToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        RegistryExplorerPanel panel = new RegistryExplorerPanel(
                project,
                RegistryExplorerState.getInstance(),
                new DockerRegistryOperations(),
                AppExecutorUtil.getAppExecutorService()
        );
        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.setDisposer(panel);
        toolWindow.getContentManager().addContent(content);
    }
}
