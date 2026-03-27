package agent;

public interface ProgressCallback {
    void onProgress(String content, boolean toolHint);
}