package com.example.LAGO.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;



/**
 * 계좌 엔티티
 * 지침서 명세 ACCOUNTS 테이블과 완전 일치
 */
@Entity
@Table(name = "accounts")
@Getter 
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "balance", nullable = false)
    private Integer balance;

    @Column(name = "total_asset", nullable = false)
    private Integer totalAsset;

    @Column(name = "profit", nullable = false)
    private Integer profit;

    @Column(name = "profit_rate", nullable = false)
    private Double profitRate;

    @Column(name = "type", nullable = false)
    private Integer type; // 계좌구분(0:모의투자, 1:역사챌린지, 2:AI봇)

    // 계좌 타입 상수
    public static final Integer TYPE_MOCK_TRADING = 0;    // 모의투자
    public static final Integer TYPE_HISTORY_CHALLENGE = 1; // 역사챌린지
    public static final Integer TYPE_AI_BOT = 2;          // AI봇
}
