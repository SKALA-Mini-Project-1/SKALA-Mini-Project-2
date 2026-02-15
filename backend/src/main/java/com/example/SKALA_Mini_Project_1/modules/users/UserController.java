package com.example.SKALA_Mini_Project_1.modules.users;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.SKALA_Mini_Project_1.common.ErrorResponse;
import com.example.SKALA_Mini_Project_1.modules.users.dto.EmailVerificationCodeRequest;
import com.example.SKALA_Mini_Project_1.modules.users.dto.EmailVerificationRequest;
import com.example.SKALA_Mini_Project_1.modules.users.dto.EmailVerificationResponse;
import com.example.SKALA_Mini_Project_1.modules.users.dto.LoginRequest;
import com.example.SKALA_Mini_Project_1.modules.users.dto.LoginResponse;
import com.example.SKALA_Mini_Project_1.modules.users.dto.SignUpRequest;
import com.example.SKALA_Mini_Project_1.modules.users.dto.SignUpResponse;
import com.example.SKALA_Mini_Project_1.modules.users.service.EmailVerificationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "사용자", description = "사용자 인증 및 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    
    private final UserService userService;
    private final UserRepository userRepository;
    private final EmailVerificationService emailVerificationService;

    
    @Operation(summary = "이메일 인증 코드 발송", description = "회원가입을 위한 이메일 인증 코드를 발송합니다")
    @PostMapping("/email/send")
    public ResponseEntity<?> sendVerificationCode(@Valid @RequestBody EmailVerificationRequest request) {
        try {
            EmailVerificationResponse response = emailVerificationService.sendVerificationCode(request.getEmail());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "이메일 발송 중 오류가 발생했습니다"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    @Operation(summary = "이메일 인증 코드 검증", description = "발송된 인증 코드를 검증합니다")
    @PostMapping("/email/verify")
    public ResponseEntity<?> verifyCode(@Valid @RequestBody EmailVerificationCodeRequest request) {
        try {
            EmailVerificationResponse response = emailVerificationService.verifyCode(
                request.getEmail(), 
                request.getCode()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "인증 처리 중 오류가 발생했습니다"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@Valid @RequestBody SignUpRequest request) {
        try {
            SignUpResponse response = userService.signUp(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }

    // 로그아웃 API (Swagger 호환)
    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @Parameter(hidden = true) 
            @RequestHeader("Authorization") String authHeader  // ✨ 단순하게!
    ) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                ErrorResponse error = ErrorResponse.of(
                    HttpStatus.BAD_REQUEST.value(), 
                    "Authorization 헤더 형식이 올바르지 않습니다"
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
            }
            
            String token = authHeader.substring(7);
            userService.logout(token);
            
            return ResponseEntity.ok(Map.of(
                "message", "로그아웃 성공",
                "status", "success"
            ));
            
        } catch (IllegalArgumentException e) {
            ErrorResponse error = ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), 
                "로그아웃 처리 중 오류가 발생했습니다"
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    // JWT 인증 테스트용 엔드포인트
    @Operation(summary = "인증된 사용자 정보 조회", description = "현재 인증된 사용자의 정보를 조회합니다")
    @GetMapping("/me")
    public ResponseEntity<?> getMyInfo() {
        // 현재 로그인한 사용자 ID 가져오기
        Long userId = (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
        
        return ResponseEntity.ok(Map.of(
            "userId", user.getId(),
            "email", user.getEmail(),
            "name", user.getName(),
            "message", "인증된 사용자 정보 조회 성공"
        ));
    }

    
    // Validation 예외 처리 추가
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

}