package com.example.LAGO.realtime;

import com.example.LAGO.realtime.dto.TickData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KIS WebSocket으로부터 받은 실시간 틱 데이터를 압축 배치 저장하고 관리하는 서비스
 * 하이브리드 방식: 압축 배치 저장(히스토리) + 실시간 조회(최신 데이터)
 */
@Slf4j
@Service
public class RealtimeDataService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, byte[]> binaryRedisTemplate;
    private final ObjectMapper objectMapper;
    private final StockIdMapper stockIdMapper;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul"); // [NEW]
    private final RealTimeDataBroadcaster broadcaster;

    // (선택) 인덱스 키 상수
    private static final String CHUNK_BLOB_KEY  = "ticks:chunk:%s:blob"; // [NEW]
    private static final String CHUNK_META_KEY  = "ticks:chunk:%s:meta"; // [NEW]
    private static final String CHUNKS_ZSET_ALL = "ticks:chunks";        // [NEW]
    private static final String CHUNKS_ZSET_BY_STOCK = "ticks:chunks:byStock:%d"; // [NEW]

    // 생성자: 이 하나만 남기세요
    public RealtimeDataService(
            @Qualifier("redisTemplate") RedisTemplate<String, String> redisTemplate,
            @Qualifier("binaryRedisTemplate") RedisTemplate<String, byte[]> binaryRedisTemplate,
            ObjectMapper objectMapper,
            StockIdMapper stockIdMapper,
            RealTimeDataBroadcaster broadcaster // 추가
    ) {
        this.redisTemplate = redisTemplate;
        this.binaryRedisTemplate = binaryRedisTemplate;
        this.objectMapper = objectMapper;
        this.stockIdMapper = stockIdMapper;
        this.broadcaster = broadcaster; // 추가
    }

    
    // 압축 배치 저장 설정
    private static final int CHUNK_SIZE = 1000;
    private static final int ZSTD_LEVEL = 3;
    
    // 종목별 청크 관리 (메모리 캐시)
    private final Map<Integer, TickChunk> stockChunks = new ConcurrentHashMap<>();
    
    // Redis Key 패턴
    private static final String REALTIME_KEY_PREFIX = "realtime:stock:";  // 실시간 조회용
    private static final String BATCH_KEY_PREFIX = "tick_batch:";        // 압축 배치용
    private static final String META_KEY_PREFIX = "tick_meta:";          // 메타데이터용
    private static final String LATEST_UPDATE_KEY = "realtime:latest_update";
    
    /**
     * KIS WebSocket에서 받은 틱 데이터를 압축 배치 저장 + 실시간 조회용 저장
     * 
     * @param tickData KIS 틱 데이터
     */
    public void saveTickData(TickData tickData) {
        try {
            if (!tickData.isValid()) {
                log.warn("Invalid tick data, skipping save: {}", tickData);
                return;
            }
            
            // 1. 압축 배치 저장 처리
            saveToCompressedBatch(tickData);
            
            // 2. 실시간 조회용 최신 데이터 저장 (기존 방식 유지)
            saveLatestForQuery(tickData);

            // 여기서 실시간 전송
            broadcaster.sendRealTimeData(tickData);
            
            log.debug("Processed tick data: {} - {}", tickData.getCode(), tickData.getClosePrice());
            
        } catch (Exception e) {
            log.error("Failed to save tick data: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 압축 배치 저장 처리
     * @param tickData 틱 데이터
     */
    private void saveToCompressedBatch(TickData tickData) {
        // 종목코드를 ID로 변환
        Integer stockId = stockIdMapper.getStockId(tickData.getCode());
        if (stockId == null) {
            log.warn("Unknown stock code, skipping batch save: {}", tickData.getCode());
            return;
        }
        
        // 종목별 청크 가져오기 (없으면 생성)
        TickChunk chunk = stockChunks.computeIfAbsent(stockId, 
            k -> new TickChunk(CHUNK_SIZE));
        
        // 청크에 데이터 추가
        if (!chunk.add16B(tickData, stockId)) {
            // 청크가 가득 참 → Redis에 저장하고 새 청크 생성
            saveBatchToRedis(stockId, chunk);
            
            // 새 청크 생성 후 데이터 추가
            chunk = new TickChunk(CHUNK_SIZE);
            chunk.add16B(tickData, stockId);
            stockChunks.put(stockId, chunk);
        }
    }
    
    /**
     * 실시간 조회용 최신 데이터 저장 (기존 방식)
     * @param tickData 틱 데이터
     */
    private void saveLatestForQuery(TickData tickData) {
        String key = REALTIME_KEY_PREFIX + tickData.getCode();
        
        // 데이터를 Hash 형태로 저장
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("code", tickData.getCode());
        dataMap.put("date", tickData.getDate());
        dataMap.put("closePrice", tickData.getClosePrice().toString());
        dataMap.put("openPrice", tickData.getOpenPrice().toString());
        dataMap.put("highPrice", tickData.getHighPrice().toString());
        dataMap.put("lowPrice", tickData.getLowPrice().toString());
        dataMap.put("volume", tickData.getVolume().toString());
        dataMap.put("receivedAt", tickData.getReceivedAt().toString());
        dataMap.put("lastUpdated", LocalDateTime.now().toString());
        
        // Redis Hash에 저장 (TTL: 1시간)
        redisTemplate.opsForHash().putAll(key, dataMap);
        redisTemplate.expire(key, Duration.ofHours(1));
        
        // 최신 업데이트 시간 기록
        redisTemplate.opsForValue().set(LATEST_UPDATE_KEY, 
            LocalDateTime.now().toString(), Duration.ofHours(1));
    }
    
    /**
     * 청크를 압축하여 Redis에 저장
     * @param stockId 종목 ID
     * @param chunk 저장할 청크
     */
//    private void saveBatchToRedis(Integer stockId, TickChunk chunk) {
//        try {
//            if (chunk.isEmpty()) {
//                return;
//            }
//
//            // 압축된 데이터 생성
//            byte[] compressed = chunk.toCompressedBlob(ZSTD_LEVEL);
//
//            // Redis 키 생성 (stockId + timestamp)
//            String key = String.format("%s%d:%d", BATCH_KEY_PREFIX, stockId, System.currentTimeMillis());
//
//            // 압축된 바이너리 데이터 저장 (TTL: 24시간)
//            binaryRedisTemplate.opsForValue().set(key, compressed, Duration.ofHours(24));
//
//            // 메타데이터 저장
//            String metaKey = META_KEY_PREFIX + stockId;
//            String metaValue = String.format("count=%d,size=%d,ratio=%.2f%%,key=%s",
//                chunk.count(), compressed.length,
//                chunk.getCompressionRatio(compressed.length), key);
//
//            redisTemplate.opsForHash().put(metaKey, key, metaValue);
//            redisTemplate.expire(metaKey, Duration.ofHours(24));
//
//            // 통계 로그
//            String stockCode = stockIdMapper.getStockCode(stockId);
//            log.info("📦 Compressed batch saved: {} ({}) - {} ticks, {} bytes, {:.1f}% ratio",
//                stockCode, stockId, chunk.count(), compressed.length,
//                chunk.getCompressionRatio(compressed.length));
//
//        } catch (Exception e) {
//            log.error("Failed to save compressed batch: {}", e.getMessage(), e);
//        }
//    }
    private void saveBatchToRedis(Integer stockId, TickChunk chunk) {
        try {
            if (chunk.isEmpty()) return;

            // 1) 압축 + 기본 메타 계산
            byte[] compressed = chunk.toCompressedBlob(ZSTD_LEVEL);
            int count = chunk.count();
            int rawBytes = count * 16;

            // 2) (기존) 레거시 키에도 저장
            String legacyKey = String.format("%s%d:%d", BATCH_KEY_PREFIX, stockId, System.currentTimeMillis());
            binaryRedisTemplate.opsForValue().set(legacyKey, compressed, Duration.ofHours(24));

            // 3) (기존) 종목별 메타 해시 업데이트
            String metaKeyPerStock = META_KEY_PREFIX + stockId;
            String metaValue = String.format("count=%d,size=%d,ratio=%.2f%%,key=%s",
                    count, compressed.length, chunk.getCompressionRatio(compressed.length), legacyKey);
            redisTemplate.opsForHash().put(metaKeyPerStock, legacyKey, metaValue);
            redisTemplate.expire(metaKeyPerStock, Duration.ofHours(24));

            // ---------------------------
            // [NEW] per-chunk 저장 + 인덱스
            // ---------------------------
            // 4) 청크 ID
            String chunkId = java.util.UUID.randomUUID().toString();

            // 5) blob 저장 (ticks:chunk:{id}:blob)
            String chunkBlobKey = "ticks:chunk:" + chunkId + ":blob";
            binaryRedisTemplate.opsForValue().set(chunkBlobKey, compressed, Duration.ofDays(1));

            // 6) meta 저장 (ticks:chunk:{id}:meta)
            String chunkMetaKey = "ticks:chunk:" + chunkId + ":meta";
            Map<String, String> meta = new HashMap<>();
            meta.put("count", String.valueOf(count));
            meta.put("rawBytes", String.valueOf(rawBytes));                         // = count * 16
            meta.put("baseDate", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toString()); // KST
            meta.put("zstdLevel", String.valueOf(ZSTD_LEVEL));
            meta.put("ver", "1");
            meta.put("endian", "LE");
            meta.put("stockId", String.valueOf(stockId));
            meta.put("createdAt", java.time.LocalDateTime.now().toString());
            redisTemplate.opsForHash().putAll(chunkMetaKey, meta);
            redisTemplate.expire(chunkMetaKey, Duration.ofDays(1));

            // 7) 인덱스(ZSET)
            long createdAt = System.currentTimeMillis();

            // 조회/리스트 인덱스는 생성시각 그대로
            redisTemplate.opsForZSet().add("ticks:chunks", chunkId, createdAt);
            redisTemplate.opsForZSet().add("ticks:chunks:byStock:" + stockId, chunkId, createdAt);

            // DB 적재 대기열은 +10초로 스케줄
            redisTemplate.opsForZSet().add("ticks:ingest:pending", chunkId, createdAt + 10_000);


            // 8) 로그
            String stockCode = stockIdMapper.getStockCode(stockId);
            double ratio = chunk.getCompressionRatio(compressed.length);
            log.info("📦 Compressed batch saved: {} ({}) - ticks={}, blob={}B, ratio={}%, chunkId={}",
                    stockCode, stockId, count, compressed.length, String.format("%.1f", ratio), chunkId);

        } catch (Exception e) {
            log.error("Failed to save compressed batch: {}", e.getMessage(), e);
        }
    }


    /**
     * Redis에서 특정 종목의 최신 틱 데이터 조회
     * 
     * @param stockCode 종목 코드
     * @return TickData 또는 null
     */
    public TickData getTickData(String stockCode) {
        try {
            String key = REALTIME_KEY_PREFIX + stockCode;
            Map<Object, Object> dataMap = redisTemplate.opsForHash().entries(key);
            
            if (dataMap.isEmpty()) {
                return null;
            }
            
            // Map을 TickData로 변환
            return convertMapToTickData(dataMap);
            
        } catch (Exception e) {
            System.err.println("Failed to get tick data from Redis: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Redis에서 모든 틱 데이터 조회
     * 
     * @return 종목코드별 틱 데이터 Map
     */
    public Map<String, TickData> getAllTickData() {
        try {
            Set<String> keys = redisTemplate.keys(REALTIME_KEY_PREFIX + "*");
            Map<String, TickData> result = new HashMap<>();
            
            for (String key : keys) {
                String stockCode = key.replace(REALTIME_KEY_PREFIX, "");
                Map<Object, Object> dataMap = redisTemplate.opsForHash().entries(key);
                
                if (!dataMap.isEmpty()) {
                    TickData data = convertMapToTickData(dataMap);
                    if (data != null) {
                        result.put(stockCode, data);
                    }
                }
            }
            
            return result;
            
        } catch (Exception e) {
            System.err.println("Failed to get all tick data from Redis: " + e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * Redis에서 특정 종목 데이터 삭제
     * 
     * @param stockCode 종목 코드
     */
    public void deleteRealtimeData(String stockCode) {
        try {
            String key = REALTIME_KEY_PREFIX + stockCode;
            redisTemplate.delete(key);
            System.out.println("Deleted realtime data from Redis: " + stockCode);
        } catch (Exception e) {
            System.err.println("Failed to delete realtime data from Redis: " + e.getMessage());
        }
    }
    
    /**
     * 모든 실시간 데이터 삭제
     */
    public void clearAllRealtimeData() {
        try {
            Set<String> keys = redisTemplate.keys(REALTIME_KEY_PREFIX + "*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                System.out.println("Cleared all realtime data from Redis");
            }
        } catch (Exception e) {
            System.err.println("Failed to clear realtime data from Redis: " + e.getMessage());
        }
    }
    
    /**
     * Redis Hash Map을 TickData 객체로 변환
     */
    private TickData convertMapToTickData(Map<Object, Object> dataMap) {
        try {
            return TickData.builder()
                    .code((String) dataMap.get("code"))
                    .date((String) dataMap.get("date"))
                    .closePrice(Integer.parseInt((String) dataMap.get("closePrice")))
                    .openPrice(Integer.parseInt((String) dataMap.get("openPrice")))
                    .highPrice(Integer.parseInt((String) dataMap.get("highPrice")))
                    .lowPrice(Integer.parseInt((String) dataMap.get("lowPrice")))
                    .volume(Integer.parseInt((String) dataMap.get("volume")))
                    .receivedAt(LocalDateTime.parse((String) dataMap.get("receivedAt")))
                    .build();
        } catch (Exception e) {
            System.err.println("Failed to convert map to TickData: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 특정 종목의 최신 실시간 가격 조회 (매매 처리용)
     * 
     * @param stockCode 종목 코드 (예: "005930")
     * @return 최신 종가, Redis에 데이터가 없으면 null
     */
    public Integer getLatestPrice(String stockCode) {
        try {
            String key = REALTIME_KEY_PREFIX + stockCode; // "realtime:stock:005930"
            String priceStr = (String) redisTemplate.opsForHash().get(key, "closePrice");
            
            if (priceStr != null) {
                Integer price = Integer.parseInt(priceStr);
                log.debug("Redis에서 종목 {} 실시간 가격 조회: {}원", stockCode, price);
                return price;
            } else {
                log.warn("Redis에 종목 {} 실시간 가격 데이터 없음", stockCode);
                return null;
            }
        } catch (Exception e) {
            log.error("종목 {} 실시간 가격 조회 실패", stockCode, e);
            return null;
        }
    }

    /**
     * Redis 연결 상태 확인
     * 
     * @return true if connected
     */
    public boolean isRedisConnected() {
        try {
            redisTemplate.opsForValue().set("health:check", "ok", Duration.ofSeconds(1));
            String result = redisTemplate.opsForValue().get("health:check");
            return "ok".equals(result);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 스케줄러: 미완성 청크들을 정기적으로 Redis에 저장
     * 1분마다 실행하여 메모리에 남아있는 청크들 저장
     */
    @Scheduled(fixedRate = 60000) // 1분마다
    public void flushPendingChunks() {
        try {
            int flushedCount = 0;
            
            for (Map.Entry<Integer, TickChunk> entry : stockChunks.entrySet()) {
                Integer stockId = entry.getKey();
                TickChunk chunk = entry.getValue();
                
                if (!chunk.isEmpty()) {
                    saveBatchToRedis(stockId, chunk);
                    chunk.reset();  // 청크 재사용을 위해 리셋
                    flushedCount++;
                }
            }
            
            if (flushedCount > 0) {
                log.info("🔄 Scheduled flush completed: {} chunks saved", flushedCount);
            }
            
        } catch (Exception e) {
            log.error("Failed to flush pending chunks: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 압축 배치 저장 통계 정보 조회
     * @return 통계 맵
     */
    public Map<String, Object> getBatchStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            int totalChunks = stockChunks.size();
            int totalTicks = stockChunks.values().stream()
                .mapToInt(TickChunk::count)
                .sum();
                
            stats.put("activeChunks", totalChunks);
            stats.put("pendingTicks", totalTicks);
            stats.put("chunkSize", CHUNK_SIZE);
            stats.put("compressionLevel", ZSTD_LEVEL);
            stats.put("lastUpdate", getLastUpdateTime());
            
        } catch (Exception e) {
            log.error("Failed to get batch statistics: {}", e.getMessage());
        }
        
        return stats;
    }
    
    /**
     * 특정 종목의 압축 배치 메타데이터 조회
     * @param stockCode 종목코드
     * @return 메타데이터 맵
     */
    public Map<Object, Object> getBatchMetadata(String stockCode) {
        try {
            Integer stockId = stockIdMapper.getStockId(stockCode);
            if (stockId == null) {
                return new HashMap<>();
            }
            
            String metaKey = META_KEY_PREFIX + stockId;
            return redisTemplate.opsForHash().entries(metaKey);
            
        } catch (Exception e) {
            log.error("Failed to get batch metadata for {}: {}", stockCode, e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * 최근 업데이트 시간 조회
     */
    public String getLastUpdateTime() {
        try {
            return redisTemplate.opsForValue().get(LATEST_UPDATE_KEY);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 메모리 정리: 사용하지 않는 청크 제거
     */
    public void cleanupMemory() {
        try {
            int before = stockChunks.size();
            stockChunks.entrySet().removeIf(entry -> entry.getValue().isEmpty()); // 비어있는 청크 제거
            int removedCount = before - stockChunks.size();
            
            if (removedCount > 0) {
                log.info("🧹 Memory cleanup completed: {} empty chunks removed", removedCount);
            }
            
        } catch (Exception e) {
            log.error("Failed to cleanup memory: {}", e.getMessage(), e);
        }
    }

    // [NEW] 최신 청크 ID 목록
    public java.util.List<String> latestChunkIds(int limit) {
        var ids = redisTemplate.opsForZSet().reverseRange(CHUNKS_ZSET_ALL, 0, limit - 1);
        return ids == null ? java.util.List.of() : ids.stream().toList();
    }

    // [NEW] 종목별 최신 청크 ID 목록
    public java.util.List<String> latestChunkIdsByStock(int stockId, int limit) {
        var key = String.format(CHUNKS_ZSET_BY_STOCK, stockId);
        var ids = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
        return ids == null ? java.util.List.of() : ids.stream().toList();
    }


}