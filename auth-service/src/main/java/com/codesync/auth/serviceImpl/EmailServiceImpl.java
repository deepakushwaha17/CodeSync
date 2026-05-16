package com.codesync.auth.serviceImpl;

import com.codesync.auth.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendOtpEmail(
            String toEmail,
            String otp,
            String purpose) {

        try {
            MimeMessage message =
                    mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(
                            message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setFrom(fromEmail);

            String subject = purpose.equals("SIGNUP")
                    ? "CodeSync — Verify your email"
                    : "CodeSync — Login verification";

            helper.setSubject(subject);
            helper.setText(
                    buildEmailHtml(otp, purpose),
                    true // isHtml
            );

            mailSender.send(message);
            log.info("OTP email sent to: {}", toEmail);

        } catch (Exception e) {
            log.error(
                    "Failed to send OTP email to {}: {}",
                    toEmail, e.getMessage());
            throw new RuntimeException(
                    "Failed to send email: "
                            + e.getMessage());
        }
    }

    private String buildEmailHtml(
            String otp, String purpose) {

        String action = purpose.equals("SIGNUP")
                ? "verify your email address"
                : "complete your login";

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8"/>
            </head>
            <body style="margin:0;padding:0;
                         background:#0d1117;
                         font-family:Inter,sans-serif;">
              <div style="max-width:480px;
                          margin:40px auto;
                          background:#161b22;
                          border:1px solid #30363d;
                          border-radius:12px;
                          padding:40px;">

                <!-- Logo -->
                <div style="text-align:center;
                            margin-bottom:32px;">
                  <div style="display:inline-block;
                              width:48px;height:48px;
                              background:linear-gradient(
                                135deg,#58a6ff,#bc8cff);
                              border-radius:12px;
                              font-size:24px;
                              line-height:48px;
                              text-align:center;">
                    ⚡
                  </div>
                  <div style="color:#e6edf3;
                              font-size:20px;
                              font-weight:700;
                              margin-top:8px;">
                    CodeSync
                  </div>
                </div>

                <!-- Title -->
                <h2 style="color:#e6edf3;
                           font-size:20px;
                           margin:0 0 12px;
                           text-align:center;">
                  Your verification code
                </h2>

                <p style="color:#8b949e;
                          font-size:14px;
                          text-align:center;
                          margin:0 0 32px;">
                  Use the code below to %s.
                  This code expires in
                  <strong style="color:#e6edf3;">
                    5 minutes</strong>.
                </p>

                <!-- OTP Box -->
                <div style="background:#0d1117;
                            border:2px solid #58a6ff;
                            border-radius:10px;
                            padding:24px;
                            text-align:center;
                            margin-bottom:32px;">
                  <div style="font-size:42px;
                              font-weight:700;
                              color:#58a6ff;
                              letter-spacing:12px;
                              font-family:
                                'JetBrains Mono',
                                monospace;">
                    %s
                  </div>
                </div>

                <p style="color:#484f58;
                          font-size:12px;
                          text-align:center;
                          margin:0;">
                  If you didn't request this,
                  you can safely ignore this email.
                  <br/>
                  Do not share this code with anyone.
                </p>
              </div>
            </body>
            </html>
            """.formatted(action, otp);
    }
}