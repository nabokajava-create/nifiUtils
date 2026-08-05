package com.example.nifi.repository;

import com.example.nifi.entity.FlowExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с историей выполнений потоков
 */
@Repository
public interface FlowExecutionHistoryRepository extends JpaRepository<FlowExecutionHistory, Long> {

    /**
     * Поиск последней записи мониторинга для указанной процессорной группы
     */
    Optional<FlowExecutionHistory> findFirstByProcessGroupIdOrderByStartTimeDesc(String processGroupId);

    /**
     * Поиск активной сессии мониторинга по ID процессорной группы
     */
    Optional<FlowExecutionHistory> findFirstByProcessGroupIdAndCompletedFalseOrderByStartTimeDesc(String processGroupId);

    /**
     * Поиск сессии по ID мониторинга
     */
    Optional<FlowExecutionHistory> findByMonitoringSessionId(String monitoringSessionId);

    /**
     * Поиск всех записей для процессорной группы за период
     */
    @Query("SELECT h FROM FlowExecutionHistory h WHERE h.processGroupId = :processGroupId AND h.startTime BETWEEN :startDate AND :endDate ORDER BY h.startTime DESC")
    List<FlowExecutionHistory> findByProcessGroupIdAndTimeRange(
            @Param("processGroupId") String processGroupId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Поиск последних N записей для процессорной группы
     */
    @Query("SELECT h FROM FlowExecutionHistory h WHERE h.processGroupId = :processGroupId ORDER BY h.startTime DESC LIMIT :limit")
    List<FlowExecutionHistory> findRecentByProcessGroupId(
            @Param("processGroupId") String processGroupId,
            @Param("limit") int limit
    );

    /**
     * Статистика выполнений по статусам за период
     */
    @Query("SELECT h.status, COUNT(h) FROM FlowExecutionHistory h WHERE h.startTime BETWEEN :startDate AND :endDate GROUP BY h.status")
    List<Object[]> getStatusStatistics(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Средняя длительность выполнения для процессорной группы
     */
    @Query("SELECT AVG(h.durationMs) FROM FlowExecutionHistory h WHERE h.processGroupId = :processGroupId AND h.completed = true AND h.durationMs IS NOT NULL")
    Long getAverageDurationByProcessGroupId(@Param("processGroupId") String processGroupId);

    /**
     * Все записи с неуспешной отправкой callback
     */
    List<FlowExecutionHistory> findByCallbackSentFalseAndCompletedTrue();
}
