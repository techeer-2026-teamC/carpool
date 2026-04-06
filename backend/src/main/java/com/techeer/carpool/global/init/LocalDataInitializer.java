package com.techeer.carpool.global.init;

import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class LocalDataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        createIfNotExists("test@carpool.com", "password1234", "테스트유저");
        createIfNotExists("admin@carpool.com", "admin1234!", "관리자");
        log.info("[LocalDataInitializer] 테스트 계정 생성 완료");
    }

    private void createIfNotExists(String email, String password, String nickname) {
        if (!memberRepository.existsByEmail(email)) {
            memberRepository.save(Member.builder()
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .nickname(nickname)
                    .build());
        }
    }
}
