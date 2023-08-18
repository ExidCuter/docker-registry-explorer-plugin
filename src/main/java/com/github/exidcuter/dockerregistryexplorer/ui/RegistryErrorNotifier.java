package com.github.exidcuter.dockerregistryexplorer.ui;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class RegistryErrorNotifier {

    public static void notifyError(@Nullable Project project, String content) {
        NotificationGroupManager.getInstance().getNotificationGroup("DockerRegistry Notifications")
                .createNotification("Docker registry error", "", content, NotificationType.ERROR)
                .notify(project);
    }

    public static void notifySuccess(@Nullable Project project, String content) {
        NotificationGroupManager.getInstance().getNotificationGroup("DockerRegistry Notifications")
                .createNotification(content, NotificationType.INFORMATION)
                .notify(project);
    }
}
