package gui.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.nio.file.Path;

/**
 * 右下角项目状态徽标。
 * 普通用户：显示工作空间路径，点击复制/打开文件夹。
 * 开发者：显示主项目名 + 数量徽标 + 设置图标，点击弹出 ProjectPopover。
 * <p>
 * 开发者模式下始终显示项目绑定界面，即使注册表中尚无项目绑定。
 */
public class ProjectStatusBadge extends HBox {

    private enum Mode { WORKSPACE, PROJECT }

    private static final int MAX_PATH_DISPLAY_LENGTH = 40;

    private final Label textLabel;
    private final Label badgeLabel;   // "+N" 数量徽标
    private final Label settingsIcon; // 齿轮图标

    private Runnable onClickHandler;
    private Mode currentMode = Mode.WORKSPACE;
    private boolean developerMode = false;

    public ProjectStatusBadge() {
        setAlignment(Pos.CENTER_RIGHT);
        setSpacing(4);
        setPadding(new Insets(0, 0, 0, 0));
        setStyle("-fx-cursor: hand;");

        textLabel = new Label();
        textLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #000000;");

        badgeLabel = new Label();
        badgeLabel.setStyle(
            "-fx-font-size: 10px; -fx-text-fill: #000000;" +
            "-fx-background-color: rgba(0,0,0,0.06); -fx-background-radius: 8px;" +
            "-fx-padding: 0 5px;");
        badgeLabel.setVisible(false);
        badgeLabel.setManaged(false);

        settingsIcon = new Label("\u2699");
        settingsIcon.setStyle("-fx-font-size: 12px; -fx-text-fill: #000000;");
        settingsIcon.setVisible(false);
        settingsIcon.setManaged(false);

        getChildren().addAll(textLabel, badgeLabel, settingsIcon);

        setOnMouseEntered(e -> setStyle(
            "-fx-background-color: rgba(0,0,0,0.06); -fx-background-radius: 6px;" +
            "-fx-padding: 2px 8px; -fx-cursor: hand;"));
        setOnMouseExited(e -> setStyle(
            "-fx-background-color: rgba(0,0,0,0.03); -fx-background-radius: 6px;" +
            "-fx-padding: 2px 8px; -fx-cursor: hand;"));
        setStyle("-fx-background-color: rgba(0,0,0,0.03); -fx-background-radius: 6px;" +
            "-fx-padding: 2px 8px; -fx-cursor: hand;");

        setOnMouseClicked(e -> {
            if (onClickHandler != null) {
                onClickHandler.run();
            }
        });
    }

    public void setOnClick(Runnable handler) {
        this.onClickHandler = handler;
    }

    /** 设置是否为开发者模式。开发者模式下始终显示项目绑定界面。 */
    public void setDeveloperMode(boolean devMode) {
        this.developerMode = devMode;
    }

    public boolean isDeveloperMode() {
        return developerMode;
    }

    /**
     * 刷新显示内容。
     * 开发者模式下：始终显示项目模式（有项目显示名称，无项目显示"绑定项目"提示）。
     * 普通模式下：有主项目显示项目模式，否则显示工作空间路径。
     */
    public void refresh(providers.cli.ProjectRegistry registry, Path workspacePath) {
        if (registry == null) return;

        String mainProjectName = null;
        for (var entry : registry.listAll().entrySet()) {
            if (entry.getValue().isMain()) {
                mainProjectName = entry.getKey();
                break;
            }
        }
        int totalCount = registry.size();

        if (developerMode) {
            // 开发者模式：始终显示项目绑定界面
            currentMode = Mode.PROJECT;
            if (mainProjectName != null && totalCount > 0) {
                textLabel.setText("\uD83D\uDCC1 " + mainProjectName + " \u2B50");
                if (totalCount > 1) {
                    badgeLabel.setText("+" + (totalCount - 1));
                    badgeLabel.setVisible(true);
                    badgeLabel.setManaged(true);
                } else {
                    badgeLabel.setVisible(false);
                    badgeLabel.setManaged(false);
                }
            } else {
                textLabel.setText("\uD83D\uDCC1 绑定项目");
                badgeLabel.setVisible(false);
                badgeLabel.setManaged(false);
            }
            settingsIcon.setVisible(true);
            settingsIcon.setManaged(true);
        } else if (mainProjectName != null && totalCount > 0) {
            currentMode = Mode.PROJECT;
            textLabel.setText("\uD83D\uDCC1 " + mainProjectName + " \u2B50");
            if (totalCount > 1) {
                badgeLabel.setText("+" + (totalCount - 1));
                badgeLabel.setVisible(true);
                badgeLabel.setManaged(true);
            } else {
                badgeLabel.setVisible(false);
                badgeLabel.setManaged(false);
            }
            settingsIcon.setVisible(true);
            settingsIcon.setManaged(true);
        } else {
            currentMode = Mode.WORKSPACE;
            textLabel.setText("\uD83D\uDCC2 " + shortenPath(workspacePath));
            badgeLabel.setVisible(false);
            badgeLabel.setManaged(false);
            settingsIcon.setVisible(false);
            settingsIcon.setManaged(false);
        }
    }

    public String getCurrentMode() {
        return currentMode.name().toLowerCase();
    }

    private static String shortenPath(Path path) {
        if (path == null) return "";
        String s = path.toString();
        String home = System.getProperty("user.home");
        if (home != null && s.startsWith(home)) {
            s = "~" + s.substring(home.length());
        }
        if (s.length() > MAX_PATH_DISPLAY_LENGTH) {
            s = "..." + s.substring(s.length() - (MAX_PATH_DISPLAY_LENGTH - 3));
        }
        return s;
    }
}