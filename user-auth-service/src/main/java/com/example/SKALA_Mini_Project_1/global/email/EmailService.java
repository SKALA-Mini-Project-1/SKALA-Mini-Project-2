package com.example.SKALA_Mini_Project_1.global.email;

import java.io.UnsupportedEncodingException;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    
    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;
    
    /**
     * 이메일 인증 코드 발송
     * @param toEmail 수신자 이메일
     * @param verificationCode 인증 코드
     * @throws RuntimeException 이메일 발송 실패 시
     */
    public void sendVerificationCode(String toEmail, String verificationCode) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            // 발신자 정보
            helper.setFrom(emailProperties.getFromAddress(), emailProperties.getFromName());
            
            // 수신자 정보
            helper.setTo(toEmail);
            
            // 제목
            helper.setSubject("[FAIRLINE TICKET] 이메일 인증 코드");
            
            // 본문 (HTML)
            String htmlContent = buildVerificationEmailHtml(verificationCode);
            helper.setText(htmlContent, true);
            
            // 발송
            mailSender.send(message);
            
            log.info("인증 코드 이메일 발송 성공 - 수신자: {}", toEmail);
            
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("이메일 생성 실패 - 수신자: {}", toEmail, e);
            throw new RuntimeException("이메일 생성에 실패했습니다", e);
        } catch (MailException e) {
            log.error("이메일 발송 실패 - 수신자: {}", toEmail, e);
            throw new RuntimeException("이메일 발송에 실패했습니다", e);
        }
    }
    
    /**
     * 인증 이메일 HTML 템플릿 생성
     * @param code 인증 코드
     * @return HTML 내용
     */
    private String buildVerificationEmailHtml(String code) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: 'Arial', sans-serif;
                        line-height: 1.6;
                        color: #333;
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .container {
                        background-color: #f9f9f9;
                        border-radius: 10px;
                        padding: 30px;
                        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 30px;
                    }
                    .header h1 {
                        color: #4A90E2;
                        margin: 0;
                    }
                    .content {
                        background-color: white;
                        padding: 25px;
                        border-radius: 8px;
                    }
                    .code-box {
                        background-color: #4A90E2;
                        color: white;
                        font-size: 32px;
                        font-weight: bold;
                        text-align: center;
                        padding: 20px;
                        border-radius: 8px;
                        letter-spacing: 8px;
                        margin: 20px 0;
                    }
                    .info {
                        color: #666;
                        font-size: 14px;
                        margin-top: 20px;
                        padding-top: 20px;
                        border-top: 1px solid #eee;
                    }
                    .footer {
                        text-align: center;
                        margin-top: 30px;
                        color: #999;
                        font-size: 12px;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎫 FAIRLINE TICKET</h1>
                        <p>이메일 인증</p>
                    </div>
                    
                    <div class="content">
                        <p>안녕하세요!</p>
                        <p>FAIRLINE TICKET 회원가입을 위한 이메일 인증 코드입니다.</p>
                        <p>아래의 인증 코드를 입력해주세요.</p>
                        
                        <div class="code-box">
                            %s
                        </div>
                        
                        <div class="info">
                            <p>⏰ 이 인증 코드는 <strong>5분간</strong> 유효합니다.</p>
                            <p>⚠️ 본인이 요청하지 않았다면 이 이메일을 무시해주세요.</p>
                        </div>
                    </div>
                    
                    <div class="footer">
                        <p>© 2025 FAIRLINE TICKET. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(code);
    }
}