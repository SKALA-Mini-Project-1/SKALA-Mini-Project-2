package com.example.SKALA_Mini_Project_1.modules.users;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // 이메일로 사용자 찾기 (로그인에 필요)
    Optional<User> findByEmail(String email);
    
    // 이메일 중복 확인 (회원가입에 필요)
    boolean existsByEmail(String email);
}