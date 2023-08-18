package com.github.exidcuter.dockerregistryexplorer.ui;

import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tdfl.docker.model.LoginCredentials;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.function.Supplier;


public class AddEditDialogWrapper extends DialogWrapper {
    private static final String urlRegex = "(https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})";

    private JBTextField username;
    private JBPasswordField password;
    private JBTextField registryURL;

    public AddEditDialogWrapper() {
        super(true);
        initFields();
        init();
        setTitle("Add New Registry");
    }

    public AddEditDialogWrapper(LoginCredentials loginCredentials) {
        super(true);
        initFields();
        init();
        setTitle("Edit Registry Entry");
        username.setText(loginCredentials.getUsername());
        password.setText(loginCredentials.getPassword());
        registryURL.setText(loginCredentials.getRegistryURL());
    }

    private void initFields() {
        username = new JBTextField();
        password = new JBPasswordField();
        registryURL = new JBTextField();

        new ComponentValidator(this.getDisposable()).withValidator((Supplier<ValidationInfo>) () -> {
            if (registryURL.getText().matches(urlRegex)) {
                return null;
            } else {
                return new ValidationInfo("Please enter a valid URL", registryURL);
            }
        }).andStartOnFocusLost().installOn(registryURL);

        new ComponentValidator(this.getDisposable()).andStartOnFocusLost().installOn(registryURL);

        registryURL.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                ComponentValidator.getInstance(registryURL).ifPresent(ComponentValidator::revalidate);
            }
        });
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setSize(1500, 300);
        GridBagLayout layout = new GridBagLayout();

        JLabel usernameLabel = new JLabel("Username:");
        JLabel passwordLabel = new JLabel("Password:");
        JLabel registryUrlLabel = new JLabel("Registry URL:");

        username.setPreferredSize(new Dimension(150, 32));
        password.setPreferredSize(new Dimension(150, 32));
        registryURL.setPreferredSize(new Dimension(150, 32));

        panel.setLayout(layout);
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(usernameLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.ipadx = 30;
        panel.add(username, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(passwordLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.ipadx = 30;
        panel.add(password, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(registryUrlLabel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.ipadx = 30;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(registryURL, gbc);

        return panel;
    }

    public LoginCredentials getLoginCredentials() {
        return LoginCredentials.builder()
                .username(username.getText())
                .password(new String(password.getPassword()))
                .registryURL(registryURL.getText())
                .build();
    }
}
