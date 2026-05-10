package config.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailMonitorConfig {
    private boolean enabled = false;
    private int pollIntervalSeconds = 60;

    // IMAP 配置
    private String imapHost;
    private int imapPort = 993;
    private String imapUsername;
    private String imapPassword;
    private boolean imapUseSsl = true;
    private String imapMailbox = "INBOX";

    // 自然语言任务描述
    private String taskDescription;

    // 通知目标列表
    private List<NotificationTarget> notificationTargets = new ArrayList<>();

    // 历史查询配置
    private HistoryQueryConfig historyQuery = new HistoryQueryConfig();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }

    public void setPollIntervalSeconds(int pollIntervalSeconds) {
        this.pollIntervalSeconds = pollIntervalSeconds;
    }

    public String getImapHost() {
        return imapHost;
    }

    public void setImapHost(String imapHost) {
        this.imapHost = imapHost;
    }

    public int getImapPort() {
        return imapPort;
    }

    public void setImapPort(int imapPort) {
        this.imapPort = imapPort;
    }

    public String getImapUsername() {
        return imapUsername;
    }

    public void setImapUsername(String imapUsername) {
        this.imapUsername = imapUsername;
    }

    public String getImapPassword() {
        return imapPassword;
    }

    public void setImapPassword(String imapPassword) {
        this.imapPassword = imapPassword;
    }

    public boolean isImapUseSsl() {
        return imapUseSsl;
    }

    public void setImapUseSsl(boolean imapUseSsl) {
        this.imapUseSsl = imapUseSsl;
    }

    public String getImapMailbox() {
        return imapMailbox;
    }

    public void setImapMailbox(String imapMailbox) {
        this.imapMailbox = imapMailbox;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }

    public List<NotificationTarget> getNotificationTargets() {
        return notificationTargets;
    }

    public void setNotificationTargets(List<NotificationTarget> notificationTargets) {
        this.notificationTargets = notificationTargets != null ? notificationTargets : new ArrayList<>();
    }

    public HistoryQueryConfig getHistoryQuery() {
        return historyQuery;
    }

    public void setHistoryQuery(HistoryQueryConfig historyQuery) {
        this.historyQuery = historyQuery != null ? historyQuery : new HistoryQueryConfig();
    }
}