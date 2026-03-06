package gui;

import agent.AgentLoop;
import bus.MessageBus;
import cli.RuntimeComponents;
import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.themes.FlatMacLightLaf;
import config.ConfigIO;
import config.ConfigSchema;
import corn.CronService;
import providers.CustomProvider;
import providers.LLMProvider;
import providers.ProviderRegistry;
import session.SessionManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static cli.RuntimeComponents.createRuntimeComponents;

/**
 * 基于 FlatLaf 的 Nanobot GUI（兼容修正版）
 *
 * 说明：
 * 1. 避免对不支持 style key 的组件使用 FlatClientProperties.STYLE
 * 2. 按钮/输入框继续使用 FlatLaf
 * 3. 卡片圆角用自定义 RoundedPanel，兼容性更高
 */
public class NanobotGUI extends JFrame {

    // =========================
    // 主题色
    // =========================
    private static final Color WINDOW_BG = new Color(242, 242, 247);
    private static final Color CARD_BG = new Color(255, 255, 255);
    private static final Color CHAT_BG = new Color(255, 255, 255);

    private static final Color TEXT_PRIMARY = new Color(28, 28, 30);
    private static final Color TEXT_SECONDARY = new Color(99, 99, 102);
    private static final Color TEXT_MUTED = new Color(142, 142, 147);

    private static final Color USER_COLOR = new Color(0, 102, 204);
    private static final Color BOT_COLOR = new Color(38, 38, 40);
    private static final Color SYSTEM_COLOR = new Color(130, 130, 135);
    private static final Color PROGRESS_COLOR = new Color(120, 120, 128);

    // =========================
    // UI
    // =========================
    private JTextPane chatPane;
    private JTextField inputField;
    private JButton sendButton;
    private JButton clearButton;
    private JLabel statusLabel;
    private JLabel titleLabel;
    private JLabel modelLabel;

    // =========================
    // 核心组件
    // =========================
    private ConfigSchema.Config config;
    private LLMProvider provider;
    private AgentLoop agentLoop;
    private CronService cron;
    private SessionManager sessionManager;

    // =========================
    // 状态
    // =========================
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean processing = new AtomicBoolean(false);

    private final String sessionId = "cli:direct";
    private final String cliChannel = "cli";
    private final String cliChatId = "direct";

    // =========================
    // 文本样式
    // =========================
    private Style userStyle;
    private Style botStyle;
    private Style systemStyle;
    private Style timestampStyle;
    private Style progressStyle;

    public NanobotGUI() {
        super("Nanobot");
        initializeWindow();
        initializeUI();
        initializeCore();
    }

    /**
     * 初始化窗口
     */
    private void initializeWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(860, 620));
        setSize(1020, 760);
        setLocationRelativeTo(null);

        getContentPane().setBackground(WINDOW_BG);

        try {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "Nanobot");
        } catch (Exception ignored) {
        }

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });
    }

    /**
     * 初始化 UI
     */
    private void initializeUI() {
        setLayout(new BorderLayout());
        setJMenuBar(createMenuBar());

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setOpaque(true);
        root.setBackground(WINDOW_BG);

        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildBottomInputPanel(), BorderLayout.SOUTH);

        add(root, BorderLayout.CENTER);
    }

    /**
     * 顶部区域
     */
    private JComponent buildTopBar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        titleLabel = new JLabel("🐈 Nanobot");
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 22));
        titleLabel.setForeground(TEXT_PRIMARY);

        modelLabel = new JLabel("AI Assistant");
        modelLabel.setFont(new Font("Dialog", Font.PLAIN, 13));
        modelLabel.setForeground(TEXT_MUTED);

        left.add(titleLabel);
        left.add(Box.createVerticalStrut(3));
        left.add(modelLabel);

        RoundedPanel statusWrap = new RoundedPanel(16, new Color(255, 255, 255));
        statusWrap.setLayout(new BorderLayout());
        statusWrap.setBorder(new EmptyBorder(8, 12, 8, 12));

        statusLabel = new JLabel("就绪");
        statusLabel.setFont(new Font("Dialog", Font.PLAIN, 13));
        statusLabel.setForeground(TEXT_SECONDARY);
        statusWrap.add(statusLabel, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(statusWrap);

        top.add(left, BorderLayout.WEST);
        top.add(right, BorderLayout.EAST);
        return top;
    }

    /**
     * 中间聊天区
     */
    private JComponent buildCenterPanel() {
        RoundedPanel card = new RoundedPanel(24, CARD_BG);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(12, 12, 12, 12));

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(CHAT_BG);
        chatPane.setForeground(TEXT_PRIMARY);
        chatPane.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 15));
        chatPane.setMargin(new Insets(12, 14, 12, 14));
        chatPane.setBorder(null);

        initTextStyles();

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(CHAT_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        beautifyScrollBar(scrollPane.getVerticalScrollBar());

        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    /**
     * 底部输入区
     */
    private JComponent buildBottomInputPanel() {
        RoundedPanel bottomCard = new RoundedPanel(24, CARD_BG);
        bottomCard.setLayout(new BorderLayout(10, 0));
        bottomCard.setBorder(new EmptyBorder(12, 12, 12, 12));

        inputField = new JTextField();
        inputField.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 15));
        inputField.setForeground(TEXT_PRIMARY);
        inputField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "输入消息后按 Enter 发送");
        inputField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, false);
        inputField.putClientProperty(FlatClientProperties.COMPONENT_ROUND_RECT, true);
        inputField.setPreferredSize(new Dimension(200, 42));
        inputField.addActionListener(e -> sendMessage());

        sendButton = new JButton("发送");
        sendButton.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 14));
        sendButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        sendButton.setPreferredSize(new Dimension(88, 40));
        sendButton.addActionListener(e -> sendMessage());

        clearButton = new JButton("清空");
        clearButton.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        clearButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_ROUND_RECT);
        clearButton.setPreferredSize(new Dimension(88, 40));
        clearButton.addActionListener(e -> clearChat());

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.setOpaque(false);
        rightButtons.add(clearButton);
        rightButtons.add(sendButton);

        bottomCard.add(inputField, BorderLayout.CENTER);
        bottomCard.add(rightButtons, BorderLayout.EAST);

        return bottomCard;
    }

    /**
     * 菜单栏
     */
    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu fileMenu = new JMenu("文件");
        JMenu settingsMenu = new JMenu("设置");
        JMenu helpMenu = new JMenu("帮助");

        JMenuItem statusItem = new JMenuItem("查看状态");
        statusItem.addActionListener(e -> showStatus());

        JMenuItem clearItem = new JMenuItem("清空对话");
        clearItem.addActionListener(e -> clearChat());

        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> {
            shutdown();
            System.exit(0);
        });

        fileMenu.add(statusItem);
        fileMenu.add(clearItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenuItem configItem = new JMenuItem("打开配置文件");
        configItem.addActionListener(e -> openConfig());

        JMenuItem workspaceItem = new JMenuItem("打开工作空间");
        workspaceItem.addActionListener(e -> openWorkspace());

        settingsMenu.add(configItem);
        settingsMenu.add(workspaceItem);

        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(e -> showAbout());

        helpMenu.add(aboutItem);

        bar.add(fileMenu);
        bar.add(settingsMenu);
        bar.add(helpMenu);
        return bar;
    }

    /**
     * 初始化文本样式
     */
    private void initTextStyles() {
        StyledDocument doc = chatPane.getStyledDocument();

        userStyle = doc.addStyle("UserStyle", null);
        StyleConstants.setForeground(userStyle, USER_COLOR);
        StyleConstants.setBold(userStyle, true);
        StyleConstants.setFontSize(userStyle, 15);

        botStyle = doc.addStyle("BotStyle", null);
        StyleConstants.setForeground(botStyle, BOT_COLOR);
        StyleConstants.setFontSize(botStyle, 15);

        systemStyle = doc.addStyle("SystemStyle", null);
        StyleConstants.setForeground(systemStyle, SYSTEM_COLOR);
        StyleConstants.setItalic(systemStyle, true);
        StyleConstants.setFontSize(systemStyle, 13);

        timestampStyle = doc.addStyle("TimestampStyle", null);
        StyleConstants.setForeground(timestampStyle, TEXT_MUTED);
        StyleConstants.setFontSize(timestampStyle, 12);

        progressStyle = doc.addStyle("ProgressStyle", null);
        StyleConstants.setForeground(progressStyle, PROGRESS_COLOR);
        StyleConstants.setItalic(progressStyle, true);
        StyleConstants.setFontSize(progressStyle, 13);
    }

    /**
     * 初始化核心组件
     */
    private void initializeCore() {
        try {
            RuntimeComponents rt = createRuntimeComponents();

            // 修复：必须赋值给成员变量
            this.config = rt.getConfig();

            provider = makeProvider(this.config);

            Path cronStorePath = ConfigIO.getDataDir().resolve("cron").resolve("jobs.json");
            cron = new CronService(cronStorePath, null);

            sessionManager = new SessionManager(this.config.getWorkspacePath());
            MessageBus bus = new MessageBus();

            agentLoop = new AgentLoop(
                    bus,
                    provider,
                    this.config.getWorkspacePath(),
                    this.config.getAgents().getDefaults().getModel(),
                    this.config.getAgents().getDefaults().getMaxToolIterations(),
                    this.config.getAgents().getDefaults().getTemperature(),
                    this.config.getAgents().getDefaults().getMaxTokens(),
                    this.config.getAgents().getDefaults().getMemoryWindow(),
                    this.config.getAgents().getDefaults().getReasoningEffort(),
                    this.config.getTools().getWeb().getSearch().getApiKey(),
                    this.config.getTools().getExec(),
                    cron,
                    this.config.getTools().isRestrictToWorkspace(),
                    sessionManager,
                    this.config.getTools().getMcpServers(),
                    this.config.getChannels(),
                    rt.getRuntimeSettings()
            );

            running.set(true);

            String model = this.config.getAgents().getDefaults().getModel();
            modelLabel.setText("Model · " + model);
            appendSystem("Nanobot 已启动，模型: " + model);
            updateStatus("就绪");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "初始化失败: " + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE
            );
            e.printStackTrace();
        }
    }

    /**
     * 发送消息
     */
    private void sendMessage() {
        String message = inputField.getText() == null ? "" : inputField.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        if (processing.get()) {
            return;
        }

        processing.set(true);

        appendUser(message);
        inputField.setText("");
        setInputEnabled(false);
        updateStatus("思考中...");

        CompletableFuture.runAsync(() -> {
            try {
                String resp = agentLoop.processDirect(
                        message,
                        sessionId,
                        cliChannel,
                        cliChatId,
                        this::onProgress
                ).toCompletableFuture().join();

                SwingUtilities.invokeLater(() -> {
                    if (resp != null && !resp.isBlank()) {
                        appendBot(resp);
                    }
                    updateStatus("就绪");
                    setInputEnabled(true);
                    inputField.requestFocusInWindow();
                    processing.set(false);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendSystem("错误: " + e.getMessage());
                    updateStatus("错误");
                    setInputEnabled(true);
                    inputField.requestFocusInWindow();
                    processing.set(false);
                });
            }
        });
    }

    /**
     * 设置输入区状态
     */
    private void setInputEnabled(boolean enabled) {
        inputField.setEnabled(enabled);
        sendButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
    }

    /**
     * 进度回调
     */
    private CompletionStage<Void> onProgress(String content, boolean toolHint) {
        var ch = agentLoop.getChannelsConfig();
        if (ch != null && toolHint && !ch.isSendToolHints()) {
            return CompletableFuture.completedFuture(null);
        }
        if (ch != null && !toolHint && !ch.isSendProgress()) {
            return CompletableFuture.completedFuture(null);
        }

        SwingUtilities.invokeLater(() -> appendProgress(content == null ? "" : content));
        return CompletableFuture.completedFuture(null);
    }

    private void appendUser(String message) {
        appendMessage("你", message, userStyle, botStyle);
    }

    private void appendBot(String message) {
        appendMessage("🐈 Nanobot", message, botStyle, botStyle);
    }

    private void appendSystem(String message) {
        appendMessage("系统", message, systemStyle, systemStyle);
    }

    private void appendProgress(String message) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            doc.insertString(doc.getLength(), "  ↳ " + message + "\n", progressStyle);
            scrollToBottom();
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void appendMessage(String sender, String message, Style senderStyle, Style contentStyle) {
        try {
            StyledDocument doc = chatPane.getStyledDocument();

            String timestamp = DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.now());

            doc.insertString(doc.getLength(), "[" + timestamp + "] ", timestampStyle);
            doc.insertString(doc.getLength(), sender + "\n", senderStyle);
            doc.insertString(doc.getLength(), message + "\n\n", contentStyle);

            scrollToBottom();
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() ->
                chatPane.setCaretPosition(chatPane.getDocument().getLength())
        );
    }

    private void clearChat() {
        try {
            StyledDocument doc = chatPane.getStyledDocument();
            doc.remove(0, doc.getLength());
            appendSystem("对话已清空");
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void updateStatus(String status) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(status));
    }

    private void showStatus() {
        if (config == null) {
            JOptionPane.showMessageDialog(this, "配置尚未初始化", "状态", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path configPath = ConfigIO.getConfigPath();
        Path workspace = config.getWorkspacePath();

        StringBuilder sb = new StringBuilder();
        sb.append("🐈 Nanobot 状态\n\n");
        sb.append("配置文件: ").append(configPath).append(Files.exists(configPath) ? " ✓" : " ✗").append("\n");
        sb.append("工作空间: ").append(workspace).append(Files.exists(workspace) ? " ✓" : " ✗").append("\n");
        sb.append("模型: ").append(config.getAgents().getDefaults().getModel()).append("\n\n");

        sb.append("提供商状态:\n");
        for (ProviderRegistry.ProviderSpec spec : ProviderRegistry.PROVIDERS) {
            var p = config.getProviders().getByName(spec.getName());
            if (p == null) continue;

            if (spec.isOauth()) {
                sb.append("  ").append(spec.getLabel()).append(": ✓ (OAuth)\n");
            } else if (spec.isLocal()) {
                String base = p.getApiBase();
                sb.append("  ").append(spec.getLabel()).append(": ")
                        .append(base != null && !base.isBlank() ? ("✓ " + base) : "未设置")
                        .append("\n");
            } else {
                boolean hasKey = p.getApiKey() != null && !p.getApiKey().isBlank();
                sb.append("  ").append(spec.getLabel()).append(": ").append(hasKey ? "✓" : "未设置").append("\n");
            }
        }

        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 14));
        ta.setBorder(new EmptyBorder(12, 12, 12, 12));

        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(580, 360));

        JOptionPane.showMessageDialog(this, sp, "状态", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openConfig() {
        try {
            Path configPath = ConfigIO.getConfigPath();
            if (Files.exists(configPath)) {
                Desktop.getDesktop().open(configPath.toFile());
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "配置文件不存在: " + configPath,
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "无法打开配置文件: " + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void openWorkspace() {
        try {
            if (config == null) {
                JOptionPane.showMessageDialog(this, "配置尚未初始化", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Path workspace = config.getWorkspacePath();
            if (Files.exists(workspace)) {
                Desktop.getDesktop().open(workspace.toFile());
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "工作空间不存在: " + workspace,
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(
                    this,
                    "无法打开工作空间: " + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void showAbout() {
        String about = ""
                + "🐈 Nanobot - AI Assistant\n\n"
                + "版本: 1.0\n"
                + "UI: FlatLaf / FlatMacLightLaf\n"
                + "风格: macOS 风格圆角桌面界面\n\n"
                + "基于 Java Swing 构建";

        JOptionPane.showMessageDialog(this, about, "关于", JOptionPane.INFORMATION_MESSAGE);
    }

    private void shutdown() {
        running.set(false);

        if (agentLoop != null) {
            try {
                agentLoop.stop();
            } catch (Exception ignored) {
            }
            try {
                agentLoop.closeMcp().toCompletableFuture().join();
            } catch (Exception ignored) {
            }
        }

        if (cron != null) {
            try {
                cron.stop();
            } catch (Exception ignored) {
            }
        }
    }

    static LLMProvider makeProvider(ConfigSchema.Config config) {
        String model = config.getAgents().getDefaults().getModel();
        String providerName = config.getProviderName(model);
        var p = config.getProvider(model);

        if ("openai_codex".equals(providerName) || (model != null && model.startsWith("openai-codex/"))) {
            throw new RuntimeException("Error: OpenAI Codex is not supported in this Java build.");
        }

        String apiKey = (p != null && p.getApiKey() != null) ? p.getApiKey() : null;
        String apiBase = config.getApiBase(model);

        if ("custom".equals(providerName)) {
            if (apiBase == null || apiBase.isBlank()) apiBase = "http://localhost:8000/v1";
            if (apiKey == null || apiKey.isBlank()) apiKey = "no-key";
            return new CustomProvider(apiKey, apiBase, model);
        }

        if (apiBase != null && !apiBase.isBlank()) {
            if (apiKey == null || apiKey.isBlank()) {
                apiKey = "no-key";
            }
            return new CustomProvider(apiKey, apiBase, model);
        }

        ProviderRegistry.ProviderSpec spec = ProviderRegistry.findByName(providerName);
        boolean isOauth = spec != null && spec.isOauth();
        boolean isBedrock = model != null && model.startsWith("bedrock/");
        boolean hasKey = apiKey != null && !apiKey.isBlank();

        if (!isBedrock && !hasKey && !isOauth) {
            throw new RuntimeException("Error: No API key configured (and no api_base set).");
        }

        throw new RuntimeException(
                "Error: Provider '" + providerName + "' is not supported in this Java build. " +
                        "Tip: set tools/providers api_base to an OpenAI-compatible endpoint so CustomProvider can be used."
        );
    }

    /**
     * 美化滚动条
     */
    private void beautifyScrollBar(JScrollBar scrollBar) {
        scrollBar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(200, 200, 205);
                trackColor = new Color(0, 0, 0, 0);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return zeroButton();
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(185, 185, 190));
                g2.fillRoundRect(
                        thumbBounds.x + 3,
                        thumbBounds.y + 2,
                        thumbBounds.width - 6,
                        thumbBounds.height - 4,
                        10,
                        10
                );
                g2.dispose();
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            }

            private JButton zeroButton() {
                JButton button = new JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }
        });

        scrollBar.setOpaque(false);
        scrollBar.setPreferredSize(new Dimension(12, Integer.MAX_VALUE));
    }

    /**
     * 圆角卡片面板
     */
    static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color bg;

        public RoundedPanel(int arc, Color bg) {
            this.arc = arc;
            this.bg = bg;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 阴影
            g2.setColor(new Color(0, 0, 0, 10));
            g2.fillRoundRect(0, 2, getWidth(), getHeight() - 1, arc, arc);

            // 主体
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight() - 2, arc, arc);

            // 描边
            g2.setColor(new Color(0, 0, 0, 12));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 3, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        try {
            FlatMacLightLaf.setup();

            UIManager.put("TitlePane.unifiedBackground", true);
            UIManager.put("MenuBar.embedded", true);

            UIManager.put("Button.arc", 18);
            UIManager.put("Component.arc", 18);
            UIManager.put("TextComponent.arc", 18);

            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("ScrollBar.showButtons", false);

            UIManager.put("Panel.background", WINDOW_BG);
            UIManager.put("TextPane.background", CHAT_BG);
            UIManager.put("TextField.background", Color.WHITE);
            UIManager.put("Button.default.background", new Color(0, 122, 255));
            UIManager.put("Button.default.foreground", Color.WHITE);

            Font font = new Font("Microsoft YaHei UI", Font.PLAIN, 14);
            FlatLaf.setPreferredFontFamily(font.getFamily());
            FlatLaf.setPreferredLightFontFamily(font.getFamily());
            FlatLaf.setPreferredSemiboldFontFamily(font.getFamily());
        } catch (Exception e) {
            e.printStackTrace();
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }

        SwingUtilities.invokeLater(() -> {
            NanobotGUI gui = new NanobotGUI();
            gui.setVisible(true);
        });
    }
}