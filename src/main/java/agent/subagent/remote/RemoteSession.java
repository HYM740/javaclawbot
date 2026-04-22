package agent.subagent.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 远程会话
 *
 * 对应 Open-ClaudeCode: src/tools/shared/spawnMultiAgent.ts - RemoteSession
 */
public class RemoteSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RemoteSession.class);

    private final String sessionId;
    private final String endpoint;
    private final String ccrEndpoint;
    private final String apiKey;
    private final BlockingQueue<String> outputQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile long lastActiveTime;
    private final long timeoutMs;

    public RemoteSession(String sessionId, String endpoint, String ccrEndpoint, String apiKey, long timeoutMs) {
        this.sessionId = sessionId;
        this.endpoint = endpoint;
        this.ccrEndpoint = ccrEndpoint;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.lastActiveTime = System.currentTimeMillis();
    }

    public String getSessionId() { return sessionId; }
    public String getEndpoint() { return endpoint; }
    public boolean isClosed() { return closed.get(); }

    void appendOutput(String output) {
        if (output != null && !output.isEmpty()) {
            outputQueue.offer(output);
            lastActiveTime = System.currentTimeMillis();
        }
    }

    public String pollOutput(long timeout, TimeUnit unit) {
        try {
            String output = outputQueue.poll(timeout, unit);
            if (output != null) {
                lastActiveTime = System.currentTimeMillis();
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public String pollOutput() {
        return pollOutput(100, TimeUnit.MILLISECONDS);
    }

    public List<String> drainOutput() {
        List<String> outputs = new ArrayList<>();
        String output;
        while ((output = outputQueue.poll()) != null) {
            outputs.add(output);
        }
        if (!outputs.isEmpty()) {
            lastActiveTime = System.currentTimeMillis();
        }
        return outputs;
    }

    public String getAndClearOutput() {
        List<String> outputs = drainOutput();
        return String.join("\n", outputs);
    }

    public boolean isTimedOut() {
        if (timeoutMs <= 0) return false;
        return System.currentTimeMillis() - lastActiveTime > timeoutMs;
    }

    public long getLastActiveTime() { return lastActiveTime; }
    public int getPendingOutputCount() { return outputQueue.size(); }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("Closed remote session: {}", sessionId);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
