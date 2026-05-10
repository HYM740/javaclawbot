package config.channel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeishuConfig {
    private boolean enabled = false;
    private String appId = "";
    private String appSecret = "";
    private String encryptKey = "";
    private String verificationToken = "";
    private List<String> allowFrom = new ArrayList<>();

    // ✅ Python: react_emoji: str = "THUMBSUP"
    private String reactEmoji = "THUMBSUP";

    // 用户账户信息存储
    private Map<String, FeishuAccount> accounts = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }

    public String getVerificationToken() {
        return verificationToken;
    }

    public void setVerificationToken(String verificationToken) {
        this.verificationToken = verificationToken;
    }

    public List<String> getAllowFrom() {
        return allowFrom;
    }

    public void setAllowFrom(List<String> allowFrom) {
        this.allowFrom = (allowFrom != null) ? allowFrom : new ArrayList<>();
    }

    public String getReactEmoji() {
        return reactEmoji;
    }

    public void setReactEmoji(String reactEmoji) {
        this.reactEmoji = reactEmoji;
    }

    public Map<String, FeishuAccount> getAccounts() {
        return accounts;
    }

    public void setAccounts(Map<String, FeishuAccount> accounts) {
        this.accounts = accounts != null ? accounts : new HashMap<>();
    }
}