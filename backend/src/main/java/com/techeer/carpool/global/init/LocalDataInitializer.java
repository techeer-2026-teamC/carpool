package com.techeer.carpool.global.init;

import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.vehicle.entity.CarColor;
import com.techeer.carpool.domain.vehicle.entity.CarModel;
import com.techeer.carpool.domain.vehicle.repository.CarColorRepository;
import com.techeer.carpool.domain.vehicle.repository.CarModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Profile("local")
@Component
@RequiredArgsConstructor
public class LocalDataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final CarModelRepository carModelRepository;
    private final CarColorRepository carColorRepository;

    @Override
    public void run(String... args) {
        createIfNotExists("test@carpool.com", "password1234", "테스트유저");
        createIfNotExists("admin@carpool.com", "admin1234!", "관리자");
        log.info("[LocalDataInitializer] 테스트 계정 생성 완료");

        initCarModels();
        initCarColors();
        log.info("[LocalDataInitializer] 차량 마스터 데이터 초기화 완료");
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

    // TODO: 실제 차량 모델 데이터로 교체 필요
    private void initCarModels() {
        if (carModelRepository.count() > 0) return;
        carModelRepository.saveAll(List.of(
                CarModel.builder().brand("현대").name("아반떼").build(),
                CarModel.builder().brand("현대").name("소나타").build(),
                CarModel.builder().brand("현대").name("그랜저").build(),
                CarModel.builder().brand("기아").name("K5").build(),
                CarModel.builder().brand("기아").name("스포티지").build(),
                CarModel.builder().brand("기아").name("카니발").build(),
                CarModel.builder().brand("BMW").name("3시리즈").build(),
                CarModel.builder().brand("벤츠").name("E클래스").build()
        ));
    }

    // TODO: 실제 차량 색상 데이터로 교체 필요
    private void initCarColors() {
        if (carColorRepository.count() > 0) return;
        carColorRepository.saveAll(List.of(
                CarColor.builder().name("흰색").hexCode("#FFFFFF").build(),
                CarColor.builder().name("검정").hexCode("#000000").build(),
                CarColor.builder().name("회색").hexCode("#808080").build(),
                CarColor.builder().name("은색").hexCode("#C0C0C0").build(),
                CarColor.builder().name("빨강").hexCode("#FF0000").build(),
                CarColor.builder().name("파랑").hexCode("#0000FF").build(),
                CarColor.builder().name("갈색").hexCode("#8B4513").build()
        ));
    }
}
