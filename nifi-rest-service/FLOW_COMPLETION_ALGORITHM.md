# Алгоритм определения завершения работы потока NiFi

## Обзор

Реализован алгоритм определения завершения работы стартованного по расписанию потока NiFi на основе анализа очередей в процессорной группе.

**Ключевая особенность:** Алгоритм надёжно отличает момент, когда поток ещё не стартовал (очереди пусты), от момента завершения работы потока (очереди опустели после обработки данных).

---

## Важное замечание: Все процессоры стартованы по расписанию

Поскольку все процессоры в процессорной группе **уже стартованы** (запускаются по расписанию), состояние процессоров `Running` не является признаком начала работы потока.

### Единственный признак старта потока

| Критерий | Значение |
|----------|----------|
| **Признак старта** | Появление FlowFile в очередях (> 0) |
| **Признак работы** | Наличие данных в очередях (FlowFile Count > 0) |
| **Признак завершения** | Очереди пусты ПОСЛЕ того как были данные |

**Не используется:**
- Состояние процессоров (Running/Stopped) - все процессоры всегда Running
- ActiveProcessorCount - не является надёжным индикатором

---

## Ключевое различие: "Не стартовал" vs "Завершился"

### Проблема

Оба состояния выглядят одинаково при однократной проверке:
- **Поток не стартовал**: все очереди пусты
- **Поток завершился**: все очереди пусты (после обработки)

### Решение

Алгоритм отслеживает **динамику изменений** во времени:

| Критерий | Поток не стартовал | Поток завершился |
|----------|-------------------|------------------|
| **Начальное состояние** | Очереди пусты | Очереди могут быть пусты |
| **Активность в процессе** | ❌ Никогда не было FlowFile | ✅ Были зафиксированы FlowFile |
| **FlowFile в очередях** | Всегда 0 | Было > 0, стало 0 |
| **Счётчик пустых проверок** | Сбрасывается | Увеличивается |
| **Статус при таймауте** | `NOT_STARTED` | `TIMEOUT` |
| **Пик FlowFile** | 0 | > 0 (запоминается) |

---

## Архитектура

### Компоненты

1. **FlowCompletionMonitorService** - основной сервис мониторинга
2. **FlowCompletionController** - REST контроллер для управления мониторингом
3. **Модели данных**:
   - `FlowCompletionStatus` - статус завершения потока
   - `QueueStatus` - статус очереди
   - `FlowCompletionConfig` - конфигурация мониторинга

### MonitoringState - внутреннее состояние

Для каждого monitored process group хранится:

```java
private static class MonitoringState {
    long startTime;
    int consecutiveEmptyChecks;
    boolean isMonitoring;
    List<QueueStatus> lastQueueStatuses;
    
    // === КЛЮЧЕВЫЕ ПОЛЯ ДЛЯ РАЗЛИЧЕНИЯ СОСТОЯНИЙ ===
    boolean flowHasStarted = false;           // Флаг: поток стартовал (появились FlowFile)
    Long firstActivityTimestamp = null;       // Время появления первых FlowFile
    int totalFlowFileCountAtStart = 0;        // Начальное количество файлов
    Integer maxFlowFileCountObserved = null;  // Пик количества FlowFile
}
```

**Важно:** Поля `hasSeenActiveProcessors` и `hasSeenDataInQueues` удалены, т.к. для потоков с расписанием единственный признак - появление FlowFile.

---

## Детальный алгоритм

### Шаг 1: Инициализация мониторинга

```java
// Сохраняем начальное состояние
FlowCompletionStatus initialStatus = checkFlowCompletion(processGroupId, config);
state.totalFlowFileCountAtStart = initialStatus.getTotalFlowFileCount();
```

### Шаг 2: Цикл мониторинга с отслеживанием активности

На каждой итерации:

#### 2.1. Отслеживание пика FlowFile (для определения фазы завершения)

```java
int currentTotalFlowFiles = status.getTotalFlowFileCount();

if (currentTotalFlowFiles > 0) {
    if (state.maxFlowFileCountObserved == null || currentTotalFlowFiles > state.maxFlowFileCountObserved) {
        state.maxFlowFileCountObserved = currentTotalFlowFiles;
    }
}
```

#### 2.2. Фиксация первой активности (появление FlowFile)

```java
if (currentTotalFlowFiles > 0 && !state.flowHasStarted) {
    state.flowHasStarted = true;
    state.firstActivityTimestamp = System.currentTimeMillis();
    logger.info("Detected data in queues: {} files (flow STARTED)", currentTotalFlowFiles);
}
```

**Ключевой момент:** Поскольку все процессоры стартованы по расписанию, появление FlowFile - единственный признак начала работы потока.

#### 2.3. Проверка подтверждения старта

```java
boolean confirmFlowStarted() {
    return flowHasStarted;  // Только по наличию FlowFile
}
```

### Шаг 3: Логика определения завершения

#### Сценарий A: Поток ещё не стартовал

```java
if (!state.confirmFlowStarted() && status.getTotalFlowFileCount() == 0) {
    logger.debug("Flow not started yet, queues empty. Waiting for activity...");
    state.consecutiveEmptyChecks = 0; // СБРАСЫВАЕМ - это НЕ завершение!
    Thread.sleep(interval);
    continue;
}
```

**Ключевой момент:** Счётчик последовательных пустых проверок сбрасывается, потому что пустые очереди до старта - это нормальное состояние ожидания.

#### Сценарий B: Поток стартовал и очереди пусты

```java
if (allQueuesEmpty) {
    if (state.confirmFlowStarted()) {
        // ✅ Это может быть завершение - увеличиваем счётчик
        state.consecutiveEmptyChecks++;
        
        if (state.consecutiveEmptyChecks >= config.getConsecutiveEmptyChecksRequired()) {
            // Поток завершен: все очереди пусты после того как поток стартовал
            status.setCompleted(true);
            status.setStatus("COMPLETED");
            
            long flowDuration = state.firstActivityTimestamp != null 
                ? (System.currentTimeMillis() - state.firstActivityTimestamp) 
                : 0L;
                
            status.setMessage(String.format(
                "Поток завершен: все очереди пусты в течение %d сек (общее время работы: %d мс, пик: %d файлов)",
                state.consecutiveEmptyChecks * interval / 1000,
                flowDuration,
                state.maxFlowFileCountObserved != null ? state.maxFlowFileCountObserved : 0
            ));
            return status;
        }
    } else {
        // ❌ Очереди пусты, но поток ещё не стартовал - это НЕ завершение
        logger.debug("Queues empty but flow hasn't started yet - not counting as completion");
        state.consecutiveEmptyChecks = 0;
    }
}
```

### Шаг 4: Обработка таймаута

```java
if (elapsed > timeout) {
    if (!state.confirmFlowStarted()) {
        timeoutStatus.setStatus("NOT_STARTED");
        timeoutStatus.setMessage("Поток не стартовал: не было обнаружено данных в очередях (FlowFiles)");
    } else {
        timeoutStatus.setStatus("TIMEOUT");
        timeoutStatus.setMessage("Превышено максимальное время ожидания: " + timeout + " мс");
    }
    throw new TimeoutException(...);
}
```

---

## Диаграмма состояний

```
┌─────────────────────────────────────────────────────────────────┐
│                    MONITORING STARTED                           │
│              (initialFlowFileCount сохранено)                   │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
        ┌───────────────────────────────────┐
        │   ЦИКЛ ПРОВЕРКИ (каждые N секунд) │
        └─────────────────┬─────────────────┘
                          │
                          ▼
        ┌─────────────────────────────────────────┐
        │  Есть FlowFile в очередях?              │
        │  (totalFlowFileCount > 0)               │
        └───────────┬─────────────────┬───────────┘
                    │ YES             │ NO
                    ▼                 ▼
        ┌───────────────────┐  ┌─────────────────────────┐
        │ Запоминаем пик    │  │ flowHasStarted?         │
        │ Обновляем         │  │                         │
        │ maxFlowFileCount  │  │ ┌───────┐ ┌───────────┐ │
        │                   │  │ │  NO   │ │    YES    │ │
        │ flowHasStarted =  │  │ │       │ │           │ │
        │ true              │  │ │ Сброс │ │ Увеличение│ │
        │ firstActivity =   │  │ │ счётч │ │ счётчика  │ │
        │ now               │  │ │ ика   │ │ пустых    │ │
        │                   │  │ │       │ │ проверок  │ │
        │ conseqEmpty = 0   │  │ └───────┘ └─────┬─────┘ │
        └───────────────────┘  └─────────────────┼───────┘
                                                 │
                                                 ▼
                                    ┌─────────────────────────┐
                                    │ conseqEmpty >= threshold│
                                    └───────────┬─────────────┘
                                                │
                        ┌───────────────────────┼───────────────────────┐
                        │ NO                    │ YES                   │
                        ▼                       ▼                       │
            ┌───────────────────┐   ┌───────────────────────┐           │
            │ Продолжать цикл   │   │ COMPLETED!            │◄──────────┘
            │ (sleep interval)  │   │ - flowStarted = true  │
            └───────────────────┘   │ - flowDuration = now  │
                                    │   - firstActivity     │
                                    │ - maxFlowFileObserved │
                                    └───────────────────────┘
```

---

## Параметры конфигурации

```json
{
    "maxWaitTimeMs": 3600000,          // Максимальное время ожидания (1 час)
    "checkIntervalMs": 5000,           // Интервал между проверками (5 секунд)
    "emptyQueueThreshold": 0,          // Порог количества файлов для "пустой" очереди
    "emptyQueueSizeThreshold": 0,      // Порог размера очереди в байтах
    "consecutiveEmptyChecksRequired": 3, // Количество последовательных пустых проверок
    "ignoredQueuePatterns": []         // Паттерны игнорируемых очередей (regex)
}
```

**Удалён параметр:** `considerOnlyActiveProcessors` - не используется, т.к. все процессоры всегда Running.

---

## REST API

### Однократная проверка статуса

```bash
POST /api/nifi/flow-monitor/{processGroupId}/check
Content-Type: application/json

{ ...конфигурация... }
```

**Ответ:**
```json
{
    "processGroupId": "abc123...",
    "processGroupName": "My Data Flow",
    "completed": false,
    "status": "RUNNING",
    "totalQueueSize": 1024,
    "totalFlowFileCount": 5,
    "activeProcessorCount": 10,
    "totalProcessorCount": 10,
    "flowStarted": true,
    "firstActivityTimestamp": 1234567890,
    "initialFlowFileCount": 0,
    "maxFlowFileCountObserved": 25,
    "queueStatuses": [...],
    "checkTimestamp": 1234567890,
    "message": "Поток выполняется: 5 файлов в очередях, 10/10 процессоров Running"
}
```

### Запуск асинхронного мониторинга

```bash
POST /api/nifi/flow-monitor/{processGroupId}/start-monitoring
Content-Type: application/json

{ ...конфигурация... }
```

### Проверка статуса мониторинга

```bash
GET /api/nifi/flow-monitor/{processGroupId}/status
```

**Ответ (поток не стартовал):**
```json
{
    "processGroupId": "abc123...",
    "monitoring": true,
    "completed": false,
    "lastStatus": {
        "status": "IDLE",
        "flowStarted": false,
        "totalFlowFileCount": 0,
        "message": "Ожидание старта потока (ожидание появления FlowFile)..."
    }
}
```

**Ответ (поток завершился):**
```json
{
    "processGroupId": "abc123...",
    "monitoring": false,
    "completed": true,
    "lastStatus": {
        "status": "COMPLETED",
        "flowStarted": true,
        "firstActivityTimestamp": 1234567890,
        "flowDurationMs": 125000,
        "maxFlowFileCountObserved": 25,
        "message": "Поток завершен: все очереди пусты в течение 15 сек (время работы: 125000 мс, пик: 25 файлов)"
    }
}
```

**Ответ (таймаут, поток не стартовал):**
```json
{
    "processGroupId": "abc123...",
    "monitoring": false,
    "completed": false,
    "lastStatus": {
        "status": "NOT_STARTED",
        "flowStarted": false,
        "message": "Поток не стартовал: не было обнаружено данных в очередях (FlowFiles)"
    }
}
```

---

## Статусы потока

| Статус | Описание | flowStarted |
|--------|----------|-------------|
| `COMPLETED` | Поток завершён: очереди пусты после активности | `true` |
| `RUNNING` | Поток выполняется: есть FlowFile в очередях | `true` |
| `IDLE` | Нет FlowFile в очередях, ожидание старта | `false` |
| `NOT_STARTED` | Таймаут ожидания старта потока | `false` |
| `TIMEOUT` | Превышено время ожидания после старта | `true` |
| `ERROR` | Произошла ошибка при проверке | `null` |

---

## Примеры сценариев

### Сценарий 1: Поток успешно стартовал и завершился

```
T0:  Мониторинг запущен
     initialFlowFileCount = 0
     flowHasStarted = false
     maxFlowFileCountObserved = null

T5:  Проверка #1
     totalFlowFileCount = 0
     → flowHasStarted = false
     → consecutiveEmptyChecks = 0 (сброшен)

T10: Проверка #2
     totalFlowFileCount = 15  ← ПОЯВИЛИСЬ FLOWFILE!
     → flowHasStarted = true
     → firstActivityTimestamp = T10
     → maxFlowFileCountObserved = 15
     → consecutiveEmptyChecks = 0

T15-T45: Поток выполняется
     totalFlowFileCount меняется: 15 → 25 → 10 → 5 → 2
     maxFlowFileCountObserved обновляется: 15 → 25
     consecutiveEmptyChecks = 0

T50: Проверка #N
     totalFlowFileCount = 0  ← ОЧЕРЕДИ ОПУСТЕЛИ!
     → flowHasStarted = true (была активность!)
     → consecutiveEmptyChecks = 1

T55: Проверка #N+1
     totalFlowFileCount = 0
     → consecutiveEmptyChecks = 2

T60: Проверка #N+2
     totalFlowFileCount = 0
     → consecutiveEmptyChecks = 3 (порог достигнут!)
     → COMPLETED!
     → flowDurationMs = T60 - T10 = 50000 мс
     → message = "Поток завершен: все очереди пусты в течение 15 сек (время работы: 50000 мс, пик: 25 файлов)"
```

### Сценарий 2: Поток не стартовал (таймаут)

```
T0:  Мониторинг запущен
     initialFlowFileCount = 0
     flowHasStarted = false

T5-T300: Проверки каждые 5 секунд
     totalFlowFileCount = 0 (всегда)
     → flowHasStarted = false
     → consecutiveEmptyChecks = 0 (постоянно сбрасывается)

T300: Таймаут (maxWaitTimeMs = 300000)
     → flowHasStarted = false
     → статус = "NOT_STARTED"
     → сообщение = "Поток не стартовал: не было обнаружено данных в очередях (FlowFiles)"
```

### Сценарий 3: Поток был запущен ранее (очереди уже содержат данные)

```
T0:  Мониторинг запущен
     initialFlowFileCount = 50  ← Данные уже есть!
     flowHasStarted = false

T0:  Первая проверка
     totalFlowFileCount = 50
     → flowHasStarted = true (немедленно!)
     → firstActivityTimestamp = T0
     → maxFlowFileCountObserved = 50

T5-T50: Поток обрабатывает данные
     totalFlowFileCount: 50 → 40 → 25 → 10 → 0
     maxFlowFileCountObserved = 50 (не изменился, т.к. только уменьшается)

T55: Проверка
     totalFlowFileCount = 0
     → consecutiveEmptyChecks = 1

T65: COMPLETED!
     flowDurationMs = 65000
     message = "... (пик: 50 файлов)"
```

---

## Рекомендации по настройке

### Для потоков с отложенным стартом

Если поток запускается по расписанию с задержкой:

```json
{
    "maxWaitTimeMs": 600000,          // Ждём старта до 10 минут
    "checkIntervalMs": 5000,
    "consecutiveEmptyChecksRequired": 3
}
```

### Для быстрых потоков (секунды-минуты)

```json
{
    "checkIntervalMs": 1000,
    "consecutiveEmptyChecksRequired": 3,
    "maxWaitTimeMs": 300000
}
```

### Для длительных потоков (часы)

```json
{
    "checkIntervalMs": 30000,
    "consecutiveEmptyChecksRequired": 2,
    "maxWaitTimeMs": 14400000
}
```

---

## Интеграция

### Spring-интеграция

```java
@Autowired
private FlowCompletionMonitorService monitorService;

public void processScheduledFlow(String processGroupId) {
    FlowCompletionConfig config = new FlowCompletionConfig();
    config.setMaxWaitTimeMs(3600000L);
    config.setCheckIntervalMs(5000L);
    config.setConsecutiveEmptyChecksRequired(3);
    
    try {
        FlowCompletionStatus status = monitorService.waitForFlowCompletion(
            processGroupId, config);
        
        if (status.isCompleted()) {
            log.info("Поток завершён успешно: {}", status.getMessage());
            log.info("Время выполнения: {} мс", status.getFlowDurationMs());
            log.info("Пик FlowFile: {}", status.getMaxFlowFileCountObserved());
        }
    } catch (TimeoutException e) {
        if ("NOT_STARTED".equals(status.getStatus())) {
            log.error("Поток не стартовал в течение ожидаемого времени");
        } else {
            log.error("Таймаут ожидания потока", e);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Мониторинг прерван", e);
    }
}
```

### Обработка различных исходов

```java
switch (status.getStatus()) {
    case "COMPLETED":
        // Поток успешно завершился
        log.info("Поток завершён. Время работы: {} мс", status.getFlowDurationMs());
        break;
        
    case "NOT_STARTED":
        // Поток не стартовал (не появилось FlowFile)
        log.error("Поток не стартовал: проверьте расписание процессоров");
        break;
        
    case "TIMEOUT":
        // Поток стартовал, но не завершился вовремя
        log.warn("Поток выполняется дольше ожидаемого");
        break;
        
    case "ERROR":
        // Ошибка при проверке
        log.error("Ошибка мониторинга: {}", status.getMessage());
        break;
}
```

---

## Заключение

Алгоритм надёжно определяет завершение потока NiFi даже когда все процессоры стартованы по расписанию. Ключевое отличие - отслеживание появления FlowFile в очередях как единственного признака начала работы потока.

**Основные преимущества:**
1. ✅ Корректно различает "не стартовал" и "завершился"
2. ✅ Не зависит от состояния процессоров (Running/Stopped)
3. ✅ Запоминает пик активности для отладки
4. ✅ Поддерживает configurable пороги и таймауты
5. ✅ Предоставляет детальную информацию о выполнении
        sendNotification("Flow completed", status);
        break;
        
    case "NOT_STARTED":
        // Поток не стартовал - возможно, проблема с расписанием
        alertOperationsTeam("Flow did not start", status);
        break;
        
    case "TIMEOUT":
        // Поток выполнялся слишком долго
        alertOperationsTeam("Flow timeout", status);
        break;
        
    case "ERROR":
        // Ошибка мониторинга
        log.error("Monitoring error: {}", status.getMessage());
        break;
}
```

---

## Метрики и логирование

### Ключевые логи

```
INFO  - Starting flow completion monitoring for process group: abc123
INFO  - Detected data in queues: 15 files (first activity)
INFO  - Detected active processors: 3/10
INFO  - Flow confirmed as STARTED at timestamp: 1234567890
DEBUG - Consecutive empty checks (after start): 1/3
DEBUG - Consecutive empty checks (after start): 2/3
INFO  - Flow completion detected for process group: abc123
INFO  - Flow completion monitoring finished for process group: abc123
```

### Метрики для мониторинга

Добавьте в ответ метрики:
- `flowStartTime` - время первой активности
- `flowDuration` - длительность выполнения
- `timeToComplete` - время от старта до завершения
- `emptyCheckCount` - количество проверок до завершения

---

## Заключение

Реализованный алгоритм надёжно различает два критически важных состояния:

1. **"Поток не стартовал"** - очереди пусты, активности не было
   - Счётчик пустых проверок постоянно сбрасывается
   - При таймауте возвращается статус `NOT_STARTED`

2. **"Поток завершился"** - очереди пусты, но была зафиксирована активность
   - Счётчик пустых проверок увеличивается только после подтверждения старта
   - При достижении порога возвращается статус `COMPLETED`

Это позволяет корректно обрабатывать сценарии с отложенным стартом потоков по расписанию и избегать ложных срабатываний о завершении.
