package com.techeer.carpool.global.init;

import com.techeer.carpool.domain.member.entity.Member;
import com.techeer.carpool.domain.member.repository.MemberRepository;
import com.techeer.carpool.domain.vehicle.entity.VehicleOption;
import com.techeer.carpool.domain.vehicle.entity.VehicleOptionType;
import com.techeer.carpool.domain.vehicle.repository.VehicleOptionRepository;
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
    private final VehicleOptionRepository vehicleOptionRepository;

    @Override
    public void run(String... args) {
        createIfNotExists("test@carpool.com", "password1234", "테스트유저");
        createIfNotExists("admin@carpool.com", "admin1234!", "관리자");
        log.info("[LocalDataInitializer] 테스트 계정 생성 완료");

        initVehicleOptions();
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

    private void initVehicleOptions() {
        if (vehicleOptionRepository.existsByType(VehicleOptionType.MODEL)) return;

        // TODO: 실제 차량 모델 데이터로 교체 필요
        vehicleOptionRepository.saveAll(List.of(
                VehicleOption.builder().type(VehicleOptionType.MODEL).brand("현대").name("아반떼").build(),
                VehicleOption.builder().type(VehicleOptionType.MODEL).brand("현대").name("소나타").build(),
                VehicleOption.builder().type(VehicleOptionType.MODEL).brand("현대").name("그랜저").build(),
                VehicleOption.builder().type(VehicleOptionType.MODEL).brand("기아").name("K5").build(),
                VehicleOption.builder().type(VehicleOptionType.MODEL).brand("기아").name("스포티지").build(),
                VehicleOption.builder().type(VehicleOptionType.MODEL).brand("기아").name("카니발").build(),
                VehicleOption.builder().type(VehicleOptionType.MODEL).brand("BMW").name("3시리즈").build(),
                VehicleOption.builder().type(VehicleOptionType.MODEL).brand("벤츠").name("E클래스").build(),
                // TODO: 실제 차량 색상 데이터로 교체 필요
                VehicleOption.builder().type(VehicleOptionType.COLOR).name("흰색").hexCode("#FFFFFF").build(),
                VehicleOption.builder().type(VehicleOptionType.COLOR).name("검정").hexCode("#000000").build(),
                VehicleOption.builder().type(VehicleOptionType.COLOR).name("회색").hexCode("#808080").build(),
                VehicleOption.builder().type(VehicleOptionType.COLOR).name("은색").hexCode("#C0C0C0").build(),
                VehicleOption.builder().type(VehicleOptionType.COLOR).name("빨강").hexCode("#FF0000").build(),
                VehicleOption.builder().type(VehicleOptionType.COLOR).name("파랑").hexCode("#0000FF").build(),
                VehicleOption.builder().type(VehicleOptionType.COLOR).name("갈색").hexCode("#8B4513").build()
        ));
    }
}
