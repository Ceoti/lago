package com.example.LAGO.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.List;

@Entity
@Table(name = "stock_info")
@Getter
@Setter
@NoArgsConstructor
public class StockInfo {
/**
 * 주식 정보 엔티티
 * 연동된 EC2 DB STOCK_INFO 테이블과 완전 일치
 * 
 * 테이블 구조:
 * - stock_info_id: PK (auto_increment)
 * - code: 종목 코드 (varchar(20))
 * - name: 종목명 (varchar(100))
 * - market: 시장구분 (varchar(20))
 */

    /**
     * 주식 정보 고유 ID (PK)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_info_id")
    private Integer stockInfoId;

    /**
     * 종목 코드 (예: 005930)
     */
    @Column(name = "code", length = 20)
    private String code;

    /**
     * 종목명 (예: 삼성전자) - JPA 메소드 호환성을 위해 name 필드 추가
     */
    @Column(name = "name", length = 100)
    private String name;
    
    /**
     * 종목명 (예: 삼성전자) - 별칭
     */
    @Transient
    private String companyName;

    /**
     * 시장 구분 (KOSPI/KOSDAQ)
     */
    @Column(name = "market", length = 20)
    private String market;
    
    /**
     * 영문 회사명 (선택적 - 검색 별칭용) - DB에 없는 필드이므로 @Transient 처리
     */
    @Transient
    private String companyNameEn;
    
    /**
     * 시가총액 (정렬용) - DB에 없는 필드이므로 @Transient 처리
     */
    @Transient
    private Long marketCap;

    // STOCK_MINUTE과의 관계
    @OneToMany(mappedBy = "stockInfo", fetch = FetchType.LAZY)
    private List<StockMinute> stockMinutes;


    // 생성자
    public StockInfo(String stockCode, String name, String market) {
        this.code = stockCode;
        this.name = name;
        this.companyName = name; // 별칭 동기화
        this.market = market;
    }
    
    // 코드 호환성을 위한 getter 별칭
    public String getCode() {
        return code;
    }
    
    public String getCompanyName() {
        return name; // name 필드를 기준으로 반환
    }
    
    // name 필드 설정 시 companyName도 동기화
    public void setName(String name) {
        this.name = name;
        this.companyName = name;
    }
    
    public void setCompanyName(String companyName) {
        this.name = companyName;
        this.companyName = companyName;
    }

}
