package com.github.exidcuter.dockerregistryexplorer.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;

final class ManifestInfoDialogWrapper extends DialogWrapper {
    private final String details;

    ManifestInfoDialogWrapper(Project project, String imageReference, String details) {
        super(project, true);
        this.details = details;
        setTitle("Manifest: " + imageReference);
        init();
        setOKButtonText("Close");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JBTextArea detailsArea = new JBTextArea(details, 18, 72);
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(false);
        detailsArea.setCaretPosition(0);
        return new JBScrollPane(detailsArea);
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }
}
