package gui.ui.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务端 /agent/releases/latest/info 的 JSON 响应模型。
 * <p>
 * 支持两种 JSON 格式：
 * <pre>{@code
 * // 旧格式（顶层 url）
 * { "version": "1.0", "url": "http://...", "size": 123 }
 *
 * // 新格式（url 嵌套在 jar 对象中）
 * { "version": "2.3", "jar": { "url": "http://...", "size": 123 }, "size": 123 }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateInfo {

    private String version;
    private String url;
    private long size;
    private String changelog;

    /**
     * JAR 文件信息（服务端新格式 nest 在 jar 对象中）。
     */
    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JarInfo {
        private String url;
        private long size;
    }

    private JarInfo jar;

    public UpdateInfo() {}

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    /**
     * 获取下载 URL。
     * 优先使用 jar.url（新格式），回退到顶层 url（旧格式兼容）。
     */
    public String getUrl() {
        if (jar != null && jar.getUrl() != null && !jar.getUrl().isBlank()) {
            return jar.getUrl();
        }
        return url;
    }

    public void setUrl(String url) { this.url = url; }

    /**
     * 获取文件大小。
     * 优先使用 jar.size（新格式），回退到顶层 size（旧格式兼容）。
     */
    public long getSize() {
        if (jar != null && jar.getSize() > 0) {
            return jar.getSize();
        }
        return size;
    }

    public void setSize(long size) { this.size = size; }

    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }

    public JarInfo getJar() { return jar; }
    public void setJar(JarInfo jar) { this.jar = jar; }

    @Override
    public String toString() {
        return "UpdateInfo{version='" + version + "', url='" + getUrl()
            + "', size=" + getSize() + ", changelog='" + changelog + "'}";
    }
}
