# Архитектура интеграции Spring Boot и NiFi для мониторинга завершения потоков

## Обзор

Реализован архитектурно правильный подход для интеграции Spring Boot сервиса с Apache NiFi, обеспечивающий надёжное определение завершения работы потоков, запускаемых по расписанию.

## Проблема предыдущего подхода

При длительных интервалах между запусками потоков (например, раз в сутки) возникали сложности:
- Состояние "не стартовал" не обнулялось автоматически
- Требовался внешний триггер для сброса состояния
- Ресурсы сервиса простаивали в ожидании следующего запуска

## Новое решение: Сессионный мониторинг с callback

### Архитектурная схема

```
┌─────────────────────────────────────────────────────────────────┐
│                         Apache NiFi                             │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐   │
│  │ Schedule     │────▶│ Process      │────▶│ InvokeHTTP   │   │
│  │ (Timer)      │     │ Group        │     │ (Callback)   │   │
│  └──────────────┘     └──────────────┘     └──────┬───────┘   │
│                                                    │           │
└────────────────────────────────────────────────────┼───────────┘
                                                     │ POST /api/nifi/flow-monitor/start
                                                     ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Service                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  FlowCompletionController                                │  │
│  │  • Создаёт изолированную сессию мониторинга              │  │
│  │  • correlationId для трекинга                            │  │
│  │  • callbackUrl для результата                            │  │
│  └───────────────────┬──────────────────────────────────────┘  │
│                      │                                         │
│  ┌───────────────────▼──────────────────────────────────────┐  │
│  │  FlowCompletionMonitorService                            │  │
│  │  • Отслеживает появление FlowFile (признак старта)       │  │
│  │  • Считает последовательные пустые проверки              │  │
│  │  • Фиксирует пик активности                              │  │
│  └───────────────────┬──────────────────────────────────────┘  │
│                      │                                         │
│  ┌───────────────────▼──────────────────────────────────────┐  │
│  │  CallbackService                                         │  │
│  │  • Отправляет результат обратно в NiFi                   │  │
│  │  • Retry логика при неудаче                              │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Компоненты

### 1. FlowMonitoringRequest
Модель запроса от NiFi:
- `processGroupId` - ID процессорной группы
- `correlationId` - Уникальный ID запуска (UUID/timestamp)
- `flowName` - Имя потока для логирования
- `callbackUrl` - URL для отправки результата
- `maxWaitTimeMs`, `checkIntervalMs` - Настройки мониторинга
- `metadata` - Дополнительные данные

### 2. FlowMonitoringResult
Модель результата:
- `correlationId` - Связь с исходным запросом
- `completed` - Статус завершения
- `status` - COMPLETED/TIMEOUT/NOT_STARTED/ERROR
- `flowDurationMs` - Длительность выполнения
- `peakFlowFileCount` - Пик активности
- `message` - Человеко-читаемое описание

### 3. FlowCompletionController
REST контроллер с endpoint'ами:
- `POST /start` - Запуск сессии мониторинга (вызывается из NiFi)
- `GET /status/{processGroupId}` - Получение статуса
- `POST /stop/{processGroupId}` - Остановка мониторинга
- `GET /active` - Список активных сессий

### 4. FlowCompletionMonitorService
Сервис мониторинга:
- `waitForFlowCompletionWithTracking()` - Запуск с трекингом метрик
- `getPeakFlowFileCount()` - Пик количества FlowFile
- `getConsecutiveEmptyChecks()` - Счётчик пустых проверок

### 5. CallbackService
Сервис обратного вызова:
- `sendCallback()` - Отправка результата в NiFi
- `sendCallbackWithRetry()` - Отправка с повторными попытками

## Алгоритм работы

### Шаг 1: NiFi запускает поток по расписанию
```
Processor Group (Scheduled) → Start → Process Data → ...
                                      ↓
                              InvokeHTTP Processor
                              URL: http://spring-boot:8080/api/nifi/flow-monitor/start
                              Method: POST
                              Body: {
                                "processGroupId": "abc-123",
                                "correlationId": "run-2024-01-15-001",
                                "flowName": "Daily Data Processing",
                                "callbackUrl": "http://nifi:8080/nifi-api/flow-monitoring/callback",
                                "maxWaitTimeMs": 3600000
                              }
```

### Шаг 2: Spring Boot создаёт сессию мониторинга
```java
MonitoringSession session = new MonitoringSession(
    correlationId,      // "run-2024-01-15-001"
    flowName,           // "Daily Data Processing"
    callbackUrl,        // "http://nifi:..."
    metadata            // {...}
);
```

**Ключевое преимущество**: Каждая сессия имеет чистое состояние!

### Шаг 3: Мониторинг появления FlowFile
```
Время    | Очереди | Активность | Событие
---------|---------|------------|------------------
T0       | 0       | Нет        | Запуск сессии
T+5s     | 0       | Нет        | Проверка 1 (сброс счётчика)
T+10s    | 0       | Нет        | Проверка 2 (сброс счётчика)
T+15s    | 150     | Да         | Поток СТАРТОВАЛ! (flowHasStarted=true)
T+20s    | 500     | Да         | Пик активности (peak=500)
T+25s    | 200     | Да         | Обработка идёт
T+30s    | 50      | Да         | Почти готово
T+35s    | 0       | Нет        | Проверка 1/3 после старта
T+40s    | 0       | Нет        | Проверка 2/3 после старта
T+45s    | 0       | Нет        | Проверка 3/3 → COMPLETED!
```

### Шаг 4: Отправка результата в NiFi
```java
FlowMonitoringResult result = FlowMonitoringResult.fromStatus(
    status, correlationId, flowName, startTime, 
    peakFlowFileCount, consecutiveEmptyChecks, metadata
);

callbackService.sendCallbackWithRetry(callbackUrl, result, 3);
```

### Шаг 5: NiFi получает callback
```json
{
  "correlationId": "run-2024-01-15-001",
  "processGroupId": "abc-123",
  "flowName": "Daily Data Processing",
  "completed": true,
  "status": "COMPLETED",
  "flowDurationMs": 30000,
  "monitoringDurationMs": 45000,
  "peakFlowFileCount": 500,
  "consecutiveEmptyChecks": 3,
  "message": "Поток завершен: все очереди пусты в течение 15 сек",
  "timestamp": 1705312845000
}
```

## Преимущества подхода

### 1. Изоляция сессий
Каждый запуск потока создаёт новую сессию с чистым состоянием:
- Нет накопления старого состояния
- Нет необходимости в сбросе между запусками
- Поддержка любых интервалов (секунды, часы, дни)

### 2. Надёжное определение старта
Старт фиксируется только по появлению FlowFile в очередях:
- Не зависит от статуса процессоров (все стартованы по расписанию)
- Исключает ложные срабатывания
- Корректно работает с delayed processing

### 3. Подтверждение завершения
Требование N последовательных пустых проверок:
- Исключает преждевременное завершение
- Учитывает burst-паттерны обработки
- Настраиваемый порог (consecutiveEmptyChecksRequired)

### 4. Обратная связь в NiFi
Callback механизм позволяет:
- Триггерить следующие шаги в потоке
- Логировать результаты выполнения
- Строить сложные orchestration паттерны

### 5. Метрики и наблюдаемость
Сбор ключевых метрик:
- Время отклика (flowDurationMs)
- Пик нагрузки (peakFlowFileCount)
- Общая длительность (monitoringDurationMs)

## Конфигурация в NiFi

### InvokeHTTP Processor (запуск мониторинга)
```
Properties:
  HTTP Method: POST
  Remote URL: http://spring-boot-service:8080/api/nifi/flow-monitor/start
  Content-Type: application/json
  Request Body: {
    "processGroupId": "${nifi.process.group.id}",
    "correlationId": "run-${now():format('yyyy-MM-dd-HHmmss')}",
    "flowName": "${nifi.flow.name}",
    "callbackUrl": "http://${nifi.web.http.host}:${nifi.web.http.port}/nifi-api/flow-monitoring/callback",
    "maxWaitTimeMs": 3600000,
    "checkIntervalMs": 5000,
    "consecutiveEmptyChecksRequired": 3
  }
  
Connection: После стартового блока процессорной группы
```

### HandleHttpRequest Processor (получение callback)
```
Properties:
  Base Path: /nifi-api/flow-monitoring/callback
  Default Media Type: application/json
  
On Success:
  → LogResult (LogAttribute)
  → UpdateDatabase (PutDatabaseRecord)
  → NotifyTeam (PublishKafka / SendEmail)
```

## Примеры использования

### Сценарий 1: Ежедневная обработка данных
```
Schedule: 0 2 * * * (каждый день в 2:00)
Max Wait: 4 часа
Check Interval: 10 секунд
Empty Checks: 5

Результат: Логирование времени выполнения, алерт при таймауте
```

### Сценарий 2: ETL пайплайн с зависимостями
```
Step 1: Extract → Monitor → Complete
                           ↓
Step 2: Transform ← Trigger by callback
                     ↓
Step 3: Load ← Trigger by callback
```

### Сценарий 3: Параллельные потоки
```
Process Group A → Monitor Session A → Callback A
Process Group B → Monitor Session B → Callback B
Process Group C → Monitor Session C → Callback C

Все сессии независимы, работают параллельно
```

## Настройка параметров

### maxWaitTimeMs
- Короткие потоки (< 5 мин): 300000 (5 мин)
- Средние потоки (5-30 мин): 1800000 (30 мин)
- Длинные потоки (> 30 мин): 3600000+ (1+ час)

### checkIntervalMs
- Быстрая реакция: 1000-2000 мс
- Стандартный: 5000 мс
- Экономия ресурсов: 10000-30000 мс

### consecutiveEmptyChecksRequired
- Burst паттерны: 5-10 проверок
- Плавная обработка: 3 проверки
- Критичные данные: 10+ проверок

## Заключение

Реализованный подход обеспечивает:
✅ Архитектурно правильную интеграцию Spring Boot и NiFi
✅ Надёжное определение старта и завершения потоков
✅ Поддержку любых интервалов между запусками
✅ Обратную связь через callback механизм
✅ Богатую телеметрию для мониторинга и отладки
✅ Масштабируемость для параллельных потоков
