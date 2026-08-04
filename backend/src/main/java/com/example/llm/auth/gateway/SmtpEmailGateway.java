package com.example.llm.auth.gateway;

import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.security.AuthCrypto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class SmtpEmailGateway implements EmailGateway {
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final AuthProperties properties;
    private final AuthCrypto crypto;
    private final Environment environment;

    public SmtpEmailGateway(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            AuthProperties properties,
            AuthCrypto crypto,
            Environment environment) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
        this.crypto = crypto;
        this.environment = environment;
    }

    @Override
    public String sendRegistrationCode(String recipient, String code, Instant expiresAt) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String from = properties.getMail().getFrom();
        String host = environment.getProperty("spring.mail.host", "");
        if (sender == null || from == null || from.isBlank() || host.isBlank()) {
            throw unavailable();
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("ExamInsight 邮箱验证码");
        message.setText("你的 ExamInsight 验证码是：" + code
                + "\n\n验证码将在 " + properties.getVerification().getCodeTtl().toMinutes()
                + " 分钟后失效，请勿转发给他人。"
                + "\n如果不是你本人操作，请忽略此邮件。");
        try {
            sender.send(message);
            return "smtp-" + crypto.newExternalId();
        } catch (MailException exception) {
            throw unavailable();
        }
    }

    private AuthApiException unavailable() {
        return new AuthApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "EMAIL_DELIVERY_UNAVAILABLE", "验证邮件暂时无法发送，请稍后重试。");
    }
}
