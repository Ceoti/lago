package com.example.LAGO.repository;

import com.example.LAGO.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Stock 엔티티 Repository
 * 지침서 명세: STOCK_INFO 테이블 연동
 */
@Repository
public interface StockRepository extends JpaRepository<Stock, String> {

    /**
     * 종목명으로 검색 - 임시 비활성화
     * @param name 종목명 (부분 일치)
     * @return 매칭되는 종목 목록
     */
    // List<Stock> findByNameContainingIgnoreCase(String name);

    /**
     * 시장별 종목 조회
     * @param market 시장구분 (KOSPI/KOSDAQ)
     * @return 해당 시장의 종목 목록
     */
    List<Stock> findByMarket(String market);

    /**
     * 종목 코드로 시계열 데이터 조회 (날짜 내림차순)
     * @param code 종목 코드
     * @return 시계열 데이터 목록
     */
    @Query("SELECT s FROM Stock s WHERE s.code = :code ORDER BY s.updatedAt DESC")
    List<Stock> findByCodeOrderByDateDesc(@Param("code") String code);

    /**
     * 업종별 종목 조회
     * @param sector 업종
     * @return 해당 업종의 종목 목록
     */
    List<Stock> findBySector(String sector);

    /**
     * 현재가 범위로 조회
     * @param minPrice 최저가
     * @param maxPrice 최고가
     * @return 가격 범위 내 종목 목록
     */
    @Query("SELECT s FROM Stock s WHERE s.currentPrice BETWEEN :minPrice AND :maxPrice")
    List<Stock> findByPriceRange(@Param("minPrice") Integer minPrice, @Param("maxPrice") Integer maxPrice);

    /**
     * 인기 종목 조회 (거래량 기준 상위)
     * @param limit 조회 개수
     * @return 거래량 상위 종목 목록
     */
    @Query("SELECT s FROM Stock s ORDER BY s.volume DESC")
    List<Stock> findTopByVolumeDesc(@Param("limit") int limit);

    /**
     * 등락률 기준 조회
     * @param minRate 최소 등락률
     * @param maxRate 최대 등락률
     * @return 등락률 범위 내 종목 목록
     */
    @Query("SELECT s FROM Stock s WHERE s.fluctuationRate BETWEEN :minRate AND :maxRate ORDER BY s.fluctuationRate DESC")
    List<Stock> findByFluctuationRate(@Param("minRate") Float minRate, @Param("maxRate") Float maxRate);
    
    /**
     * 거래량 상위 50개 종목 조회 (AI 자동매매용)
     * @return 거래량 상위 50개 종목 목록
     */
    @Query("SELECT s FROM Stock s ORDER BY s.volume DESC LIMIT 50")
    List<Stock> findTop50ByOrderByVolumeDesc();
    
    /**
     * 종목 코드별 최신 데이터 조회 (AI 자동매매용)
     * @param code 종목 코드
     * @return 해당 종목의 최신 데이터
     */
    @Query("SELECT s FROM Stock s WHERE s.code = :code ORDER BY s.updatedAt DESC LIMIT 1")
    Optional<Stock> findTopByCodeOrderByUpdatedAtDesc(@Param("code") String code);
}
