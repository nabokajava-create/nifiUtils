# Алгоритм определения завершения работы потока NiFi

## Обзор

Реализован алгоритм определения завершения работы стартованного по расписанию потока NiFi на основе анализа очередей в процессорной группе.

## Архитектура

### Компоненты

1. **FlowCompletionMonitorService** - основной сервис мониторинга
2. **FlowCompletionController** - REST контроллер для управления мониторингом
3. **Модели данных**:
   - `FlowCompletionStatus` - статус завершения потока
   - `QueueStatus` - статус очереди
   - `FlowCompletionConfig` - конфигурация мониторинга

## Алгоритм работы

### Основной принцип

Поток считается завершённым, когда выполняются следующие условия:
1. **Все очереди пусты** - количество FlowFile и размер данных во всех соединениях равны нулю (или ниже пороговых значений)
2. **Нет активных процессоров** - все процессоры в процессорной группе остановлены (опционально)
3. **Стабильность состояния** - очереди остаются пустыми в течение N последовательных проверок

### Параметры конфигурации

```java
{
    "maxWaitTimeMs": 3600000,          // Максимальное время ожидания (1 час)
    "checkIntervalMs": 5000,           // Интервал между проверками (5 секунд)
    "emptyQueueThreshold": 0,          // Порог количества файлов для "пустой" очереди
    "emptyQueueSizeThreshold": 0,      // Порог размера очереди в байтах
    "consecutiveEmptyChecksRequired": 3, // Количество последовательных пустых проверок
    "considerOnlyActiveProcessors": true, // Учитывать активные процессоры
    "ignoredQueuePatterns": []         // Паттерны игнорируемых очередей (regex)
}
```

### Шаги алгоритма

#### 1. Получение данных из NiFi API
- Запрос к `/process-groups/{id}/connections` - получение информации об очередях
- Запрос к `/process-groups/{id}/processors` - получение информации о процессорах

#### 2. Анализ очередей
Для каждого соединения извлекаются:
- `flowFileCount` - количество файлов в очереди
- `queueSize` - общий размер данных в байтах
- `maxQueueSize` - максимальный размер очереди
- Имена источника и назначения

#### 3. Оценка состояния
```
ЕСЛИ (все очереди пусты И 
      (нет активных процессоров ИЛИ не требуется их проверка))
ТО поток завершён
```

#### 4. Подтверждение завершения
- Счётчик последовательных пустых проверок увеличивается
- При достижении порога (`consecutiveEmptyChecksRequired`) поток считается завершённым
- При появлении данных в очередях счётчик сбрасывается

#### 5. Мониторинг с таймаутом
- Проверки выполняются циклически с заданным интервалом
- При превышении `maxWaitTimeMs` генерируется исключение TimeoutException

## REST API

### Однократная проверка статуса
```
POST /api/nifi/flow-monitor/{processGroupId}/check
Content-Type: application/json

{
    "maxWaitTimeMs": 3600000,
    "checkIntervalMs": 5000,
    "emptyQueueThreshold": 0,
    "emptyQueueSizeThreshold": 0,
    "consecutiveEmptyChecksRequired": 3,
    "considerOnlyActiveProcessors": true
}
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
    "activeProcessorCount": 3,
    "totalProcessorCount": 10,
    "queueStatuses": [...],
    "checkTimestamp": 1234567890,
    "message": "Поток выполняется: 5 файлов в очередях, 3/10 активных процессоров"
}
```

### Запуск асинхронного мониторинга
```
POST /api/nifi/flow-monitor/{processGroupId}/start-monitoring
Content-Type: application/json

{ ...конфигурация... }
```

**Ответ:**
```json
{
    "status": "STARTED",
    "message": "Мониторинг запущен",
    "processGroupId": "abc123...",
    "config": {
        "maxWaitTimeMs": 3600000,
        "checkIntervalMs": 5000,
        "consecutiveEmptyChecksRequired": 3
    }
}
```

### Проверка статуса мониторинга
```
GET /api/nifi/flow-monitor/{processGroupId}/status
```

**Ответ (мониторинг выполняется):**
```json
{
    "processGroupId": "abc123...",
    "monitoring": true,
    "completed": false,
    "message": "Мониторинг выполняется"
}
```

**Ответ (мониторинг завершён):**
```json
{
    "processGroupId": "abc123...",
    "monitoring": true,
    "completed": true,
    "status": {
        "completed": true,
        "status": "COMPLETED",
        "message": "Все очереди пусты в течение 15 секунд"
    },
    "message": "Мониторинг завершен"
}
```

### Остановка мониторинга
```
POST /api/nifi/flow-monitor/{processGroupId}/stop
```

### Список активных мониторингов
```
GET /api/nifi/flow-monitor/active
```

## Статусы потока

| Статус | Описание |
|--------|----------|
| `COMPLETED` | Поток завершён: очереди пусты, нет активных процессоров |
| `RUNNING` | Поток выполняется: есть данные в очередях или активные процессоры |
| `STOPPED` | Все процессоры остановлены, но могут быть данные в очередях |
| `IDLE` | Нет данных в очередях, но процессоры ещё работают |
| `TIMEOUT` | Превышено максимальное время ожидания |
| `ERROR` | Произошла ошибка при проверке |

## Примеры использования

### Пример 1: Синхронная проверка
```bash
curl -X POST http://localhost:8080/api/nifi/flow-monitor/abc123/check \
  -H "Content-Type: application/json" \
  -d '{
    "consecutiveEmptyChecksRequired": 5,
    "checkIntervalMs": 2000
  }'
```

### Пример 2: Асинхронный мониторинг с веб-хуком
```bash
# Запуск мониторинга
MONITOR_ID=$(curl -X POST http://localhost:8080/api/nifi/flow-monitor/abc123/start-monitoring \
  -H "Content-Type: application/json" \
  -d '{"maxWaitTimeMs": 7200000}' | jq -r '.processGroupId')

# Периодическая проверка статуса
while true; do
  STATUS=$(curl -s http://localhost:8080/api/nifi/flow-monitor/$MONITOR_ID/status)
  COMPLETED=$(echo $STATUS | jq -r '.completed')
  
  if [ "$COMPLETED" = "true" ]; then
    echo "Поток завершён!"
    echo $STATUS | jq '.status'
    
    # Отправка уведомления
    curl -X POST https://my-webhook.com/notify \
      -H "Content-Type: application/json" \
      -d "{\"event\": \"flow_completed\", \"groupId\": \"$MONITOR_ID\"}"
    
    break
  fi
  
  sleep 10
done
```

### Пример 3: Интеграция со Spring-приложением
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
            // Обработка завершённого потока
        }
    } catch (TimeoutException e) {
        log.error("Таймаут ожидания потока", e);
        // Обработка таймаута
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Мониторинг прерван", e);
    }
}
```

## Рекомендации по настройке

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

### Для потоков с фоновыми процессами
```json
{
    "considerOnlyActiveProcessors": false,
    "emptyQueueThreshold": 0,
    "consecutiveEmptyChecksRequired": 5
}
```

## Обработка ошибок

Сервис обрабатывает следующие сценарии:
- Недоступность NiFi API - возвращается статус `ERROR`
- Таймаут запросов - используется настроенный timeout HTTP-клиента
- Прерывание мониторинга - корректная очистка ресурсов
- Утечка памяти - ограничение на количество одновременных мониторингов

## Метрики и логирование

Сервис логирует:
- Начало и окончание мониторинга
- Результаты каждой проверки
- Изменения состояния очередей
- Ошибки и исключения

Уровни логирования:
- `INFO` - основные события мониторинга
- `DEBUG` - детали каждой проверки
- `WARN` - предупреждения (таймауты, близкие к пределу значения)
- `ERROR` - ошибки подключения и обработки
