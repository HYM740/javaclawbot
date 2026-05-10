package monitor;

import bus.MessageBus;
import bus.OutboundMessage;
import config.Config;
import config.channel.EmailMonitorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import providers.LLMProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 邮件监控服务主类
 * 协调轮询、分析、通知分发
 */
public class EmailMonitorService {
    
    private static final Logger log = LoggerFactory.getLogger(EmailMonitorService.class);
    
    private final EmailMonitorConfig config;
    private final MessageBus bus;
    private final EmailPoller poller;
    private final MailAnalyzer analyzer;
    private final NotificationDispatcher dispatcher;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> pollTask;

    public EmailMonitorService(Config appConfig, LLMProvider provider, MessageBus bus) {
        this.config = appConfig.getChannels().getEmailMonitor();
        this.bus = bus;
        
        this.poller = new EmailPoller(config);
        this.analyzer = new MailAnalyzer(provider, config);
        this.dispatcher = new NotificationDispatcher(config, this::sendMessage);
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "email-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动监控服务
     */
    public void start() {
        if (!config.isEnabled()) {
            log.info("Email monitor is disabled");
            return;
        }
        
        if (running.compareAndSet(false, true)) {
            log.info("Starting email monitor service...");
            
            poller.connect()
                .thenRun(() -> {
                    int interval = config.getPollIntervalSeconds();
                    pollTask = scheduler.scheduleAtFixedRate(
                        this::pollAndProcess,
                        0,
                        interval,
                        TimeUnit.SECONDS
                    );
                    log.info("Email monitor started, polling every {} seconds", interval);
                })
                .exceptionally(ex -> {
                    log.error("Failed to start email monitor: {}", ex.getMessage());
                    running.set(false);
                    return null;
                });
        }
    }

    /**
     * 停止监控服务
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping email monitor service...");
            
            if (pollTask != null) {
                pollTask.cancel(false);
            }
            
            poller.disconnect()
                .thenRun(() -> {
                    scheduler.shutdown();
                    log.info("Email monitor stopped");
                });
        }
    }

    /**
     * 查询历史邮件（供工具调用）
     */
    public void queryHistory(LocalDate startDate, LocalDate endDate, String keywords) {
        if (!running.get()) {
            log.warn("Email monitor is not running");
            return;
        }
        
        int maxResults = config.getHistoryQuery().getMaxResults();
        List<MailInfo> mails = poller.fetchHistory(startDate, endDate, keywords, maxResults);
        
        log.info("Processing {} history mails", mails.size());
        
        for (MailInfo mail : mails) {
            processMail(mail);
        }
    }

    private void pollAndProcess() {
        try {
            List<MailInfo> newMails = poller.fetchNewMails();
            log.debug("Poll found {} new mails", newMails.size());
            
            for (MailInfo mail : newMails) {
                processMail(mail);
            }
        } catch (Exception e) {
            log.error("Error during poll: {}", e.getMessage());
        }
    }

    private void processMail(MailInfo mail) {
        log.info("Processing mail: {} from {}", mail.getSubject(), mail.getFrom());
        
        analyzer.analyze(mail)
            .thenCompose(result -> {
                log.debug("Analysis result: shouldNotify={}, reason={}", 
                    result.isShouldNotify(), result.getReason());
                return dispatcher.dispatch(result);
            })
            .exceptionally(ex -> {
                log.error("Failed to process mail {}: {}", mail.getMessageId(), ex.getMessage());
                return null;
            });
    }

    private CompletableFuture<Void> sendMessage(OutboundMessage message) {
        log.info("Sending message to {}: {}", message.getChannel(), message.getChatId());
        
        // 通过 MessageBus 发送消息
        bus.publishOutbound(message);
        
        return CompletableFuture.completedFuture(null);
    }

    public boolean isRunning() {
        return running.get();
    }
}