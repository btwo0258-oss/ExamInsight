package com.example.llm.auth.gateway;

import com.example.llm.auth.api.AuthApiException;
import com.example.llm.auth.config.AuthProperties;
import com.example.llm.auth.security.AuthCrypto;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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
    public String sendVerificationCode(
            String recipient,
            String code,
            Instant expiresAt,
            VerificationPurpose purpose) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        String from = properties.getMail().getFrom();
        String host = environment.getProperty("spring.mail.host", "");
        if (sender == null || from == null || from.isBlank() || host.isBlank()) {
            throw unavailable();
        }

        String fromName = properties.getMail().getFromName();
        try {
            sender.send(message -> {
                MimeMessageHelper helper = new MimeMessageHelper(
                        message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(from, fromName);
                helper.setTo(recipient);
                String action = purpose == VerificationPurpose.PASSWORD_RESET ? "重置密码" : "注册账户";
                String subject = purpose == VerificationPurpose.PASSWORD_RESET
                        ? "ExamInsight 密码重置验证码"
                        : "ExamInsight 邮箱验证码";
                helper.setSubject(subject);
                helper.setText("你正在为 ExamInsight " + action + "，验证码是：" + code
                        + "\n\n验证码将在 " + properties.getVerification().getCodeTtl().toMinutes()
                        + " 分钟后失效，请勿转发给他人。"
                        + "\n如果不是你本人操作，请忽略此邮件。", false);
            });
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
