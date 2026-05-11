package gui.ui.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 服务端 /agent/releases/latest/info 的 JSON 响应模型
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateInfo {

    private String version;
    private String url;
    private long size;
    private String changelog;

    public UpdateInfo() {}

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getChangelog() { return changelog; }
    public void setChangelog(String changelog) { this.changelog = changelog; }

    @Override
    public String toString() {
        return "UpdateInfo{version='" + version + "', url='" + url
            + "', size=" + size + ", changelog='" + changelog + "'}";
    }
}
