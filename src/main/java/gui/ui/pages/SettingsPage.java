package gui.ui.pages;

import gui.ui.update.UpdateInfo;
import gui.ui.update.UpdateService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 设置页面 - Claude 风格设计
 */
public class SettingsPage extends VBox {

    private VBox settingsContainer;
    private ScrollPane scrollPane;
    private gui.ui.BackendBridge backendBridge;
    private Consumer<String> onModelChanged;

    // ---- 自动更新相关 ----
    private final UpdateService updateService = new UpdateService();
    private VBox updateContentBox;
    private UpdateInfo pendingUpdate;
    private String updateErrorMessage;

    // 更新弹窗引用
    private Stage updateDialog;
    private VBox updateDialogContent;
    private ProgressBar updateDialogProgress;

    // ---- Claude 风格颜色常量 ----
    private static final String COLOR_CANVAS = "#faf9f5";
    private static final String COLOR_SURFACE_CARD = "#efe9de";
    private static final String COLOR_SURFACE_SOFT = "#f5f0e8";
    private static final String COLOR_PRIMARY = "#cc785c";
    private static final String COLOR_PRIMARY_ACTIVE = "#a9583e";
    private static final String COLOR_INK = "#141413";
    private static final String COLOR_MUTED = "#6c6a64";
    private static final String COLOR_MUTED_SOFT = "#8e8b82";
    private static final String COLOR_HAIRLINE = "#e6dfd8";
    private static final String COLOR_HAIRLINE_SOFT = "#ebe6df";
    private static final String COLOR_SUCCESS = "#5db872";
    private static final String COLOR_ON_PRIMARY = "#ffffff";
    private static final String COLOR_SURFACE_CREAM_STRONG = "#e8e0d2";
    private static final String COLOR_SURFACE_DARK = "#181715";
    private static final String COLOR_ON_DARK = "#faf9f5";

    // 当前选中的提供者
    private String selectedProvider = null;

    public SettingsPage() {
        setSpacing(0);
        setStyle("-fx-background-color: " + COLOR_CANVAS + ";");

        scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background: " + COLOR_CANVAS + "; -fx-background-color: transparent; -fx-border-color: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        VBox content = new VBox(32);
        content.setPadding(new Insets(48, 32, 32, 32));
        content.setAlignment(Pos.TOP_CENTER);

        // 页面标题 - 使用衬线风格
        Label title = new Label("设置");
        title.setStyle("-fx-font-family: 'Georgia', 'Times New Roman', serif; -fx-font-size: 36px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + "; -fx-letter-spacing: -0.5px;");

        Label subtitle = new Label("管理你的应用配置和偏好");
        subtitle.setStyle("-fx-font-size: 15px; -fx-text-fill: " + COLOR_MUTED + "; -fx-font-weight: 400;");

        VBox titleBox = new VBox(8);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().addAll(title, subtitle);

        // 设置容器
        settingsContainer = new VBox(16);
        settingsContainer.setMaxWidth(680);

        content.getChildren().addAll(titleBox, settingsContainer);

        scrollPane.setContent(content);
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createModelSection() {
        VBox section = new VBox(16);
        section.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-background-radius: 12px; -fx-padding: 24px;");

        Label sectionTitle = new Label("模型配置");
        sectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-text-transform: uppercase; -fx-letter-spacing: 1.5px;");

        // 默认模型
        HBox modelRow = createSettingRow("默认模型", "选择用于对话的 AI 模型", "claude-sonnet-4 ▸");

        section.getChildren().addAll(sectionTitle, modelRow);
        return section;
    }

    private HBox createSettingRow(String titleText, String desc, String value) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox infoBox = new VBox(4);
        Label titleLabel = new Label(titleText);
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");
        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED_SOFT + ";");
        infoBox.getChildren().addAll(titleLabel, descLabel);

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 8px; -fx-padding: 0 16px; -fx-pref-height: 40px; -fx-alignment: center; -fx-font-family: 'JetBrains Mono', 'SF Mono', monospace; -fx-font-size: 13px; -fx-text-fill: " + COLOR_INK + "; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 8px;");

        row.getChildren().addAll(infoBox, valueLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        return row;
    }

    private VBox createGatewaySection() {
        VBox section = new VBox(16);
        section.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-background-radius: 12px; -fx-padding: 24px;");

        Label sectionTitle = new Label("Gateway 状态");
        sectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-text-transform: uppercase; -fx-letter-spacing: 1.5px;");

        HBox statusRow = new HBox(12);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // 状态指示器 - 默认关闭
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + COLOR_MUTED_SOFT + "; -fx-font-size: 10px;");

        VBox infoBox = new VBox(2);
        Label statusLabel = new Label("Gateway 已关闭");
        statusLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");
        Label detailLabel = new Label("点击启动以开启服务");
        detailLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED_SOFT + ";");
        infoBox.getChildren().addAll(statusLabel, detailLabel);

        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(actionBox, Priority.ALWAYS);

        // 启动按钮 - 点击弹窗提示功能未开放
        Button startBtn = new Button("启动");
        startBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand;");
        startBtn.setOnMouseEntered(e -> startBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY_ACTIVE + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand;"));
        startBtn.setOnMouseExited(e -> startBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand;"));
        startBtn.setOnAction(e -> showFeatureNotAvailableDialog());

        // 停止按钮 - 禁用状态
        Button stopBtn = new Button("停止");
        stopBtn.setStyle("-fx-background-color: " + COLOR_HAIRLINE_SOFT + "; -fx-text-fill: " + COLOR_MUTED + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: not-allowed;");
        stopBtn.setDisable(true);

        actionBox.getChildren().addAll(startBtn, stopBtn);
        statusRow.getChildren().addAll(dot, infoBox, actionBox);

        section.getChildren().addAll(sectionTitle, statusRow);
        return section;
    }

    private VBox createChannelsSection() {
        VBox section = new VBox(16);
        section.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-background-radius: 12px; -fx-padding: 24px;");

        Label sectionTitle = new Label("通道");
        sectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-text-transform: uppercase; -fx-letter-spacing: 1.5px;");

        VBox channelsBox = new VBox(8);

        String[][] channels = {
            {"📱", "Telegram", "即时通讯机器人", "true"},
            {"💬", "飞书", "企业协作平台", "false"},
            {"📧", "Email", "电子邮件通知", "true"}
        };

        for (String[] ch : channels) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(14, 16, 14, 16));
            row.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 8px;");

            Label icon = new Label(ch[0]);
            icon.setStyle("-fx-font-size: 18px; -fx-background-color: " + COLOR_SURFACE_SOFT + "; -fx-background-radius: 8px; -fx-padding: 8px;");

            VBox infoBox = new VBox(2);
            Label nameLabel = new Label(ch[1]);
            nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");
            Label descLabel = new Label(ch[2]);
            descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + COLOR_MUTED_SOFT + ";");
            infoBox.getChildren().addAll(nameLabel, descLabel);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            Label statusLabel = new Label("true".equals(ch[3]) ? "已配置" : "未配置");
            String statusColor = "true".equals(ch[3]) ? COLOR_SUCCESS : COLOR_MUTED;
            String statusBg = "true".equals(ch[3]) ? "rgba(93, 184, 114, 0.1)" : COLOR_SURFACE_SOFT;
            statusLabel.setStyle("-fx-background-color: " + statusBg + "; -fx-background-radius: 9999px; -fx-padding: 4px 12px; -fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + statusColor + ";");

            row.getChildren().addAll(icon, infoBox, statusLabel);
            channelsBox.getChildren().add(row);
        }

        section.getChildren().addAll(sectionTitle, channelsBox);
        return section;
    }

    private Line createSeparator() {
        Line line = new Line();
        line.setEndX(680);
        line.setStyle("-fx-stroke: " + COLOR_HAIRLINE_SOFT + ";");
        line.setVisible(false);
        return line;
    }

    public void setBackendBridge(gui.ui.BackendBridge bridge) {
        this.backendBridge = bridge;
        refresh();
    }

    public void refresh() {
        if (backendBridge == null) return;
        // 保存滚动位置
        double savedVvalue = scrollPane.getVvalue();
        settingsContainer.getChildren().clear();
        settingsContainer.getChildren().add(buildModelSection());
        settingsContainer.getChildren().add(buildGatewaySection());
        settingsContainer.getChildren().add(buildChannelsSection());
        settingsContainer.getChildren().add(buildUpdateSection());
        // 恢复滚动位置（延迟到布局完成后，避免 vvalue 被 clamp）
        final double restoreVvalue = savedVvalue;
        Platform.runLater(() -> scrollPane.setVvalue(restoreVvalue));
    }

    private VBox buildModelSection() {
        config.Config cfg = backendBridge.getConfig();
        String currentModel = cfg.getAgents().getDefaults().getModel();
        String currentProvider = cfg.getProviderName(currentModel);

        // 初始化选中的提供者
        if (selectedProvider == null && currentProvider != null) {
            selectedProvider = currentProvider;
        }

        VBox section = new VBox(16);
        section.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-background-radius: 12px; -fx-padding: 24px;");

        Label sectionTitle = new Label("模型配置");
        sectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-text-transform: uppercase; -fx-letter-spacing: 1.5px;");

        // 默认模型标题
        VBox modelHeader = new VBox(4);
        Label modelTitle = new Label("默认模型");
        modelTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");
        Label modelDesc = new Label("选择用于对话的 AI 模型");
        modelDesc.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED_SOFT + ";");
        modelHeader.getChildren().addAll(modelTitle, modelDesc);

        // 按提供商分组收集模型
        config.provider.ProvidersConfig provCfg = cfg.getProviders();
        java.util.Set<String> allNames = provCfg.names();

        // 动态排序
        java.util.List<String> dynOrder = new java.util.ArrayList<>();
        for (providers.ProviderRegistry.ProviderSpec spec : providers.ProviderRegistry.PROVIDERS) {
            if (allNames.contains(spec.getName())) {
                dynOrder.add(spec.getName());
            }
        }
        for (String name : allNames) {
            if (!dynOrder.contains(name)) {
                dynOrder.add(name);
            }
        }

        // 提供者标签
        TilePane providerTabs = new TilePane();
        providerTabs.setHgap(6);
        providerTabs.setVgap(6);
        providerTabs.setPrefColumns(4);

        for (String pn : dynOrder) {
            config.provider.ProviderConfig pc = provCfg.getByName(pn);
            if (pc == null || pc.getModelConfigs() == null || pc.getModelConfigs().isEmpty()) continue;

            String pl = pn.substring(0, 1).toUpperCase() + pn.substring(1);
            boolean isSelected = pn.equals(selectedProvider);

            Button tab = new Button(isSelected ? pl + " ✓" : pl);
            // 统一基础样式（所有 tab 都有边框）
            String baseStyle = "-fx-background-radius: 8px; -fx-border-radius: 8px; -fx-border-width: 1.5px;"
                + " -fx-padding: 6px 14px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand;";
            String defaultStyle = baseStyle
                + " -fx-background-color: " + COLOR_CANVAS + "; -fx-text-fill: " + COLOR_MUTED
                + "; -fx-border-color: " + COLOR_HAIRLINE + ";";
            String selectedStyle = baseStyle
                + " -fx-background-color: " + COLOR_SURFACE_CREAM_STRONG + "; -fx-text-fill: " + COLOR_INK
                + "; -fx-border-color: " + COLOR_PRIMARY + ";";
            String hoverStyle = baseStyle
                + " -fx-background-color: " + COLOR_CANVAS + "; -fx-text-fill: " + COLOR_INK
                + "; -fx-border-color: " + COLOR_MUTED_SOFT + ";";

            tab.setStyle(isSelected ? selectedStyle : defaultStyle);

            // hover 效果：未选中时边框加深
            if (!isSelected) {
                tab.setOnMouseEntered(e -> tab.setStyle(hoverStyle));
                tab.setOnMouseExited(e -> tab.setStyle(defaultStyle));
            }

            tab.setOnAction(e -> {
                selectedProvider = pn;
                refresh();
            });

            providerTabs.getChildren().add(tab);
        }

        // 当前选中提供商的模型区域（圆角框 + 提供商名）
        VBox modelArea = new VBox(6);
        if (selectedProvider != null) {
            config.provider.ProviderConfig selectedPc = provCfg.getByName(selectedProvider);
            if (selectedPc != null && selectedPc.getModelConfigs() != null) {
                // 提供商小标题
                String displayName = selectedProvider.substring(0, 1).toUpperCase() + selectedProvider.substring(1);
                Label providerLabel = new Label("● " + displayName);
                providerLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-padding: 0 0 4px 0;");

                // 模型卡片网格（带圆角底框）
                TilePane modelGrid = new TilePane();
                modelGrid.setHgap(8);
                modelGrid.setVgap(8);
                modelGrid.setPrefColumns(2);
                modelGrid.setStyle("-fx-background-color: " + COLOR_SURFACE_SOFT + "; -fx-background-radius: 8px; -fx-padding: 10px;");

                for (config.provider.model.ModelConfig mc : selectedPc.getModelConfigs()) {
                    if (mc.getModel() == null || mc.getModel().isBlank()) continue;
                    boolean isSelected = mc.getModel().equals(currentModel);
                    VBox card = createModelCard(selectedProvider + "/" + mc.getModel(), mc.getModel(), isSelected);
                    modelGrid.getChildren().add(card);
                }

                modelArea.getChildren().addAll(providerLabel, modelGrid);
            }
        }

        // 快速模型联动区域
        VBox fastModelSection = createFastModelSection(cfg, provCfg, currentModel, currentProvider);

        // 编辑配置文件按钮
        Button openConfigBtn = new Button("📄 编辑配置文件");
        openConfigBtn.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-text-fill: " + COLOR_INK + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 8px;");
        openConfigBtn.setOnAction(e -> showEditorChoiceDialog());
        HBox btnBox = new HBox(openConfigBtn);
        btnBox.setAlignment(Pos.CENTER);

        section.getChildren().addAll(sectionTitle, modelHeader, providerTabs, modelArea, fastModelSection, btnBox);
        return section;
    }

    private VBox createModelCard(String fullModelName, String modelName, boolean isSelected) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(14, 16, 14, 16));

        String borderColor = isSelected ? COLOR_PRIMARY : "transparent";
        String style = "-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 8px; -fx-border-color: " + borderColor + "; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;";
        card.setStyle(style);

        Label nameLabel = new Label(modelName);
        nameLabel.setStyle("-fx-font-family: 'JetBrains Mono', 'SF Mono', monospace; -fx-font-size: 13px; -fx-font-weight: 400; -fx-text-fill: " + COLOR_INK + ";");

        Label descLabel = new Label(isSelected ? "✓ 当前选择" : "点击选择");
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isSelected ? COLOR_PRIMARY : COLOR_MUTED_SOFT) + ";");

        card.getChildren().addAll(nameLabel, descLabel);

        card.setOnMouseClicked(e -> {
            config.Config cfg = backendBridge.getConfig();
            cfg.getAgents().getDefaults().setModel(modelName);
            if (selectedProvider != null && !selectedProvider.isBlank()) {
                cfg.getAgents().getDefaults().setProvider(selectedProvider);
            }
            try { config.ConfigIO.saveConfig(cfg, null); } catch (Exception ignored) {}
            if (onModelChanged != null) onModelChanged.accept(modelName);
            refresh();
        });

        card.setOnMouseEntered(e -> {
            if (!isSelected) {
                card.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 8px; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;");
            }
        });

        card.setOnMouseExited(e -> {
            if (!isSelected) {
                card.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 8px; -fx-border-color: transparent; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;");
            }
        });

        return card;
    }

    private VBox createFastModelSection(config.Config cfg, config.provider.ProvidersConfig provCfg, String currentModel, String currentProvider) {
        VBox section = new VBox(12);
        section.setStyle("-fx-background-color: " + COLOR_SURFACE_SOFT + "; -fx-background-radius: 8px; -fx-padding: 16px;");

        // 标题行
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("⚡");
        icon.setStyle("-fx-font-size: 14px; -fx-background-color: " + COLOR_SURFACE_DARK + "; -fx-text-fill: " + COLOR_ON_DARK + "; -fx-background-radius: 6px; -fx-padding: 6px;");

        Label title = new Label("快速模型");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");

        Label badge = new Label("已联动");
        badge.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 9999px; -fx-padding: 2px 10px; -fx-font-size: 11px; -fx-font-weight: 500; -fx-letter-spacing: 0.5px;");

        header.getChildren().addAll(icon, title, badge);

        // 描述
        String providerDisplay = selectedProvider != null ? selectedProvider.substring(0, 1).toUpperCase() + selectedProvider.substring(1) : "默认";
        Label desc = new Label("轻量级任务使用，已自动匹配 " + providerDisplay + " 提供者");
        desc.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED + ";");

        // 快速模型卡片
        TilePane fastGrid = new TilePane();
        fastGrid.setHgap(8);
        fastGrid.setVgap(8);
        fastGrid.setPrefColumns(2);

        String currentFast = cfg.getAgents().getDefaults().getFastModel();

        // "无 (使用默认)" 选项
        VBox noFastCard = createFastModelCard("", "无 (使用默认)", currentFast == null || currentFast.isBlank(), cfg);
        fastGrid.getChildren().add(noFastCard);

        // 当前提供者的模型
        if (selectedProvider != null) {
            config.provider.ProviderConfig selectedPc = provCfg.getByName(selectedProvider);
            if (selectedPc != null && selectedPc.getModelConfigs() != null) {
                for (config.provider.model.ModelConfig mc : selectedPc.getModelConfigs()) {
                    if (mc.getModel() == null || mc.getModel().isBlank()) continue;
                    boolean isSelected = mc.getModel().equals(currentFast);
                    VBox card = createFastModelCard(mc.getModel(), mc.getModel(), isSelected, cfg);
                    fastGrid.getChildren().add(card);
                }
            }
        }

        section.getChildren().addAll(header, desc, fastGrid);
        return section;
    }

    private VBox createFastModelCard(String modelName, String displayName, boolean isSelected, config.Config cfg) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(14, 16, 14, 16));

        String borderColor = isSelected ? COLOR_PRIMARY : "transparent";
        String style = "-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 8px; -fx-border-color: " + borderColor + "; -fx-border-radius: 8px; -fx-border-width: 1.5px; -fx-cursor: hand;";
        card.setStyle(style);

        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-font-family: 'JetBrains Mono', 'SF Mono', monospace; -fx-font-size: 13px; -fx-font-weight: 400; -fx-text-fill: " + COLOR_INK + ";");

        Label descLabel = new Label(isSelected ? "✓ 当前选择" : (modelName.isEmpty() ? "回退到默认模型" : "点击选择"));
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + (isSelected ? COLOR_PRIMARY : COLOR_MUTED_SOFT) + ";");

        card.getChildren().addAll(nameLabel, descLabel);

        card.setOnMouseClicked(e -> {
            cfg.getAgents().getDefaults().setFastModel(modelName.isEmpty() ? null : modelName);
            try { config.ConfigIO.saveConfig(cfg, null); } catch (Exception ignored) {}
            refresh();
        });

        return card;
    }

    private VBox buildGatewaySection() {
        VBox section = new VBox(16);
        section.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-background-radius: 12px; -fx-padding: 24px;");

        Label sectionTitle = new Label("Gateway 状态");
        sectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-text-transform: uppercase; -fx-letter-spacing: 1.5px;");

        HBox statusRow = new HBox(12);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // 状态指示器 - 默认关闭
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + COLOR_MUTED_SOFT + "; -fx-font-size: 10px;");

        VBox infoBox = new VBox(2);
        Label statusLabel = new Label("Gateway 已关闭");
        statusLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");
        Label detailLabel = new Label("点击启动以开启服务");
        detailLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED_SOFT + ";");
        infoBox.getChildren().addAll(statusLabel, detailLabel);

        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(actionBox, Priority.ALWAYS);

        // 启动按钮
        Button startBtn = new Button("启动");
        startBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand;");
        startBtn.setOnMouseEntered(e -> startBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY_ACTIVE + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand;"));
        startBtn.setOnMouseExited(e -> startBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand;"));
        startBtn.setOnAction(e -> showFeatureNotAvailableDialog());

        // 停止按钮 - 禁用状态
        Button stopBtn = new Button("停止");
        stopBtn.setStyle("-fx-background-color: " + COLOR_HAIRLINE_SOFT + "; -fx-text-fill: " + COLOR_MUTED + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: not-allowed;");
        stopBtn.setDisable(true);

        actionBox.getChildren().addAll(startBtn, stopBtn);
        statusRow.getChildren().addAll(dot, infoBox, actionBox);

        section.getChildren().addAll(sectionTitle, statusRow);
        return section;
    }

    private void showFeatureNotAvailableDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32));
        root.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 16px; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 16px;");

        Label icon = new Label("🚧");
        icon.setStyle("-fx-font-size: 28px; -fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-background-radius: 50%; -fx-padding: 14px;");

        Label title = new Label("功能未开放");
        title.setStyle("-fx-font-family: 'Georgia', 'Times New Roman', serif; -fx-font-size: 22px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + "; -fx-letter-spacing: -0.3px;");

        Label desc = new Label("Gateway 功能正在开发中，\n敬请期待后续版本更新");
        desc.setStyle("-fx-font-size: 14px; -fx-text-fill: " + COLOR_MUTED + "; -fx-text-alignment: center; -fx-wrap-text: true;");

        Button okBtn = new Button("知道了");
        okBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 10px 32px; -fx-font-size: 14px; -fx-font-weight: 500; -fx-cursor: hand;");
        okBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(icon, title, desc, okBtn);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    public void setOnModelChanged(Consumer<String> callback) {
        this.onModelChanged = callback;
    }

    private VBox buildChannelsSection() {
        config.channel.ChannelsConfig ch = backendBridge.getConfig().getChannels();
        VBox section = new VBox(16);
        section.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-background-radius: 12px; -fx-padding: 24px;");

        Label sectionTitle = new Label("通道");
        sectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-text-transform: uppercase; -fx-letter-spacing: 1.5px;");

        VBox channelsBox = new VBox(8);

        addChannelRow(channelsBox, "📱", "Telegram", "即时通讯机器人",
            ch.getTelegram().getToken() != null && !ch.getTelegram().getToken().isBlank());
        addChannelRow(channelsBox, "💬", "飞书", "企业协作平台",
            ch.getFeishu().getAppId() != null && !ch.getFeishu().getAppId().isBlank());
        addChannelRow(channelsBox, "📧", "Email", "电子邮件通知",
            ch.getEmail().getSmtpHost() != null && !ch.getEmail().getSmtpHost().isBlank());

        section.getChildren().addAll(sectionTitle, channelsBox);
        return section;
    }

    private void addChannelRow(VBox box, String icon, String name, String desc, boolean configured) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 16, 14, 16));
        row.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 8px;");

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 18px; -fx-background-color: " + COLOR_SURFACE_SOFT + "; -fx-background-radius: 8px; -fx-padding: 8px;");

        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");
        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: " + COLOR_MUTED_SOFT + ";");
        infoBox.getChildren().addAll(nameLabel, descLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Label statusLabel = new Label(configured ? "已配置" : "未配置");
        String statusColor = configured ? COLOR_SUCCESS : COLOR_MUTED;
        String statusBg = configured ? "rgba(93, 184, 114, 0.1)" : COLOR_SURFACE_SOFT;
        statusLabel.setStyle("-fx-background-color: " + statusBg + "; -fx-background-radius: 9999px; -fx-padding: 4px 12px; -fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + statusColor + ";");

        row.getChildren().addAll(iconLabel, infoBox, statusLabel);
        box.getChildren().add(row);
    }

    /** 编辑器选择对话框 */
    private void showEditorChoiceDialog() {
        Path configPath = config.ConfigIO.getConfigPath();
        if (!Files.exists(configPath)) return;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("选择编辑器");

        VBox root = new VBox(20);
        root.setPadding(new Insets(28));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 16px; -fx-border-width: 1px;");

        Label title = new Label("📄 选择编辑器");
        title.setStyle("-fx-font-family: 'Georgia', 'Times New Roman', serif; -fx-font-size: 20px; -fx-text-fill: " + COLOR_INK + "; -fx-font-weight: 500;");

        Label desc = new Label("请选择使用哪种编辑器打开配置文件");
        desc.setStyle("-fx-font-size: 14px; -fx-text-fill: " + COLOR_MUTED + ";");

        Button systemBtn = new Button("🖥 系统默认编辑器");
        systemBtn.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-text-fill: " + COLOR_INK + "; -fx-background-radius: 10px; -fx-padding: 12px 24px; -fx-font-size: 14px; -fx-font-weight: 500; -fx-cursor: hand; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 10px; -fx-border-width: 1px;");
        systemBtn.setPrefWidth(240);
        systemBtn.setOnAction(e -> {
            dialog.close();
            try {
                java.awt.Desktop.getDesktop().edit(configPath.toFile());
            } catch (IOException ex) {
                try {
                    java.awt.Desktop.getDesktop().open(configPath.toFile());
                } catch (IOException ex2) {
                    javafx.application.Platform.runLater(this::showJsonEditor);
                }
            }
        });

        Button builtinBtn = new Button("📝 本项目内置编辑器");
        builtinBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 10px; -fx-padding: 12px 24px; -fx-font-size: 14px; -fx-font-weight: 600; -fx-cursor: hand;");
        builtinBtn.setPrefWidth(240);
        builtinBtn.setOnAction(e -> {
            dialog.close();
            javafx.application.Platform.runLater(this::showJsonEditor);
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + COLOR_MUTED + "; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 6px 16px;");
        cancelBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(title, desc, systemBtn, builtinBtn, cancelBtn);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /** 内置 JSON 编辑对话框 */
    private void showJsonEditor() {
        Path configPath = config.ConfigIO.getConfigPath();
        if (!Files.exists(configPath)) return;

        String originalContent;
        try {
            originalContent = Files.readString(configPath);
        } catch (IOException e) {
            return;
        }

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("编辑配置文件");

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 16px; -fx-border-width: 1px;");
        root.setMinWidth(700);
        root.setMinHeight(500);

        Label title = new Label("📄 " + configPath.getFileName().toString());
        title.setStyle("-fx-font-size: 15px; -fx-text-fill: " + COLOR_INK + "; -fx-font-weight: 500;");

        TextArea editor = new TextArea(originalContent);
        editor.setStyle("-fx-font-family: 'JetBrains Mono','Fira Code',monospace; -fx-font-size: 13px; -fx-text-fill: " + COLOR_INK + "; -fx-background-color: " + COLOR_CANVAS + "; -fx-control-inner-background: " + COLOR_CANVAS + "; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 8px;");
        editor.setWrapText(false);
        VBox.setVgrow(editor, Priority.ALWAYS);

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button formatBtn = new Button("格式化");
        formatBtn.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-text-fill: " + COLOR_MUTED + "; -fx-background-radius: 8px; -fx-padding: 6px 16px; -fx-cursor: hand; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 8px; -fx-border-width: 1px;");
        formatBtn.setOnAction(e -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                Object json = mapper.readValue(editor.getText(), Object.class);
                String pretty = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                editor.setText(pretty);
            } catch (Exception ex) {
                editor.setStyle(editor.getStyle() + "; -fx-control-inner-background: #fff0f0;");
            }
        });

        Button saveBtn = new Button("保存");
        saveBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 6px 20px; -fx-font-weight: 600; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            try {
                new ObjectMapper().readValue(editor.getText(), Object.class);
                Files.writeString(configPath, editor.getText());
                backendBridge.reloadConfigFromDisk();
                refresh();
                dialog.close();
            } catch (Exception ex) {
                editor.setStyle(editor.getStyle().replace("-fx-control-inner-background: " + COLOR_CANVAS + ";", "-fx-control-inner-background: #fff0f0;"));
            }
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-text-fill: " + COLOR_MUTED + "; -fx-background-radius: 8px; -fx-padding: 6px 16px; -fx-cursor: hand; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 8px; -fx-border-width: 1px;");
        cancelBtn.setOnAction(e -> dialog.close());

        btnRow.getChildren().addAll(formatBtn, cancelBtn, saveBtn);

        root.getChildren().addAll(title, editor, btnRow);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /**
     * 构建"更新"设置区域
     */
    private VBox buildUpdateSection() {
        VBox section = new VBox(16);
        section.setStyle("-fx-background-color: " + COLOR_SURFACE_CARD + "; -fx-background-radius: 12px; -fx-padding: 24px;");

        Label sectionTitle = new Label("更新");
        sectionTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-text-transform: uppercase; -fx-letter-spacing: 1.5px;");

        HBox versionRow = new HBox(16);
        versionRow.setAlignment(Pos.CENTER_LEFT);
        Label versionLabel = new Label("当前版本");
        versionLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");
        Label versionValue = new Label("v" + UpdateService.getCurrentVersion());
        versionValue.setStyle("-fx-font-family: 'JetBrains Mono', 'SF Mono', monospace; -fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED + ";");
        HBox.setHgrow(versionLabel, Priority.ALWAYS);
        versionRow.getChildren().addAll(versionLabel, versionValue);

        updateContentBox = new VBox(12);
        buildUpdateContent();

        section.getChildren().addAll(sectionTitle, versionRow, updateContentBox);
        return section;
    }

    private void buildUpdateContent() {
        updateContentBox.getChildren().clear();
        Button checkBtn = new Button("检查更新");
        checkBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 7px 16px; -fx-font-size: 13px; -fx-font-weight: 500; -fx-cursor: hand;");
        checkBtn.setOnAction(e -> showUpdateDialog());
        updateContentBox.getChildren().add(checkBtn);
    }

    // ======================== 更新弹窗 ========================

    private void showUpdateDialog() {
        updateDialog = new Stage();
        updateDialog.initModality(Modality.APPLICATION_MODAL);
        updateDialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(32));
        root.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-background-radius: 16px; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 16px;");
        root.setMinWidth(420);

        Label title = new Label("检查更新");
        title.setStyle("-fx-font-family: 'Georgia', 'Times New Roman', serif; -fx-font-size: 22px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + "; -fx-letter-spacing: -0.3px;");

        updateDialogContent = new VBox(12);
        updateDialogContent.setAlignment(Pos.CENTER);

        // 初始状态：检查中
        Label checkingLabel = new Label("正在检查更新...");
        checkingLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + COLOR_MUTED + ";");
        updateDialogContent.getChildren().add(checkingLabel);

        root.getChildren().addAll(title, updateDialogContent);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        updateDialog.setScene(scene);
        updateDialog.show();

        // 后台检查
        CompletableFuture.runAsync(() -> {
            try {
                UpdateInfo info = updateService.checkForUpdates();
                Platform.runLater(() -> {
                    if (info != null) {
                        pendingUpdate = info;
                        showDialogUpdateAvailable();
                    } else {
                        showDialogUpToDate();
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateErrorMessage = e.getMessage();
                    showDialogError(() -> {
                        updateDialog.close();
                        showUpdateDialog();
                    });
                });
            }
        });
    }

    private void showDialogUpToDate() {
        updateDialogContent.getChildren().clear();

        Label icon = new Label("✓");
        icon.setStyle("-fx-font-size: 28px; -fx-text-fill: " + COLOR_SUCCESS + "; -fx-background-color: rgba(93,184,114,0.1); -fx-background-radius: 50%; -fx-padding: 12px 18px;");

        Label label = new Label("已是最新版本");
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + ";");

        Label detail = new Label("当前版本 v" + UpdateService.getCurrentVersion());
        detail.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED + ";");

        Button okBtn = new Button("知道了");
        okBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 8px 28px; -fx-font-size: 14px; -fx-font-weight: 500; -fx-cursor: hand;");
        okBtn.setOnAction(e -> updateDialog.close());

        updateDialogContent.getChildren().addAll(icon, label, detail, okBtn);
        updateDialog.sizeToScene();
    }

    private void showDialogUpdateAvailable() {
        updateDialogContent.getChildren().clear();
        updateDialogContent.setAlignment(Pos.CENTER_LEFT);

        if (pendingUpdate == null) return;

        // 新版本标题
        Label newVerLabel = new Label("发现新版本");
        newVerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 600; -fx-text-fill: " + COLOR_PRIMARY + ";");

        Label verValueLabel = new Label("v" + pendingUpdate.getVersion());
        verValueLabel.setStyle("-fx-font-family: 'JetBrains Mono', 'SF Mono', monospace; -fx-font-size: 15px; -fx-text-fill: " + COLOR_INK + "; -fx-padding: 2px 0 0 0;");

        String sizeStr = pendingUpdate.getSize() > 0
            ? String.format("%.1f MB", pendingUpdate.getSize() / 1_048_576.0) : "未知";
        Label sizeLabel = new Label("大小: " + sizeStr);
        sizeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED + ";");

        updateDialogContent.getChildren().addAll(newVerLabel, verValueLabel, sizeLabel);

        // 更新内容
        if (pendingUpdate.getChangelog() != null && !pendingUpdate.getChangelog().isBlank()) {
            Label changelogTitle = new Label("更新内容");
            changelogTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_MUTED + "; -fx-padding: 8px 0 0 0;");

            Label changelogBody = new Label(pendingUpdate.getChangelog());
            changelogBody.setStyle("-fx-font-size: 13px; -fx-text-fill: " + COLOR_MUTED_SOFT + "; -fx-wrap-text: true; -fx-padding: 0 0 8px 0;");

            updateDialogContent.getChildren().addAll(changelogTitle, changelogBody);
        }

        updateDialogContent.setAlignment(Pos.CENTER);

        // 按钮行
        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER);

        Button cancelBtn = new Button("稍后再说");
        cancelBtn.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-text-fill: " + COLOR_MUTED + "; -fx-background-radius: 8px; -fx-padding: 8px 20px; -fx-font-size: 14px; -fx-font-weight: 500; -fx-cursor: hand; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 8px;");
        cancelBtn.setOnAction(e -> updateDialog.close());

        Button downloadBtn = new Button("立即更新");
        downloadBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 8px 24px; -fx-font-size: 14px; -fx-font-weight: 600; -fx-cursor: hand;");
        downloadBtn.setOnAction(e -> startDialogDownload());

        btnRow.getChildren().addAll(cancelBtn, downloadBtn);
        updateDialogContent.getChildren().add(btnRow);
        updateDialog.sizeToScene();
    }

    private void startDialogDownload() {
        if (pendingUpdate == null || pendingUpdate.getUrl() == null || pendingUpdate.getUrl().isBlank()) {
            updateErrorMessage = "下载地址无效";
            showDialogError(() -> updateDialog.close());
            return;
        }

        updateDialogContent.getChildren().clear();
        updateDialogContent.setAlignment(Pos.CENTER);

        Label dlLabel = new Label("正在下载更新...");
        dlLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + COLOR_MUTED + ";");

        updateDialogProgress = new ProgressBar(0);
        updateDialogProgress.setPrefWidth(320);
        updateDialogProgress.setStyle("-fx-accent: " + COLOR_PRIMARY + ";");

        updateDialogContent.getChildren().addAll(dlLabel, updateDialogProgress);
        updateDialog.sizeToScene();

        CompletableFuture.runAsync(() -> {
            try {
                Consumer<Double> progressCb = p -> Platform.runLater(() -> {
                    if (updateDialogProgress != null) {
                        updateDialogProgress.setProgress(p);
                    }
                });

                updateService.downloadAndReplace(pendingUpdate, progressCb);
                Platform.runLater(this::showDialogReady);
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateErrorMessage = "下载失败: " + e.getMessage();
                    showDialogError(() -> updateDialog.close());
                });
            }
        });
    }

    private void showDialogReady() {
        updateDialogContent.getChildren().clear();
        updateDialogContent.setAlignment(Pos.CENTER);

        Label icon = new Label("✓");
        icon.setStyle("-fx-font-size: 28px; -fx-text-fill: " + COLOR_SUCCESS + "; -fx-background-color: rgba(93,184,114,0.1); -fx-background-radius: 50%; -fx-padding: 12px 18px;");

        String readyMsg = "更新已就绪，请重启应用" + (pendingUpdate != null ? " v" + pendingUpdate.getVersion() : "");
        Label readyLabel = new Label(readyMsg);
        readyLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 500; -fx-text-fill: " + COLOR_INK + "; -fx-wrap-text: true;");

        Button okBtn = new Button("知道了");
        okBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 8px 28px; -fx-font-size: 14px; -fx-font-weight: 500; -fx-cursor: hand;");
        okBtn.setOnAction(e -> updateDialog.close());

        updateDialogContent.getChildren().addAll(icon, readyLabel, okBtn);
        updateDialog.sizeToScene();
    }

    private void showDialogError(Runnable onRetry) {
        updateDialogContent.getChildren().clear();
        updateDialogContent.setAlignment(Pos.CENTER);

        Label icon = new Label("✗");
        icon.setStyle("-fx-font-size: 28px; -fx-text-fill: " + COLOR_PRIMARY + "; -fx-background-color: rgba(204,120,92,0.1); -fx-background-radius: 50%; -fx-padding: 12px 18px;");

        Label errorLabel = new Label(updateErrorMessage != null ? updateErrorMessage : "未知错误");
        errorLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: " + COLOR_MUTED + "; -fx-wrap-text: true;");

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER);

        Button closeBtn = new Button("关闭");
        closeBtn.setStyle("-fx-background-color: " + COLOR_CANVAS + "; -fx-text-fill: " + COLOR_MUTED + "; -fx-background-radius: 8px; -fx-padding: 8px 20px; -fx-font-size: 14px; -fx-font-weight: 500; -fx-cursor: hand; -fx-border-color: " + COLOR_HAIRLINE + "; -fx-border-radius: 8px;");
        closeBtn.setOnAction(e -> updateDialog.close());

        Button retryBtn = new Button("重试");
        retryBtn.setStyle("-fx-background-color: " + COLOR_PRIMARY + "; -fx-text-fill: " + COLOR_ON_PRIMARY + "; -fx-background-radius: 8px; -fx-padding: 8px 24px; -fx-font-size: 14px; -fx-font-weight: 500; -fx-cursor: hand;");
        retryBtn.setOnAction(e -> {
            if (onRetry != null) onRetry.run();
        });

        btnRow.getChildren().addAll(closeBtn, retryBtn);
        updateDialogContent.getChildren().addAll(icon, errorLabel, btnRow);
        updateDialog.sizeToScene();
    }
}