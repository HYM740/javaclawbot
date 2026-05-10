package gui.ui.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SettingsPage extends VBox {

    private VBox settingsContainer;
    private gui.ui.BackendBridge backendBridge;
    private Consumer<String> onModelChanged;

    public SettingsPage() {
        setSpacing(0);
        setStyle("-fx-background-color: #f1ede1;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        scrollPane.setFitToWidth(true);

        VBox content = new VBox(48);
        content.setPadding(new Insets(40, 24, 24, 24));
        content.setAlignment(Pos.TOP_CENTER);

        // 页面标题
        Label title = new Label("设置");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label("管理你的应用配置和偏好");
        subtitle.setStyle("-fx-font-size: 17px; -fx-text-fill: rgba(0, 0, 0, 0.5); -fx-font-weight: 500;");

        VBox titleBox = new VBox(8);
        titleBox.setAlignment(Pos.CENTER);
        titleBox.getChildren().addAll(title, subtitle);

        // 设置容器
        settingsContainer = new VBox(32);
        settingsContainer.setMaxWidth(800);

        // 模型设置
        settingsContainer.getChildren().add(createModelSection());
        settingsContainer.getChildren().add(createSeparator());

        // Gateway 状态
        settingsContainer.getChildren().add(createGatewaySection());
        settingsContainer.getChildren().add(createSeparator());

        // 通道设置
        settingsContainer.getChildren().add(createChannelsSection());

        content.getChildren().addAll(titleBox, settingsContainer);

        scrollPane.setContent(content);
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    private VBox createModelSection() {
        VBox section = new VBox(16);

        Label sectionTitle = new Label("模型");
        sectionTitle.getStyleClass().add("section-title");

        // 默认模型
        HBox modelRow = createSettingRow("默认模型", "选择用于对话的 AI 模型", "claude-sonnet-4 \u25BE");

        // API Key
        HBox apiKeyRow = createSettingRow("API 密钥", "用于认证模型服务", "sk-\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022");

        section.getChildren().addAll(sectionTitle, modelRow, apiKeyRow);
        return section;
    }

    private HBox createSettingRow(String titleText, String desc, String value) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox infoBox = new VBox(4);
        Label titleLabel = new Label(titleText);
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");
        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");
        infoBox.getChildren().addAll(titleLabel, descLabel);

        Label valueLabel = new Label(value);
        valueLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.03); -fx-background-radius: 12px; -fx-padding: 0 16px; -fx-pref-height: 40px; -fx-alignment: center; -fx-font-family: monospace; -fx-font-size: 13px;");

        row.getChildren().addAll(infoBox, valueLabel);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        return row;
    }

    private VBox createGatewaySection() {
        VBox section = new VBox(16);

        Label sectionTitle = new Label("Gateway 状态");
        sectionTitle.getStyleClass().add("section-title");

        VBox statusCard = new VBox(12);
        statusCard.setStyle("-fx-background-color: rgba(0, 0, 0, 0.02); -fx-background-radius: 12px; -fx-border-color: rgba(0, 0, 0, 0.05); -fx-border-radius: 12px; -fx-border-width: 1px;");
        statusCard.setPadding(new Insets(16));

        HBox statusRow = new HBox(12);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        Label dot = new Label("\u25CF");
        dot.setStyle("-fx-text-fill: #22c55e; -fx-font-size: 8px;");

        VBox infoBox = new VBox(2);
        Label statusLabel = new Label("Gateway 运行中");
        statusLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");
        Label detailLabel = new Label("端口: 18789 · 延迟: 12ms");
        detailLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");
        infoBox.getChildren().addAll(statusLabel, detailLabel);

        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(actionBox, Priority.ALWAYS);

        Button restartBtn = new Button("重启");
        restartBtn.getStyleClass().add("pill-button");
        restartBtn.setPrefHeight(32);

        Button stopBtn = new Button("停止");
        stopBtn.getStyleClass().add("pill-button");
        stopBtn.setPrefHeight(32);

        actionBox.getChildren().addAll(restartBtn, stopBtn);
        statusRow.getChildren().addAll(dot, infoBox, actionBox);

        statusCard.getChildren().add(statusRow);
        section.getChildren().addAll(sectionTitle, statusCard);
        return section;
    }

    private VBox createChannelsSection() {
        VBox section = new VBox(16);

        Label sectionTitle = new Label("通道");
        sectionTitle.getStyleClass().add("section-title");

        VBox channelsBox = new VBox(12);

        String[][] channels = {
            {"\uD83D\uDCF1", "Telegram", "已配置"},
            {"\uD83D\uDCAC", "飞书", "未配置"}
        };

        for (String[] ch : channels) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));
            row.setStyle("-fx-background-color: rgba(0, 0, 0, 0.02); -fx-background-radius: 12px;");

            Label icon = new Label(ch[0]);
            icon.setStyle("-fx-font-size: 20px;");

            VBox infoBox = new VBox(2);
            Label nameLabel = new Label(ch[1]);
            nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");
            Label statusLabel = new Label(ch[2]);
            statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");
            infoBox.getChildren().addAll(nameLabel, statusLabel);

            row.getChildren().addAll(icon, infoBox);
            HBox.setHgrow(infoBox, Priority.ALWAYS);

            channelsBox.getChildren().add(row);
        }

        section.getChildren().addAll(sectionTitle, channelsBox);
        return section;
    }

    private Line createSeparator() {
        Line line = new Line();
        line.setEndX(800);
        line.setStyle("-fx-stroke: rgba(0, 0, 0, 0.05);");
        return line;
    }

    public void setBackendBridge(gui.ui.BackendBridge bridge) {
        this.backendBridge = bridge;
        refresh();
    }

    public void refresh() {
        if (backendBridge == null) return;
        settingsContainer.getChildren().clear();
        settingsContainer.getChildren().add(buildModelSection());
        settingsContainer.getChildren().add(createSeparator());
        settingsContainer.getChildren().add(createGatewaySection());
        settingsContainer.getChildren().add(createSeparator());
        settingsContainer.getChildren().add(buildChannelsSection());
    }

    /** ComboBox 条目类型：模型名 或 提供商分隔标题 */
    private static class ModelItem {
        final String text;
        final String modelName;     // null 代表是标题
        final String providerName;  // 提供商名（标题用）或该模型所属的 provider
        ModelItem(String text, String model, String provider) {
            this.text = text; this.modelName = model; this.providerName = provider;
        }
        boolean isHeader() { return modelName == null; }
        @Override public String toString() { return text; }
    }

    private VBox buildModelSection() {
        config.Config cfg = backendBridge.getConfig();
        String currentModel = cfg.getAgents().getDefaults().getModel();
        String currentProvider = cfg.getProviderName(currentModel);

        VBox section = new VBox(16);
        Label sectionTitle = new Label("模型");
        sectionTitle.getStyleClass().add("section-title");

        // 按提供商分组收集模型
        String[] providerOrder = {"openai","anthropic","deepseek","openrouter","groq",
            "zhipu","dashscope","gemini","moonshot","minimax","aihubmix",
            "siliconflow","volcengine","vllm","githubCopilot","custom"};
        String[] providerLabels = {"OpenAI","Anthropic","DeepSeek","OpenRouter","Groq",
            "智谱 GLM","阿里云 DashScope","Google Gemini","Moonshot","MiniMax","AIHubMix",
            "SiliconFlow","火山引擎","vLLM","GitHub Copilot","Custom"};

        config.provider.ProvidersConfig provCfg = cfg.getProviders();
        java.util.List<ModelItem> items = new java.util.ArrayList<>();

        // 当前模型排最前，带提供商前缀
        String currentLabel = (currentProvider != null && !currentProvider.isBlank())
            ? "  " + currentProvider + "/" + currentModel + "  (当前)"
            : "  " + currentModel + "  (当前)";
        items.add(new ModelItem(currentLabel, currentModel, currentProvider != null ? currentProvider : ""));

        for (int i = 0; i < providerOrder.length; i++) {
            String pn = providerOrder[i];
            String pl = providerLabels[i];
            config.provider.ProviderConfig pc = provCfg.getByName(pn);
            if (pc == null || pc.getModelConfigs() == null || pc.getModelConfigs().isEmpty()) continue;

            // 检查此 provider 是否有 API key（标记状态）
            boolean hasKey = pc.getApiKey() != null && !pc.getApiKey().isBlank();
            String headerText = "▸ " + pl + (hasKey ? "" : " (未配置 Key)");
            if (pn.equals(currentProvider)) headerText = "▸ " + pl + " ★";

            // 添加提供商标题
            items.add(new ModelItem(headerText, null, pn));

            for (config.provider.model.ModelConfig mc : pc.getModelConfigs()) {
                if (mc.getModel() != null && !mc.getModel().isBlank()
                    && !mc.getModel().equals(currentModel)) {
                    items.add(new ModelItem("     " + pn + "/" + mc.getModel(), mc.getModel(), pn));
                }
            }
        }

        // 模型选择行
        HBox modelRow = new HBox(16);
        modelRow.setAlignment(Pos.CENTER_LEFT);

        VBox infoBox = new VBox(4);
        Label titleLabel = new Label("默认模型");
        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");
        Label descLabel = new Label("选择用于对话的 AI 模型");
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");
        infoBox.getChildren().addAll(titleLabel, descLabel);

        // 保留完整列表供搜索
        java.util.List<ModelItem> allItems = new java.util.ArrayList<>(items);

        ComboBox<ModelItem> modelCombo = new ComboBox<>();
        modelCombo.getItems().addAll(items);
        if (!items.isEmpty()) modelCombo.setValue(items.get(0));
        modelCombo.setEditable(true);
        modelCombo.setStyle("-fx-background-color: rgba(0, 0, 0, 0.03); -fx-background-radius: 12px;"
            + " -fx-border-color: transparent; -fx-font-size: 13px;");
        modelCombo.setPrefHeight(40);
        modelCombo.setMaxWidth(350);

        // 自定义单元格渲染：标题项不可选、灰色
        modelCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(ModelItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setDisable(false); return; }
                setText(item.text);
                if (item.isHeader()) {
                    setDisable(true);
                    setStyle("-fx-text-fill: rgba(0,0,0,0.4); -fx-font-weight: 700;"
                        + " -fx-font-size: 11px; -fx-font-family: sans-serif;");
                } else {
                    setDisable(false);
                    setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
                }
            }
        });

        modelCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(ModelItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.modelName != null ? item.modelName : item.text);
                setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
            }
        });

        // ---- 搜索过滤：使用独立 Popup，不修改 ComboBox items 避免 IndexOutOfBounds ----
        javafx.stage.Popup searchPopup = new javafx.stage.Popup();
        searchPopup.setAutoHide(true);
        VBox searchList = new VBox(2);
        searchList.setStyle("-fx-background-color: rgba(255,255,255,0.97); -fx-background-radius: 10px;"
            + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 10px; -fx-border-width: 1px;");
        searchList.setPadding(new Insets(4));
        searchPopup.getContent().add(searchList);

        modelCombo.getEditor().textProperty().addListener((obs, old, text) -> {
            searchPopup.hide();
            if (text == null || text.isBlank()) return;
            String lower = text.toLowerCase().trim();
            if (lower.isEmpty()) return;

            // Filter models
            java.util.List<ModelItem> filtered = new java.util.ArrayList<>();
            for (ModelItem mi : allItems) {
                if (mi.isHeader()) {
                    filtered.add(mi);
                } else if (mi.modelName != null && mi.modelName.toLowerCase().contains(lower)) {
                    filtered.add(mi);
                }
            }
            // Clean orphan headers
            java.util.List<ModelItem> clean = new java.util.ArrayList<>();
            for (int i = 0; i < filtered.size(); i++) {
                ModelItem mi = filtered.get(i);
                if (mi.isHeader()) {
                    if (i + 1 < filtered.size() && !filtered.get(i + 1).isHeader()) {
                        clean.add(mi);
                    }
                } else {
                    clean.add(mi);
                }
            }
            if (clean.isEmpty()) return;

            // Hide native dropdown so it doesn't overlap the search popup
            modelCombo.hide();

            // Build popup rows
            searchList.getChildren().clear();
            for (ModelItem mi : clean) {
                javafx.scene.control.Label row = new javafx.scene.control.Label(mi.text);
                row.setPadding(new Insets(4, 10, 4, 10));
                row.setPrefHeight(24);
                if (mi.isHeader()) {
                    row.setStyle("-fx-text-fill: rgba(0,0,0,0.4); -fx-font-weight: 700;"
                        + " -fx-font-size: 11px; -fx-font-family: sans-serif;");
                    row.setDisable(true);
                } else {
                    row.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;"
                        + " -fx-cursor: hand;");
                    row.setOnMouseClicked(ev -> {
                        searchPopup.hide();
                        modelCombo.setValue(mi);
                        modelCombo.getEditor().setText(mi.modelName);
                    });
                }
                searchList.getChildren().add(row);
            }
            // Show popup below ComboBox
            if (!searchList.getChildren().isEmpty()) {
                var bounds = modelCombo.localToScreen(modelCombo.getBoundsInLocal());
                searchPopup.show(modelCombo.getScene().getWindow(),
                    bounds.getMinX(), bounds.getMaxY() + 2);
            }
        });

        // Hide search popup when ComboBox dropdown is opened (user clicked arrow)
        modelCombo.setOnShowing(e -> searchPopup.hide());

        modelCombo.setOnAction(e -> {
            // editable ComboBox getValue() may return String when user types
            Object value = modelCombo.getValue();
            if (!(value instanceof ModelItem)) return;
            ModelItem selected = (ModelItem) value;
            if (selected.isHeader()) return;
            if (selected.modelName.equals(currentModel)) return;
            String pn = selected.providerName;
            // 同时更新 model 和 provider
            cfg.getAgents().getDefaults().setModel(selected.modelName);
            if (pn != null && !pn.isBlank()) {
                cfg.getAgents().getDefaults().setProvider(pn);
            }
            try { config.ConfigIO.saveConfig(cfg, null); } catch (Exception ignored) {}
            if (onModelChanged != null) onModelChanged.accept(selected.modelName);
            refresh();
        });

        modelRow.getChildren().addAll(infoBox, modelCombo);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        infoBox.setMinWidth(220);

        // ---- 快速模型选择 ----
        String currentFast = cfg.getAgents().getDefaults().getFastModel();
        HBox fastRow = new HBox(16);
        fastRow.setAlignment(Pos.CENTER_LEFT);

        VBox fastInfoBox = new VBox(4);
        Label fastTitleLabel = new Label("快速模型");
        fastTitleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");
        Label fastDescLabel = new Label("标题生成等轻量级任务，留空则回退到默认模型");
        fastDescLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");
        fastInfoBox.getChildren().addAll(fastTitleLabel, fastDescLabel);

        // 构建 fast model ComboBox（含 "无 (回退默认)" 选项）
        // "无" 使用空字符串 modelName，与 null 等效（getFastModel isBlank 检查）
        java.util.List<ModelItem> fastItems = new java.util.ArrayList<>();
        fastItems.add(new ModelItem("  无 (使用默认模型)", "", ""));
        fastItems.addAll(items.stream().filter(mi -> !mi.isHeader() && mi.modelName != null
            && !mi.modelName.isBlank()).toList());

        // 保留完整列表供搜索
        java.util.List<ModelItem> allFastItems = new java.util.ArrayList<>(fastItems);

        ComboBox<ModelItem> fastCombo = new ComboBox<>();
        fastCombo.getItems().addAll(fastItems);
        // 选中当前 fast model
        String fastMatch = (currentFast != null && !currentFast.isBlank()) ? currentFast : "";
        for (ModelItem mi : fastItems) {
            if (mi.modelName != null && mi.modelName.equals(fastMatch)) {
                fastCombo.setValue(mi);
                break;
            }
        }
        if (fastCombo.getValue() == null && !fastItems.isEmpty()) {
            fastCombo.setValue(fastItems.get(0));
        }
        fastCombo.setEditable(true);
        fastCombo.setStyle("-fx-background-color: rgba(0, 0, 0, 0.03); -fx-background-radius: 12px;"
            + " -fx-border-color: transparent; -fx-font-size: 13px;");
        fastCombo.setPrefHeight(40);
        fastCombo.setMaxWidth(350);

        // 自定义单元格渲染："无" 项特殊样式，提供商标题不可选
        fastCombo.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(ModelItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setDisable(false); return; }
                setText(item.text);
                if (item.isHeader()) {
                    setDisable(true);
                    setStyle("-fx-text-fill: rgba(0,0,0,0.4); -fx-font-weight: 700;"
                        + " -fx-font-size: 11px; -fx-font-family: sans-serif;");
                } else {
                    setDisable(false);
                    setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
                }
            }
        });

        fastCombo.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(ModelItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.modelName != null && !item.modelName.isEmpty() ? item.modelName : "无");
                setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
            }
        });

        // ---- 快速模型搜索过滤 Popup ----
        javafx.stage.Popup fastSearchPopup = new javafx.stage.Popup();
        fastSearchPopup.setAutoHide(true);
        VBox fastSearchList = new VBox(2);
        fastSearchList.setStyle("-fx-background-color: rgba(255,255,255,0.97); -fx-background-radius: 10px;"
            + " -fx-border-color: rgba(0,0,0,0.08); -fx-border-radius: 10px; -fx-border-width: 1px;");
        fastSearchList.setPadding(new Insets(4));
        fastSearchPopup.getContent().add(fastSearchList);

        fastCombo.getEditor().textProperty().addListener((obs, old, text) -> {
            fastSearchPopup.hide();
            if (text == null || text.isBlank()) return;
            String lower = text.toLowerCase().trim();
            if (lower.isEmpty()) return;

            java.util.List<ModelItem> filtered = new java.util.ArrayList<>();
            for (ModelItem mi : allFastItems) {
                if (mi.isHeader()) {
                    filtered.add(mi);
                } else if (mi.modelName != null && mi.modelName.toLowerCase().contains(lower)) {
                    filtered.add(mi);
                } else if (mi.modelName != null && mi.modelName.isEmpty() && "无".contains(lower)) {
                    // 搜索"无"时显示"使用默认模型"选项
                    filtered.add(mi);
                }
            }
            // Clean orphan headers
            java.util.List<ModelItem> clean = new java.util.ArrayList<>();
            for (int i = 0; i < filtered.size(); i++) {
                ModelItem mi = filtered.get(i);
                if (mi.isHeader()) {
                    if (i + 1 < filtered.size() && !filtered.get(i + 1).isHeader()) {
                        clean.add(mi);
                    }
                } else {
                    clean.add(mi);
                }
            }
            if (clean.isEmpty()) return;

            fastCombo.hide();

            fastSearchList.getChildren().clear();
            for (ModelItem mi : clean) {
                javafx.scene.control.Label row = new javafx.scene.control.Label(mi.text);
                row.setPadding(new Insets(4, 10, 4, 10));
                row.setPrefHeight(24);
                if (mi.isHeader()) {
                    row.setStyle("-fx-text-fill: rgba(0,0,0,0.4); -fx-font-weight: 700;"
                        + " -fx-font-size: 11px; -fx-font-family: sans-serif;");
                    row.setDisable(true);
                } else {
                    row.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;"
                        + " -fx-cursor: hand;");
                    row.setOnMouseClicked(ev -> {
                        fastSearchPopup.hide();
                        fastCombo.setValue(mi);
                        fastCombo.getEditor().setText(
                            mi.modelName != null && !mi.modelName.isEmpty() ? mi.modelName : "无");
                    });
                }
                fastSearchList.getChildren().add(row);
            }
            if (!fastSearchList.getChildren().isEmpty()) {
                var bounds = fastCombo.localToScreen(fastCombo.getBoundsInLocal());
                fastSearchPopup.show(fastCombo.getScene().getWindow(),
                    bounds.getMinX(), bounds.getMaxY() + 2);
            }
        });

        fastCombo.setOnShowing(e -> fastSearchPopup.hide());

        fastCombo.setOnAction(e -> {
            Object value = fastCombo.getValue();
            if (!(value instanceof ModelItem)) return;
            ModelItem sel = (ModelItem) value;
            if (sel.isHeader()) return;
            cfg.getAgents().getDefaults().setFastModel(
                sel.modelName != null && !sel.modelName.isEmpty() ? sel.modelName : null);
            try { config.ConfigIO.saveConfig(cfg, null); } catch (Exception ignored) {}
            refresh();
        });

        fastRow.getChildren().addAll(fastInfoBox, fastCombo);
        HBox.setHgrow(fastInfoBox, Priority.ALWAYS);

        // API Key 显示
        String apiKey = "";
        if (currentProvider != null) {
            config.provider.ProviderConfig pc = cfg.getProviders().getByName(currentProvider);
            if (pc != null && pc.getApiKey() != null && !pc.getApiKey().isBlank()) {
                apiKey = pc.getApiKey();
            }
        }
        String maskedKey = apiKey.length() > 4
            ? apiKey.substring(0, 4) + "\u2022\u2022\u2022\u2022" + apiKey.substring(apiKey.length() - 4)
            : (apiKey.isBlank() ? "未配置" : "\u2022\u2022\u2022");
        HBox apiKeyRow = createSettingRow("API 密钥", "用于认证模型服务", maskedKey);

        section.getChildren().addAll(sectionTitle, modelRow, fastRow, apiKeyRow);

        // 打开配置文件 — 内置 JSON 编辑器
        Button openConfigBtn = new Button("\uD83D\uDCC4 编辑配置文件");
        openConfigBtn.getStyleClass().add("pill-button");
        openConfigBtn.setPrefHeight(36);
        openConfigBtn.setOnAction(e -> showEditorChoiceDialog());
        HBox btnBox = new HBox(openConfigBtn);
        btnBox.setAlignment(Pos.CENTER);
        section.getChildren().add(btnBox);

        return section;
    }

    public void setOnModelChanged(Consumer<String> callback) {
        this.onModelChanged = callback;
    }

    private VBox buildChannelsSection() {
        config.channel.ChannelsConfig ch = backendBridge.getConfig().getChannels();
        VBox section = new VBox(16);
        Label sectionTitle = new Label("通道");
        sectionTitle.getStyleClass().add("section-title");
        VBox channelsBox = new VBox(12);

        addChannelRow(channelsBox, "\uD83D\uDCF1", "Telegram",
            ch.getTelegram().getToken() != null && !ch.getTelegram().getToken().isBlank());
        addChannelRow(channelsBox, "\uD83D\uDCAC", "飞书",
            ch.getFeishu().getAppId() != null && !ch.getFeishu().getAppId().isBlank());
        addChannelRow(channelsBox, "\uD83D\uDCE7", "Email",
            ch.getEmail().getSmtpHost() != null && !ch.getEmail().getSmtpHost().isBlank());

        section.getChildren().addAll(sectionTitle, channelsBox);
        return section;
    }

    private void addChannelRow(VBox box, String icon, String name, boolean configured) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));
        row.setStyle("-fx-background-color: rgba(0, 0, 0, 0.02); -fx-background-radius: 12px;");

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 20px;");
        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 500;");
        Label statusLabel = new Label(configured ? "已配置" : "未配置");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: rgba(0, 0, 0, 0.5);");
        infoBox.getChildren().addAll(nameLabel, statusLabel);
        row.getChildren().addAll(iconLabel, infoBox);
        HBox.setHgrow(infoBox, Priority.ALWAYS);
        box.getChildren().add(row);
    }

    /** 编辑器选择对话框：系统默认编辑器 or 内置编辑器 */
    private void showEditorChoiceDialog() {
        Path configPath = config.ConfigIO.getConfigPath();
        if (!Files.exists(configPath)) return;

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("选择编辑器");

        VBox root = new VBox(20);
        root.setPadding(new Insets(28));
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #ffffff;"
            + " -fx-border-color: rgba(0,0,0,0.1); -fx-border-radius: 12px; -fx-border-width: 1px;");

        Label title = new Label("\uD83D\uDCC4 选择编辑器");
        title.setStyle("-fx-font-family: Georgia; -fx-font-size: 20px; -fx-text-fill: rgba(0,0,0,0.7); -fx-font-weight: 600;");

        Label desc = new Label("请选择使用哪种编辑器打开配置文件");
        desc.setStyle("-fx-font-size: 14px; -fx-text-fill: rgba(0,0,0,0.45);");

        Button systemBtn = new Button("\uD83D\uDDA5 系统默认编辑器");
        systemBtn.setStyle("-fx-background-color: #f8f8f8; -fx-text-fill: #333;"
            + " -fx-background-radius: 10px; -fx-padding: 12px 24px;"
            + " -fx-font-size: 14px; -fx-font-weight: 500; -fx-cursor: hand;"
            + " -fx-border-color: rgba(0,0,0,0.1); -fx-border-radius: 10px; -fx-border-width: 1px;");
        systemBtn.setPrefWidth(240);
        systemBtn.setOnAction(e -> {
            dialog.close();
            try {
                java.awt.Desktop.getDesktop().edit(configPath.toFile());
            } catch (IOException ex) {
                // 如果 edit 不支持，回退到 open
                try {
                    java.awt.Desktop.getDesktop().open(configPath.toFile());
                } catch (IOException ex2) {
                    // 最终回退到内置编辑器
                    javafx.application.Platform.runLater(this::showJsonEditor);
                }
            }
        });

        Button builtinBtn = new Button("\uD83D\uDCDD 本项目内置编辑器");
        builtinBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white;"
            + " -fx-background-radius: 10px; -fx-padding: 12px 24px;"
            + " -fx-font-size: 14px; -fx-font-weight: 600; -fx-cursor: hand;");
        builtinBtn.setPrefWidth(240);
        builtinBtn.setOnAction(e -> {
            dialog.close();
            javafx.application.Platform.runLater(this::showJsonEditor);
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #888;"
            + " -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 6px 16px;");
        cancelBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(title, desc, systemBtn, builtinBtn, cancelBtn);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /** 内置 JSON 编辑对话框：语法高亮 + 格式化 + 保存 */
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
        root.setStyle("-fx-background-color: #ffffff;"
            + " -fx-border-color: rgba(0,0,0,0.1); -fx-border-radius: 12px; -fx-border-width: 1px;");
        root.setMinWidth(700);
        root.setMinHeight(500);

        Label title = new Label("📄 " + configPath.getFileName().toString());
        title.setStyle("-fx-font-size: 15px; -fx-text-fill: rgba(0,0,0,0.7); -fx-font-weight: 500;");

        TextArea editor = new TextArea(originalContent);
        editor.setStyle("-fx-font-family: 'JetBrains Mono','Fira Code',monospace;"
            + " -fx-font-size: 13px; -fx-text-fill: #333;"
            + " -fx-background-color: #fafafa; -fx-control-inner-background: #fafafa;"
            + " -fx-border-color: rgba(0,0,0,0.1); -fx-border-radius: 8px;");
        editor.setWrapText(false);
        VBox.setVgrow(editor, Priority.ALWAYS);

        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        Button formatBtn = new Button("格式化");
        formatBtn.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #555;"
            + " -fx-background-radius: 8px; -fx-padding: 6px 16px; -fx-cursor: hand;"
            + " -fx-border-color: rgba(0,0,0,0.1); -fx-border-radius: 8px; -fx-border-width: 1px;");
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
        saveBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white;"
            + " -fx-background-radius: 8px; -fx-padding: 6px 20px; -fx-font-weight: 600; -fx-cursor: hand;");
        saveBtn.setOnAction(e -> {
            try {
                new ObjectMapper().readValue(editor.getText(), Object.class);
                Files.writeString(configPath, editor.getText());
                backendBridge.reloadConfigFromDisk();
                refresh();
                dialog.close();
            } catch (Exception ex) {
                editor.setStyle(editor.getStyle().replace("-fx-control-inner-background: #fafafa;",
                    "-fx-control-inner-background: #fff0f0;"));
            }
        });

        Button cancelBtn = new Button("取消");
        cancelBtn.setStyle("-fx-background-color: #f0f0f0; -fx-text-fill: #888;"
            + " -fx-background-radius: 8px; -fx-padding: 6px 16px; -fx-cursor: hand;"
            + " -fx-border-color: rgba(0,0,0,0.1); -fx-border-radius: 8px; -fx-border-width: 1px;");
        cancelBtn.setOnAction(e -> dialog.close());

        btnRow.getChildren().addAll(formatBtn, cancelBtn, saveBtn);

        root.getChildren().addAll(title, editor, btnRow);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}
