# NiFi REST Service

Spring Boot REST сервис для работы с Apache NiFi API.

## Требования

- Java 17+
- Maven 3.6+
- Apache NiFi (запущенный экземпляр)

## Конфигурация

Отредактируйте `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

nifi:
  api:
    base-url: http://localhost:8080/nifi-api  # URL вашего NiFi API
    timeout: 30000
    connect-timeout: 10000
```

## Запуск

```bash
cd nifi-rest-service
mvn spring-boot:run
```

Или создайте JAR файл:

```bash
mvn clean package
java -jar target/nifi-rest-service-1.0.0.jar
```

## API Endpoints

| Метод | Endpoint | Описание |
|-------|----------|----------|
| GET | `/api/nifi/health` | Проверка состояния сервиса |
| GET | `/api/nifi/system` | Получить информацию о системе NiFi |
| GET | `/api/nifi/process-groups` | Получить все группы процессов |
| GET | `/api/nifi/process-groups/{id}/processors` | Получить процессоры в группе |
| GET | `/api/nifi/process-groups/{id}/connections` | Получить соединения в группе |
| PUT | `/api/nifi/processors/{id}/start` | Запустить процессор |
| PUT | `/api/nifi/processors/{id}/stop` | Остановить процессор |
| GET | `/api/nifi/flow/status` | Получить статистику потока |

## Примеры использования

### Проверка здоровья сервиса
```bash
curl http://localhost:8080/api/nifi/health
```

### Получить информацию о системе NiFi
```bash
curl http://localhost:8080/api/nifi/system
```

### Получить все группы процессов
```bash
curl http://localhost:8080/api/nifi/process-groups
```

### Получить процессоры в группе
```bash
curl http://localhost:8080/api/nifi/process-groups/{processGroupId}/processors
```

### Запустить процессор
```bash
curl -X PUT http://localhost:8080/api/nifi/processors/{processorId}/start
```

### Остановить процессор
```bash
curl -X PUT http://localhost:8080/api/nifi/processors/{processorId}/stop
```

### Получить статистику потока
```bash
curl http://localhost:8080/api/nifi/flow/status
```

## Структура проекта

```
nifi-rest-service/
├── src/main/java/com/example/nifi/
│   ├── NifiRestServiceApplication.java    # Главный класс приложения
│   ├── config/
│   │   ├── NifiApiProperties.java         # Конфигурация NiFi API
│   │   └── HttpClientConfig.java          # Конфигурация HTTP клиента
│   ├── controller/
│   │   └── NifiController.java            # REST контроллер
│   ├── service/
│   │   └── NifiService.java               # Сервис для работы с NiFi API
│   └── model/
│       └── ProcessGroupInfo.java          # Модель данных
├── src/main/resources/
│   └── application.yml                    # Конфигурация приложения
└── pom.xml                                # Maven зависимости
```

## Примечания

- Убедитесь, что NiFi запущен и доступен по указанному URL
- Для доступа к NiFi API может потребоваться аутентификация (в текущей версии не реализована)
- Все endpoints возвращают JSON формат
