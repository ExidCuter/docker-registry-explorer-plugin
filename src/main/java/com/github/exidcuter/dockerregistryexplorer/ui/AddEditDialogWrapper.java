package com.github.exidcuter.dockerregistryexplorer.ui;

import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tdfl.docker.model.LoginCredentials;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.util.concurrent.Executor;


public class AddEditDialogWrapper extends DialogWrapper {
    private static final String URL_REGEX = "(https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?:\\/\\/(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})";

    private JBTextField username;
    private JBPasswordField password;
    private JBTextField registryURL;
    private Action testConnectionAction;
    private JButton testConnectionButton;
    private ComponentValidator testConnectionValidator;
    private Color testConnectionDefaultForeground;
    private String testConnectionFailure;
    private final RegistryConnectionTestRunner connectionTestRunner;
    private boolean testingConnection;

    public AddEditDialogWrapper() {
        this(new DockerRegistryConnectionTester(), AppExecutorUtil.getAppExecutorService());
    }

    AddEditDialogWrapper(RegistryConnectionTester tester, Executor workerExecutor) {
        super(true);
        this.connectionTestRunner = createConnectionTestRunner(tester, workerExecutor);
        initFields();
        initActions();
        init();
        initConnectionFeedback();
        setTitle("Add New Registry");
        updateActions();
    }

    public AddEditDialogWrapper(LoginCredentials loginCredentials) {
        this(loginCredentials, new DockerRegistryConnectionTester(), AppExecutorUtil.getAppExecutorService());
    }

    AddEditDialogWrapper(LoginCredentials loginCredentials, RegistryConnectionTester tester, Executor workerExecutor) {
        super(true);
        this.connectionTestRunner = createConnectionTestRunner(tester, workerExecutor);
        initFields();
        initActions();
        init();
        initConnectionFeedback();
        setTitle("Edit Registry Entry");
        username.setText(loginCredentials.getUsername());
        password.setText(loginCredentials.getPassword());
        registryURL.setText(loginCredentials.getRegistryURL());
        updateActions();
    }

    private void initFields() {
        username = new JBTextField(30);
        password = new JBPasswordField();
        password.setColumns(30);
        registryURL = new JBTextField(30);
        registryURL.getEmptyText().setText("https://registry.example.com");

        new ComponentValidator(this.getDisposable())
                .withValidator(this::validateUrl)
                .andStartOnFocusLost()
                .andRegisterOnDocumentListener(registryURL)
                .installOn(registryURL);

        registryURL.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                credentialsChanged();
            }
        });
        username.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                credentialsChanged();
            }
        });
        password.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                credentialsChanged();
            }
        });
    }

    private void initActions() {
        testConnectionAction = new AbstractAction("Test Connection") {
            @Override
            public void actionPerformed(ActionEvent event) {
                connectionTestRunner.testConnection(getLoginCredentials());
            }
        };
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Registry URL:", registryURL, 8, false)
                .addLabeledComponent("Username:", username, 8, false)
                .addLabeledComponent("Password:", password, 0, false)
                .getPanel();
    }

    @Override
    protected Action[] createLeftSideActions() {
        return new Action[]{testConnectionAction};
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        return validateUrl();
    }

    static boolean isValidRegistryUrl(String value) {
        return value != null && value.matches(URL_REGEX);
    }

    private ValidationInfo validateUrl() {
        return isValidRegistryUrl(registryURL.getText())
                ? null
                : new ValidationInfo("Please enter a valid URL", registryURL);
    }

    private void updateActions() {
        setOKActionEnabled(isValidRegistryUrl(registryURL.getText()));
        testConnectionAction.setEnabled(isValidRegistryUrl(registryURL.getText()) && !testingConnection);
    }

    private void initConnectionFeedback() {
        testConnectionButton = getButton(testConnectionAction);
        if (testConnectionButton == null) {
            return;
        }

        testConnectionDefaultForeground = testConnectionButton.getForeground();
        testConnectionValidator = new ComponentValidator(getDisposable())
                .withValidator(() -> testConnectionFailure == null
                        ? null
                        : new ValidationInfo("Connection failed: " + testConnectionFailure, testConnectionButton)
                                .withOKEnabled());
        testConnectionValidator.installOn(testConnectionButton);
    }

    private void credentialsChanged() {
        connectionTestRunner.invalidate();
        testingConnection = false;
        clearConnectionFeedback();
        updateActions();
    }

    private void clearConnectionFeedback() {
        testConnectionFailure = null;
        if (testConnectionValidator != null) {
            testConnectionValidator.revalidate();
        }
        if (testConnectionButton != null) {
            testConnectionButton.setForeground(testConnectionDefaultForeground);
            testConnectionButton.setToolTipText(null);
        }
    }

    private void showConnectionSucceeded() {
        clearConnectionFeedback();
        if (testConnectionButton != null) {
            testConnectionButton.setForeground(UIUtil.getLabelSuccessForeground());
            testConnectionButton.setToolTipText("Connection successful.");
        }
    }

    private void showConnectionFailed(String message) {
        clearConnectionFeedback();
        testConnectionFailure = message;
        if (testConnectionValidator != null) {
            testConnectionValidator.revalidate();
        }
    }

    private RegistryConnectionTestRunner createConnectionTestRunner(
            RegistryConnectionTester tester,
            Executor workerExecutor
    ) {
        return new RegistryConnectionTestRunner(
                tester,
                workerExecutor,
                javax.swing.SwingUtilities::invokeLater,
                new RegistryConnectionTestRunner.Listener() {
                    @Override
                    public void testingStarted() {
                        testingConnection = true;
                        clearConnectionFeedback();
                        if (testConnectionButton != null) {
                            testConnectionButton.setToolTipText("Testing connection...");
                        }
                        updateActions();
                    }

                    @Override
                    public void testingSucceeded() {
                        testingConnection = false;
                        updateActions();
                        showConnectionSucceeded();
                    }

                    @Override
                    public void testingFailed(String message) {
                        testingConnection = false;
                        updateActions();
                        showConnectionFailed(message);
                    }
                }
        );
    }

    @Override
    protected void dispose() {
        connectionTestRunner.dispose();
        super.dispose();
    }

    public LoginCredentials getLoginCredentials() {
        return LoginCredentials.builder()
                .username(username.getText())
                .password(new String(password.getPassword()))
                .registryURL(registryURL.getText())
                .build();
    }
}
