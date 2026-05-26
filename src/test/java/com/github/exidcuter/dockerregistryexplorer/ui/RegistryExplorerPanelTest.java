package com.github.exidcuter.dockerregistryexplorer.ui;

import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryExplorerPanelTest {
    @Test
    void treeUsesTheToolWindowThemeSurfaceForUnselectedRows() throws Exception {
        runOnEdt(() -> {
            Tree tree = new Tree();
            DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
            JScrollPane scrollPane = new JScrollPane(tree);
            JPanel decoratorPanel = new JPanel();
            decoratorPanel.add(scrollPane);

            RegistryExplorerPanel.configureTreeAppearance(tree);
            RegistryExplorerPanel.configureRendererAppearance(renderer);
            RegistryExplorerPanel.configureDecoratedTreeAppearance(decoratorPanel);

            assertTrue(tree.isOpaque());
            assertEquals(JBUI.CurrentTheme.ToolWindow.background(), tree.getBackground());
            assertEquals(JBUI.CurrentTheme.ToolWindow.background(), renderer.getBackgroundNonSelectionColor());
            assertTrue(renderer.getBackgroundSelectionColor() != null);
            assertTrue(decoratorPanel.isOpaque());
            assertEquals(JBUI.CurrentTheme.ToolWindow.background(), decoratorPanel.getBackground());
            assertTrue(scrollPane.isOpaque());
            assertEquals(JBUI.CurrentTheme.ToolWindow.background(), scrollPane.getBackground());
            assertTrue(scrollPane.getViewport().isOpaque());
            assertEquals(JBUI.CurrentTheme.ToolWindow.background(), scrollPane.getViewport().getBackground());
        });
    }

    @Test
    void tagContextMenuProvidesShowInfoAndDeleteActions() throws Exception {
        runOnEdt(() -> {
            List<String> actions = new ArrayList<>();
            JPopupMenu menu = RegistryExplorerPanel.createTagContextMenu(
                    RegistryTreeNode.tag("latest"),
                    () -> actions.add("info"),
                    () -> actions.add("delete")
            );

            assertEquals(3, menu.getComponentCount());
            JMenuItem showInfo = assertInstanceOf(JMenuItem.class, menu.getComponent(0));
            assertInstanceOf(JPopupMenu.Separator.class, menu.getComponent(1));
            JMenuItem delete = assertInstanceOf(JMenuItem.class, menu.getComponent(2));
            assertEquals("Show Info", showInfo.getText());
            assertEquals("Delete", delete.getText());
            assertTrue(delete.isEnabled());

            showInfo.doClick();
            delete.doClick();
            assertEquals(List.of("info", "delete"), actions);
        });
    }

    @Test
    void contextMenuDisablesDeleteWhilePendingAndExcludesNonTagNodes() throws Exception {
        runOnEdt(() -> {
            RegistryTreeNode deletingTag = RegistryTreeNode.tag("latest");
            deletingTag.setDeleting(true);
            List<String> actions = new ArrayList<>();
            JPopupMenu menu = RegistryExplorerPanel.createTagContextMenu(
                    deletingTag,
                    () -> actions.add("info"),
                    () -> actions.add("delete")
            );

            JMenuItem delete = assertInstanceOf(JMenuItem.class, menu.getComponent(2));
            assertFalse(delete.isEnabled());
            delete.doClick();
            assertEquals(List.of(), actions);

            assertNull(RegistryExplorerPanel.createTagContextMenu(
                    RegistryTreeNode.image("image"),
                    () -> actions.add("info"),
                    () -> actions.add("delete")
            ));
            assertNull(RegistryExplorerPanel.createTagContextMenu(
                    RegistryTreeNode.loading(),
                    () -> actions.add("info"),
                    () -> actions.add("delete")
            ));
        });
    }

    private static void runOnEdt(Runnable task) throws Exception {
        SwingUtilities.invokeAndWait(task);
    }
}
