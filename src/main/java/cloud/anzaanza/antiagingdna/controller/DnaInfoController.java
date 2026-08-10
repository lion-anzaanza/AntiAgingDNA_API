package cloud.anzaanza.antiagingdna.controller;

import cloud.anzaanza.antiagingdna.dto.DnaInfoResponse;
import cloud.anzaanza.antiagingdna.service.DnaInfoService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내 LifeDNA — 초기 진단 원본과 파생 baseline. */
@RestController
@RequestMapping("/api/dna")
@SecurityRequirement(name = "bearerAuth")
public class DnaInfoController {

    private final DnaInfoService dnaInfoService;

    public DnaInfoController(DnaInfoService dnaInfoService) {
        this.dnaInfoService = dnaInfoService;
    }

    /**
     * 경로에 사용자 식별자가 없다. 남의 진단을 조회할 수 있는 URL 자체를 만들지 않는다 —
     * 토큰의 {@code sub} 가 곧 조회 대상이다.
     */
    @GetMapping
    public DnaInfoResponse myDna(@AuthenticationPrincipal Jwt jwt) {
        return dnaInfoService.myDna(jwt.getSubject());
    }
}
