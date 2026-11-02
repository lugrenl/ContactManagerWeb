# ContactManagerWeb

Thymeleaf-веб-клиент для проекта ContactManager. Клиент предоставляет браузерный интерфейс для регистрации, входа, личной адресной книги, CSV-импорта и административного раздела, а все данные получает из отдельного REST-бэкенда.

## Архитектура

- `ContactManager` — REST-бэкенд на Spring Boot, Spring Security, Hibernate и PostgreSQL.
- `ContactManagerWeb` — отдельное Spring Boot + Thymeleaf-приложение.
- Браузер работает только с веб-клиентом (`http://localhost:8081`).
- Серверная часть веб-клиента обращается к REST API бэкенда (`http://localhost:8080`) через Spring `RestClient`.
- После входа JWT хранится в серверной `HttpSession` веб-клиента и передаётся в API явным заголовком `Authorization: Bearer ...` для конкретного запроса. В HTML, URL, JavaScript и логи JWT не попадает.
- PostgreSQL нужен только бэкенду. У веб-клиента собственной БД нет.
- Бэкенд и веб-клиент запускаются раздельно: клиент не запускает API и не содержит его бизнес-логики.

## Требования

| Компонент | Требование |
|---|---|
| Java | 21 для `ContactManagerWeb` (toolchain); бэкенд проверялся на OpenJDK 21, по его README достаточно Java 17+ |
| Gradle | Wrapper 8.13 в обоих проектах |
| PostgreSQL | локальный сервер; проверено на PostgreSQL 17.2, документация бэкенда допускает 14+ |
| Maven Central | сетевой доступ при первой сборке зависимостей |
| Браузер | любой современный браузер для демонстрации UI |
| Docker | не требуется и не используется |

## Локальный запуск

Ниже команды приведены для Windows PowerShell. Рабочая директория указана для каждого шага. Значения в угловых скобках замените своими; не коммитьте пароли и секреты.

### 1. Создать отдельную демонстрационную БД

Используйте **новую локальную БД**, например `contactmanager_demo`. Не используйте production-данные и `contactmanager_stage5_check`.

```powershell
# PostgreSQL bin должен быть в PATH; иначе используйте полный путь к createdb/psql
$env:PGPASSWORD = "<пароль-пользователя-postgres>"
createdb -h localhost -U <db-user> contactmanager_demo
```

Если `createdb`/`psql` не в PATH, выполните те же действия через pgAdmin или укажите полный путь к утилитам PostgreSQL.

### 2. Подготовить схему

Бэкенд работает с `hibernate.hbm2ddl.auto=validate` и **не создаёт схему при старте**.

Для **пустой демонстрационной БД** используйте прилагаемую схему, соответствующую текущим entity mappings бэкенда (`User`, `Contact`, `RevokedToken`):

```powershell
# Рабочая директория: C:\Users\ad\IdeaProjects\ContactManagerWeb
psql -h localhost -U <db-user> -d contactmanager_demo -v ON_ERROR_STOP=1 `
  -f .\docs\demo-schema.sql
```

`docs/demo-schema.sql` — только схема для локальной демонстрации. Она проверена Hibernate `validate` на временной PostgreSQL. Это не production-миграция; не применяйте её к существующей рабочей БД.

Если у вас уже есть совместимая локальная БД, не пересоздавайте её. Для обновления старой БД используйте процедуру из `C:\Users\ad\IdeaProjects\ContactManager\README.md`: резервная копия, `Add_unique_users_name.sql`, `Add_revoked_tokens.sql`, диагностика `Diagnose_contacts_migration.sql` и при необходимости `Migrate_contacts_to_personal_owner.sql`. Не применяйте миграции повторно к уже актуальной схеме и не используйте исторический `Basic_schema.sql` как готовую production-схему.

### 3. Задать переменные окружения бэкенда

Рекомендуется задавать секреты через окружение, а не хранить их в файлах проекта.

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/contactmanager_demo"
$env:SPRING_DATASOURCE_USERNAME = "<db-user>"
$env:SPRING_DATASOURCE_PASSWORD = "<db-password>"
$env:JWT_SECRET = "<случайная строка не короче 32 символов>"
$env:ADMIN_USERNAME = "<имя-демо-администратора>"
$env:ADMIN_PASSWORD = "<пароль-демо-администратора>"
$env:ADMIN_EMAIL = "<email-демо-администратора>"
```

- `JWT_SECRET` обязателен; используйте уникальную случайную строку длиной не менее 32 символов.
- Начальный администратор создаётся только если заданы **оба** параметра `ADMIN_USERNAME` и `ADMIN_PASSWORD`. Если не задан ни один, приложение запустится без автоматического администратора; если задан только один — старт завершится ошибкой.
- `ADMIN_EMAIL` необязателен, по умолчанию `admin@example.com`.
- Переменные `SPRING_DATASOURCE_*` переопределяют значения из `jdbc.properties`.

### 4. Запустить REST-бэкенд

Рабочая директория: `C:\Users\ad\IdeaProjects\ContactManager`.

Бэкенд **не является исполняемым Spring Boot JAR**: в его сборке нет плагина Spring Boot, задачи `bootRun` и исполняемого fat JAR. Не запускайте `build/libs/ContactManager-1.0-SNAPSHOT.jar` как готовое приложение.

**Основной способ — IDE:** откройте проект `ContactManager`, выберите runtime classpath и запустите главный класс `org.example.ContactManagerMain`. Переменные окружения из шага 3 задайте в Run Configuration.

**Проверенная альтернатива из PowerShell** — собрать классы и получить runtime classpath через временный init-скрипт Gradle:

```powershell
# Рабочая директория: C:\Users\ad\IdeaProjects\ContactManager
.\gradlew.bat classes --console=plain

$init = Join-Path $env:TEMP "cm-runtime-classpath.gradle"
@'
allprojects {
    afterEvaluate { project ->
        if (project.plugins.hasPlugin("java")) {
            project.tasks.register("printRuntimeClasspath") {
                doLast {
                    println project.sourceSets.main.runtimeClasspath.asPath
                }
            }
        }
    }
}
'@ | Set-Content -Encoding ASCII $init

$cp = (.\gradlew.bat -q -I $init printRuntimeClasspath --console=plain | Select-Object -Last 1)
java -cp "$cp" org.example.ContactManagerMain
```

Запускайте `java` из каталога `ContactManager`, чтобы рабочие пути и каталог логов оставались предсказуемыми. При старте Hibernate проверяет схему и не пересоздаёт её.

### 5. Проверить REST API

```powershell
Invoke-WebRequest http://localhost:8080/v3/api-docs -UseBasicParsing | Select-Object StatusCode
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

Корневой `GET http://localhost:8080/` возвращает `401` — это ожидаемое поведение защиты бэкенда, а не признак ошибки запуска.

### 6. Собрать и запустить веб-клиент

Рабочая директория: `C:\Users\ad\IdeaProjects\ContactManagerWeb`.

```powershell
.\gradlew.bat bootJar --console=plain
java -jar build\libs\contactmanager-web-0.0.1-SNAPSHOT.jar
```

При необходимости адрес API можно переопределить системным свойством (значение по умолчанию уже `http://localhost:8080`):

```powershell
java -Dcontactmanager.api.base-url=http://localhost:8080 `
  -jar build\libs\contactmanager-web-0.0.1-SNAPSHOT.jar
```

### 7. Адреса приложений

| Что | Адрес |
|---|---|
| Веб-клиент | `http://localhost:8081` |
| REST API | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| PostgreSQL | `localhost:5432` |

## Настройки и безопасность

- Адрес API задаётся свойством `contactmanager.api.base-url` в `src/main/resources/application.properties` и может быть переопределён при запуске JAR.
- Клиент и бэкенд запускаются отдельно, потому что клиент является самостоятельным приложением и не содержит серверной бизнес-логики. Остановка клиента не останавливает API и наоборот.
- JWT нельзя передавать в HTML, URL, hidden-поля, JavaScript, `localStorage` или `sessionStorage`: он хранится только в серверной HTTP-сессии. Токены, пароли и заголовки `Authorization` не должны попадать в логи.
- CSRF-защита включена для POST-форм. Для multipart-загрузки CSV используется JavaScript `fetch()` с CSRF-заголовком, имя которого берётся из конфигурации Spring Security.
- Одноразовые тестовые БД нужны для автоматических и интеграционных проверок. Для обычной демонстрации используйте отдельную постоянную локальную БД, чтобы данные не исчезали при перезапуске.
- Не коммитьте `JWT_SECRET`, пароли БД и администратора. Используйте переменные окружения или локальные файлы вне Git.
- Локальные cookie веб-клиента настроены с `HttpOnly` и `SameSite=Lax`; `Secure` отключён только для локального HTTP.

## Возможности веб-клиента

- Регистрация, вход и выход; JWT хранится в серверной сессии.
- Просмотр, создание, редактирование и удаление личных контактов.
- Конфликт обновления (`409`) отображается без потери непарольных данных.
- CSV-импорт через `/contacts/import`: `multipart/form-data`, часть `file`, UTF-8 BOM, кавычки и запятые внутри кавычек; лимит 1 MB на файл и 2 MB на запрос.
- Административный список пользователей, изменение email, роли и пароля, удаление пользователей.
- Административный список всех контактов с сопоставлением владельцев по `ownerId`.
- Административные ссылки видны только пользователю с ролью администратора; прямой доступ к `/admin/**` дополнительно блокируется interceptor-проверкой.

Не реализованы: создание пользователя администратором, редактирование или удаление чужих контактов, поиск, пагинация.
