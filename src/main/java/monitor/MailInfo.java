package monitor;

import java.time.LocalDateTime;

/**
 * 邮件信息载体
 */
public class MailInfo {
    private String messageId;
    private String from;
    private String subject;
    private String body;
    private LocalDateTime date;

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }
}