package com.example.LAGO.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 주식 정보 엔티티
 * 지침서 명세 STOCK_INFO 테이블과 완전 일치
 */
@Entity
@Table(name = "stock_info")
@Getter 
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {

    @Id
    @Column(name = "code", length = 10)
    private String code; // 종목 코드 (예: 005930)

    @Column(name = "name", nullable = false, length = 100)
    private String name; // 종목명 (예: 삼성전자)

    @Column(name = "market", length = 10)
    private String market; // 시장구분 (KOSPI/KOSDAQ)

    @Column(name = "sector", length = 50)
    private String sector; // 업종

    @Column(name = "current_price")
    private Integer currentPrice; // 현재가

    @Column(name = "open_price")
    private Integer openPrice; // 시가

    @Column(name = "high_price")
    private Integer highPrice; // 고가

    @Column(name = "low_price")
    private Integer lowPrice; // 저가

    @Column(name = "close_price")
    private Integer closePrice; // 종가

    @Column(name = "fluctuation_rate")
    private Float fluctuationRate; // 등락률

    @Column(name = "volume")
    private Long volume; // 거래량

    @Column(name = "market_cap")
    private Long marketCap; // 시가총액

    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // 최종 업데이트 시간

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
