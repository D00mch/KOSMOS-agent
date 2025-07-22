# Пишем агента на Kotlin: KOSMOS

Интернет завален реализациями на Питоне, но иногда удобнее поразбираться с технологиями на своём основном языке. Для меня это Kotlin.

Если вы программист, наверняка к вам приходят знакомые и предлагают писать агентов. Реализовав простой помощник по написанию кода, вы поймете, что задача из себя представляет.

Статья обещает соблюдать два принципа, упрощающих восприятие:
- Движение от частного к общему, потому что легче воспринимать примеры, чем абстракцию.
- Быстрая обратная связь, как с [REPL](https://en.wikipedia.org/wiki/Read%E2%80%93eval%E2%80%93print_loop).

Агента реализуем так, чтобы легко было заменить лежащую в основе LLM. Посмотрим, как отличается работа при использовании REST API в сравнении с SDK, пощупаем Гигачат и Anthropic.

Ах да, 🪐 KOSMOS — акроним. Kotlin Open Synthetic Mind Orbiting System.

## Что такое агент

Если попросить LLM умножить 2 больших числа, она ошибется. Решение — дать ей калькулятор. LLM с калькулятором — это уже агент.

В общем случае агент — это компьютерная программа, использующая возможности LLM для решения задач с помощью других программ.

Продвинутые агенты могут иметь долгосрочную память (векторная база данных, [RAG](https://habr.com/ru/companies/raft/articles/791034/)), хитрые промпты для рефлексии и самокритики.

```
                              ┌──────────────────────┐
                              │    Short-term mem    │
                              ├──────────────────────┤
                              │    Long-term mem     │
                              └───────────▲──────────┘
                                          │
                                  ┌───────┴───────┐
                                  │    Memory     │
                                  └───────▲───────┘
                                          │
┌───────────────────┐                     │
│   Calendar()      │                     │
├───────────────────┤                     │
│   Calculator()    │                     │
├───────────────────┤                     │
│ CodeInterpreter() │    ┌───────────────────────────────────┐
├───────────────────◀────│              Agent                │
│     Search()      │    └─────────────────────┬─────────────┘
├───────────────────┤                          │
│      ...more      │                          │
└───────────────────┘                          │
                                               │
                                         ┌─────▼─────┐
                                         │ Planning  │
                                         └─────┬─────┘
                                               │
                                      ┌────────▼───────────────┐
                                      │ Reflection │ Self-crit │
                                      │ Chain-of-thoughts      │
                                      │ Subgoal-decomposition  │
                                      └────────────────────────┘
```

## Как работают агенты: пример в чате

Откройте любой доступный вам LLM-чат и напишите:

```
Если я попрошу сложить два числа, ты можешь вызывать калькалятор. Для этого напиши в json формате:
{
    "n1": number1,
    "n2": number2,
    "operation": "+"
}

И следующим сообщением получишь ответ.
А теперь сложи 22 и 33
```

Json объект и его описание — это tool (в терминах Anthropic, OpenAI, Deepseek) или [функция](https://developers.sber.ru/docs/ru/gigachat/guides/function-calling#rabota-s-sobstvennymi-funktsiyami) (в терминах Гигачат). В статье мы будем называть «тулы» функциями. Текстом выше мы дали понять LLM, что у нее есть функция «калькулятор».

Я пробовал с Deepseek, Qwen, ChatGpt, Гигачат — все ответили:

```json
{
    "n1": 22,
    "n2": 33,
    "operation": "+"
}
```

Такое сообщение легко парсится. Все что нам теперь нужно — выполнить операцию на калькуляторе и написать «55» в чат. LLM ответит что-то вроде:

> Сумма чисел 22 и 33 равна 55. 😊

## Как работают агенты: пример с API

Давайте попробуем притвориться агентом: сами будем вызывать LLM.

Для начала понадобится завести аккаунт [Гигачата](https://developers.sber.ru/portal/products/gigachat-api), получить ключ и записать его в переменные окружения:

```bash
export GIGA_KEY=<ключ>
```

Запросим токен, которого хватит на 30 минут:

```bash
curl -L -X POST 'https://ngw.devices.sberbank.ru:9443/api/v2/oauth' \
-H 'Content-Type: application/x-www-form-urlencoded' \
-H 'Accept: application/json' \
-H 'RqUID: 9aa1df35-33f6-43fc-b92e-1e61384c8660' \
-H "Authorization: Basic $GIGA_KEY" \
--data-urlencode 'scope=GIGACHAT_API_PERS' 
```

Если запрос не выполняется с ошибками сертификата, попробуйте передать флаг `-k` или пропишите сертификаты Сбера по [инструкции](https://www.sberbank.com/ru/certificates).

В ответ придет токен, который тоже для удобства положим в переменные окружения:
```bash
export GIGA_TOKEN=token.from.oauth
```

Теперь можно отправить первые сообщения ([документация](https://developers.sber.ru/docs/ru/gigachat/api/reference/rest/post-chat)).

Зададим первый вопрос Гигачату о том, какие файлы лежат в кодовой базе проекта.

```bash
curl -L 'https://gigachat.devices.sberbank.ru/api/v1/chat/completions' \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
-H "Authorization: Bearer $GIGA_TOKEN" \
-d '{
  "model": "GigaChat-Max",
  "messages": [
    {
      "role": "system",
      "content": "Ты — ассистент, помогающий писать код"
    },
    {
      "role": "user",
      "content": "Что лежит в директории текущего проекта?"
    }
  ],
  "function_call": "auto",
  "functions": [
    {
        "name": "ListFiles",
        "description": "Запускаем ls команду по текущему пути. Точка (.) означает текущую папку",
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                "type": "string",
                "description": "Путь к директории, файлы которой покажем"
             }
           }
        }
    }
  ]
}'
```

Мы указали, что у LLM есть функция «ListFiles», требующая `path` в качестве параметра, и спросили, что лежит в директории проекта.
В ответ Гигачат запрашивает `function_call`:

```json
{
  "choices": [
    {
      "message": {
        "content": "",
        "role": "assistant",
        "function_call": {
          "name": "ListFiles",
          "arguments": {
            "path": "."
          }
        },
        "functions_state_id": "e379e132-2cf8-4ce1-8545-c9c94cbebb1b"
      },
      "index": 0,
      "finish_reason": "function_call"
    }
  ],
  "created": 1752855939,
  "model": "GigaChat-Max:2.0.28.2",
  "object": "chat.completion",
  "usage": {
    "prompt_tokens": 88,
    "completion_tokens": 23,
    "total_tokens": 111,
    "precached_prompt_tokens": 3
  }
}
```

Добавляем запрос и результат вызова фукнции в messages. Ожидаем получить ответ, основанный на этом вызове. Не забудьте проставить вернувшийся `functions_state_id`:

```bash
curl -L 'https://gigachat.devices.sberbank.ru/api/v1/chat/completions' \
-H 'Content-Type: application/json' \
-H 'Accept: application/json' \
-H "Authorization: Bearer $GIGA_TOKEN" \
-d '{
  "model": "GigaChat-Max",
  "messages": [
    {
      "role": "system",
      "content": "Ты — ассистент, помогающий писать код"
    },
    {
      "role": "user",
      "content": "Что лежит в директории текущего проекта?"
    },
    {
      "role":"assistant",
      "content": 
        "{\"name\": \"ListFiles\", \"arguments\": {\"path\": \".\"}} ",
      "functions_state_id": "e379e132-2cf8-4ce1-8545-c9c94cbebb1b"
    },
    {
      "role": "function",
      "content": "[\"README.md\", \"src/\", \"src/main.kt/\"]",
      "name": "ListFiles" 
    }
  ],
  "function_call": "auto",
  "functions": [
    {
        "name": "ListFiles",
        "description": "Запускаем ls команду по текущему пути. Точка (.) означает текущую папку",
        "parameters": {
            "type": "object",
            "properties": {
                "path": {
                "type": "string",
                "description": "Путь к директории, файлы которой покажем"
             }
           }
        }
    }
  ]
}'
```

Ответ пришел, как мы и ожидали:

```json
{
  "choices": [
    {
      "message": {
        "content": "В текущей директории проекта находятся следующие элементы:\n- README.md\n- src/\n- src/main.kt",
        "role": "assistant",
        "functions_state_id": "de68b8a0-c2c7-448b-af8b-8a1b652fccd5"
      },
      "index": 0,
      "finish_reason": "stop"
    }
  ],
  "created": 1752856961,
  "model": "GigaChat-Max:2.0.28.2",
  "object": "chat.completion",
  "usage": {
    "prompt_tokens": 150,
    "completion_tokens": 26,
    "total_tokens": 176,
    "precached_prompt_tokens": 3
  }
}
```

# Написание агента

Понадобится окружение, где мы сможем запустить Kotlin код. Можно создать новый Kotlin-проект в Intellij IDEA или взять скелет из [моего репозитория ko-agent](https://github.com/D00mch/ko-agent/tree/skeleton).

```bash
tree -I '.*|.git' --prune
.
├── gradle...
├── gradle.properties
├── gradlew
├── settings.gradle.kts
├── build.gradle.kts
└── src
    ├── main
    │   └── kotlin
    │       ├── Main.kt
    │       └── tool
    │           ├── files
    │           │   └── ToolListFiles.kt
    │           └── ToolSetup.kt
    └── test
        ├── kotlin
        │   └── tool
        │       └── ToolTest.kt
        └── resources
            ├── directory
            │   └── file.txt
            └── test.txt
```

Из зависимостей не забудьте добавить корутины в build.gradle:

```kotlin
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Coroutines}")
    testImplementation(kotlin("test"))
}
```

## Реализация фукнций (tools)

Передавая функцию Гигачату, мы думали о том, как объяснить ее чату и какой алиас ей дать. Так что нам понадобятся имя, описание и сама функция:

```kotlin
interface ToolSetup<Input> {
    val name: String
    val description: String
    operator fun invoke(input: Input): String
}
```

Кроме того, агенту нужно будет рассказать и о параметрах функции. У `ListFiles` есть `path`. Отложим решение о том, как предоставить эту информацию, на потом.

Для полноценного функционирования агента-помощника в написании кода нам понадобятся следующие функции:

- Чтение файла (`echo`)
- Перечисление файлов (`ls`)
- Изменение файла (`sed -e`)
- Создание файла (`>>`)
- Удаление файла (`rm`)
- Поиск текста в файле (`find`)

Просто дать терминал мы не хотим, потому что сложнее будет обеспечить безопасность.

## Пишем первую функцию — ListFiles

Реализуем `ListFiles`, как в примере использования API Гигачата:
```kotlin
object ToolListFiles : ToolSetup<ToolListFiles.Input> {
    override val name = "ListFiles"
    override val description = "Runs bash ls command at a given path. Dot (.) means current directory"

    override fun invoke(input: Input): String {
        TODO()
    }

    data class Input(val path: String = ".")
}
```

Обратите внимание, мы дали описание на английском. Считается, что LLM лучше работают с английскими промптами. Почему так? На английском больше данных для обучения, и по количеству токенов английский экономнее (нет падежей).

Начнем с написания теста.

```kotlin
@Test
fun `test ToolListFiles`() {
    val input = ToolListFiles.Input("src/test/resources")
    val resources = ToolListFiles()
    assertEquals("[directory/,directory/file.txt,test.txt]", resources)
    println(resources)
}
```

И в `src/test/resources` положим папку `directory` и два файла: `test.txt` и `directory/file.txt`. Попробуйте запустить тест и убедиться, что пока что tool не работает.

```bash
./gradlew test # не забудьте проставить JAVE_HOME, либо просто запускайте проект из IDEA
```
Результат:
```
> Task :test FAILED
ToolTest > test ToolListFiles() FAILED
    kotlin.NotImplementedError at ToolTest.kt:11
```

Набросаем наивную реализацию:
```kotlin
override fun invoke(input: Input): String {
    val base = File(input.path)
    val files = base.list()
    return files.joinToString(",", prefix = "[", postfix = "]")
}
```
Запускаем тест:
```bash
Expected :[directory/,directory/file.txt,test.txt]
Actual   :[directory,test.txt]
```

Осталось поддержать поиск вложенных файлов. Kotlin предоставляем отличную функцию `File.walkTopDown` ([DFS](https://en.wikipedia.org/wiki/Depth-first_search) по файлам), возвращающую `sequence`. Т.е. можно пользоваться [преобразованием коллекций](https://kotlinlang.org/docs/collection-transformations.html) без накладных расходов в виде создания по новой коллекции на каждом операторе:

```kotlin
override fun invoke(input: Input): String {
    val base = File(input.path)
    val files = base.walkTopDown()
        .filter { it != base }
        .map {
            val relPath = it.relativeTo(base).path
            if (it.isDirectory) "$relPath/" else relPath
        }
    return files.joinToString(",", prefix = "[", postfix = "]")
}
```

Тест должен быть пройден. Функцию можно улучшить, добавив еще параметры с исключениями. Например, мы не хотим тратить токены на отправку данных из папок .git или .idea. Реализацию фичи оставлю на совести читателя.

## Пишем функцию чтения файла

Опять начнем с теста. Допишем в `src/test/resources/test.txt` «Test content» с новой строкой.

```kotlin
@Test
fun `test ToolReadFile`() {
    println(File("src/test/resources/test.txt").readText())
    val result = ToolReadFile(ToolReadFile.Input("src/test/resources/test.txt"))
    assertEquals("Test content\n", result) // \n для новой строки
}
```

В реализации всё предсказуемо:
```kotlin
object ToolReadFile : ToolSetup<ToolReadFile.Input> {
    override val name = "ReadFile"
    override val description = "Retrieve the contents of a specified file using a relative path. " +
            "Use this to read a file's contents. Avoid using it with directory paths"

    override fun invoke(input: Input): String {
        val path = input.path
        val file = File(path)
        return file.readText()
    }

    data class Input(val path: String)
}
```

## Допишем остальные функции работы с файловой системой

Следующий тест создает файл, меняет его, ищет текст в файлах и удаляет файл:

```kotlin
@Test
fun `test ToolNewFile, ToolModifyFile, ToolFindTextInFiles, ToolDeleteFile lifecycle`() {
    val content = "Test"
    val resources = "src/test/resources"
    val newFileName = "${UUID.randomUUID()}.txt"
    val path = "$resources/$newFileName"

    // create new file
    ToolNewFile(ToolNewFile.Input(path, text = content))
    val fileContent = ToolReadFile(ToolReadFile.Input(path))
    assertEquals(content, fileContent)

    // modify new
    val newContent = "New"
    ToolModifyFile(ToolModifyFile.Input(path, oldText = content, newText = newContent))

    // find
    val findResult = ToolFindTextInFiles(ToolFindTextInFiles.Input(path = resources, newContent))
    assertEquals("[$newFileName]", findResult)

    // delete
    ToolDeleteFile(ToolDeleteFile.Input(path))
    assertThrows<IOException> { ToolReadFile(ToolReadFile.Input(path)) }
}
```
Реализация недостающих функций:

```kotlin
object ToolNewFile : ToolSetup<ToolNewFile.Input> {
    override val name = "NewFile"
    override val description = "Creates a new file at the given path with the provided content."

    override fun invoke(input: Input): String {
        val file = File(input.path)
        file.parentFile?.mkdirs()
        file.writeText(input.text)
        return "File created at ${input.path}"
    }

    data class Input(
        val path: String,
        val text: String
    )
}


object ToolModifyFile : ToolSetup<ToolModifyFile.Input> {
    override val name = "EditFile"
    override val description = "Replace text in a file. Replaces 'old_text' with 'new_text' in the specified file. "

    override fun invoke(input: Input): String {
        val file = File(input.path)
        val content = file.readText()
        val newContent = content.replace(input.oldText, input.newText)
        file.writeText(newContent)
        return "OK"
    }

    data class Input(
        val path: String,
        val oldText: String,
        val newText: String,
    )
}

object ToolFindTextInFiles : ToolSetup<ToolFindTextInFiles.Input> {
    override val name = "FindTextInFiles"
    override val description = "Search for a specific text across all files in a directory (recursively) " +
            "and return matching file paths."

    override fun invoke(input: Input): String {
        val baseDir = File(input.path)
        val matchedFiles = baseDir.walkTopDown()
            .filter { it.isFile && it.readText().contains(input.text) }
            .map { it.relativeTo(baseDir).path }
            .toList()
        return matchedFiles.joinToString(",", prefix = "[", postfix = "]")
    }

    data class Input(
        val path: String = ".",
        val text: String,
    )
}

object ToolDeleteFile : ToolSetup<ToolDeleteFile.Input> {
    override val name = "DeleteFile"
    override val description = "Deletes a file at the given path."

    override fun invoke(input: Input): String {
        val file = File(input.path)
        file.delete()
        return "File deleted at ${input.path}"
    }

    data class Input(val path: String)
}
```

Тест должны проходить. Если возникнут проблемы, можете посмотреть на проект [ko-agent](https://github.com/D00mch/ko-agent) и взять код оттуда.

## Подумаем о безопасности

Подустали? Давайте просыпаться. Ниже написан тест, который не стоит(!) запускать, пока вы не будете на 100% уверены в реализации:

```kotlin
class ToolSecurityTest {
    @Test
    fun `test delete file rejects paths outside project root`() {
        assertThrows<BadInputException> {
            ToolDeleteFile.invoke(ToolDeleteFile.Input("/"))
        }
    }
}
```

Рисковать или нет — дело читателя. Автор статьи все еще пишет, а значит, тест был пройден. Вот моя реализация:

```kotlin
object ToolDeleteFile : ToolSetup<ToolDeleteFile.Input> {
    // ...
    override fun invoke(input: Input): String {
        val file = File(input.path)
        FilesToolUtil.requirePathIsSave(file)
        file.delete()
        return "File deleted at ${input.path}"
    }
}

object FilesToolUtil {
    private val projectRoot = File(".").canonicalFile

    fun isPathSafe(file: File): Boolean {
        val canonicalPath = file.canonicalFile
        return canonicalPath.startsWith(projectRoot)
    }

    @Throws(BadInputException::class)
    fun requirePathIsSave(file: File) {
        if (!isPathSafe(file)) {
            throw BadInputException("Access denied: File path must be within project directory")
        }
    }
}
```

## Реализация чата с агентом-попугаем

Чат с агентом — это просто:

```kotlin
while (true) {
    print("> ")
    val input = kotlin.io.readLine() ?: break
    if (input.lowercase() == "exit") break
    println(input)
    // тут будет отправка сообщение к ИИ и обработка ответа
}
```

Не хочется сразу завязываться на конкретную реализацию LLM, поэтому предлагаю вынести общение в абстракцию. Так будет выглядеть Flow сообщений пользователя:

```kotlin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

suspend fun main() {
    val agent = ParrotAgent(userInputFlow())
    agent.run().collect { text -> print(text) }
}

/** Агент, повторяющий сообщение */
class ParrotAgent(private val userMessages: Flow<String>) {
    fun run(): Flow<String> = userMessages
}

private fun userInputFlow(): Flow<String> = flow {
    println("Type `exit` to quit")
    while (true) {
        println("> ")
        val input = readLine() ?: break
        if (input.lowercase() == "exit") break
        emit(input)
        println("\n")
    }
}
```

Попробуйте запустить и пообщаться с первым агентом.

## Гигачат по REST API

Проверьте, что ключ Гигачата доступен из переменных окружения:

```kotlin
fun main() {
    val gigaKey = System.getenv("GIGA_KEY")
    println(gigaKey)
}
```

Если вы его проставили, а печатаеся null, переоткройте Intellij IDEA.

Напишем код на запрос токена с популярной библиотекой [Ktor](https://ktor.io/docs/client-create-new-application.html). Нам понадобится прописать зависимости в build.gradle:

```kotlin
dependencies {
    ...
    // ktor
    implementation("io.ktor:ktor-client-core:${Versions.Ktor}")
    implementation("io.ktor:ktor-client-cio:${Versions.Ktor}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.Ktor}")
    implementation("io.ktor:ktor-client-auth:${Versions.Ktor}")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.Ktor}")
    implementation("io.ktor:ktor-serialization-jackson:${Versions.Ktor}")
    ...
}
```
И сам код на запрос авторизации:

```kotlin
object GigaResponse {
    data class Token(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("expires_at") val expiresAt: Date
    )

object GigaAuth {
    suspend fun requestToken(apiKey: String): String {
        val client = HttpClient(CIO) {
            gigaDefaults()
        }
        val response = client.submitForm(
            url = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth",
            formParameters = Parameters.build {
                append("scope", "GIGACHAT_API_PERS")
            }
        ) {
            header("Content-Type", "application/x-www-form-urlencoded")
            header("Authorization", "Basic $apiKey")
        }.body<GigaResponse.Token>()

        client.close()
        return response.accessToken
    }
}
```

Настройки для клиента Ktor вынесли в функцию, которая нам еще пригодиться для клиента чата:
```kotlin
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.jackson.jackson
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.X509TrustManager

fun HttpClientConfig<CIOEngineConfig>.gigaDefaults() {
    this.defaultRequest {
        header(HttpHeaders.ContentType, "application/json")
        header(HttpHeaders.Accept, "application/json")
        header("RqUID", UUID.randomUUID().toString())
    }
    install(ContentNegotiation) {
        jackson { this.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) }
    }
    engine {
        https {
            // Наивная реализация TrustManager, чтобы доверять всем, включая Сбер.
            trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        }
    }
}
```

Проверяем, что токен запрашивается:
```kotlin
suspend fun main() {
    val gigaKey = System.getenv("GIGA_KEY")
    val gigaToken = GigaAuth.requestToken(gigaKey)
    println(gigaToken)
}
```

## Реализация функций чата

Опишем все необходимые DTO:

```kotlin
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

object GigaResponse {

    data class Token(
        @JsonProperty("access_token") val accessToken: String,
        @JsonProperty("expires_at") val expiresAt: Date
    )

    sealed interface Chat {
        data class Ok(val choices: List<Choice>, val created: Long, val model: String) : Chat
        data class Error(val status: Int, val message: String) : Chat
    }

    data class Choice(
        val message: Message,
        val index: Int,
        @JsonProperty("finish_reason")
        val finishReason: String
    )

    data class Message(
        val content: String,
        val role: GigaMessageRole,
        @JsonProperty("function_call")
        val functionCall: FunctionCall? = null,
        @JsonProperty("functions_state_id")
        val functionsStateId: String?
    )

    data class FunctionCall(
        val name: String,
        val arguments: Map<String, Any>
    )
}

object GigaRequest {
    data class Chat(
        val model: String = "GigaChat-Max",
        val messages: List<Message>,
        @JsonProperty("function_call")
        val functionCall: String = "auto",
        val functions: List<Function>? = null,
    )

    data class Message(
        val role: GigaMessageRole,
        val content: String, // Could be String or FunctionCall object
        @JsonProperty("functions_state_id")
        val functionsStateId: String? = null
    )

    data class Function(
        val name: String,
        val description: String,
        val parameters: Parameters
    )

    data class Parameters(
        val type: String,
        val properties: Map<String, Property>
    )

    data class Property(
        val type: String,
        val description: String? = null
    )
}

@Suppress("EnumEntryName")
enum class GigaMessageRole { system, user, assistant, function }
```

И API Гигачата:

```kotlin
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*

class GigaChatAPI(private val auth: GigaAuth) {
    private val client = HttpClient(CIO) {
        var token = "" // get form env, or cache, or db
        val gigaKey = System.getenv("GIGA_KEY")
        gigaDefaults()
        install(Auth) {
            bearer {
                loadTokens {
                    BearerTokens(token, "")
                }
                refreshTokens {
                    token = auth.requestToken(gigaKey)
                    BearerTokens(token, "")
                }
            }
        }
    }

    suspend fun message(body: GigaRequest.Chat): GigaResponse.Chat {
        val response = client.post("https://gigachat.devices.sberbank.ru/api/v1/chat/completions") {
            setBody(body)
        }
        return when {
            response.status.isSuccess() -> response.body<GigaResponse.Chat.Ok>()
            else -> response.body<GigaResponse.Chat.Error>()
        }
    }

    fun clear() = client.close()
}
```

Попробуем получить первый ответ от Гигачата, как мы делали руками через `curl`:

```kotlin
suspend fun main() {
    val chat = GigaChatAPI(GigaAuth)

    // временный код для демонстрации
    val response = chat.message(
        GigaRequest.Chat(
            messages = listOf(
                GigaRequest.Message(
                    role = GigaMessageRole.user,
                    content = "Help me find out what are the source files in the directory",
                )
            ),
            functions = listOf(
                GigaRequest.Function(
                    name = "ListFiles",
                    description = "Show the files in the current directory path",
                    parameters = GigaRequest.Parameters(
                        "object",
                        properties = mapOf(
                            "path" to GigaRequest.Property(
                                type = "string",
                                description = "Relative path to list files from"
                            )
                        )
                    )
                )
            )
        )
    )

    response.choices.forEach { (message, index, finishReason) ->
        println(message)
    }
}
```

После запуска должно напечататься что-то вроде:
```
Message(content=, role=assistant, functionCall=FunctionCall(name=ListFiles, arguments={'path':.}), functionsStateId=055e95ce-cbdf-46e7-ba22-6d3ad791f8c6)
```

## Подключаем функций

Помните, мы отложили на потом решение о том, как передавать метаданные о параметрах запроса?

```kotlin
object ToolListFiles : ToolSetup<ToolListFiles.Input> {
    override val name = "ListFiles"
    override val description = "Runs bash ls command at a given path. Dot (.) means current directory"

    override fun invoke(input: Input): String = TODO("...")

    data class Input(
        // Как бы нам передать в Гигачат "Relative path to list files from"?
        val path: String = "."
    )
}
```

Решений, как добавить метаданные, много, но самый идиоматичный — аннотации.

> Annotations are a means of attaching metadata to code. — [kotlinlang.org](https://kotlinlang.org/docs/annotations.html)

Можно использовать `@JsonPropertyDescription` из jackson, но для наглядности и независимости от сторонних библиотек предлагаю добавить свою:

```kotlin
@Target(AnnotationTarget.PROPERTY) // на property (val в data class)
@Retention(AnnotationRetention.RUNTIME) // достанем ее в Runtime
annotation class InputParamDescription(val value: String)
```

```kotlin
object ToolListFiles : ToolSetup<ToolListFiles.Input> {
    /* остальной код */

    data class Input(
        @InputParamDescription("Relative path to list files from")
        val path: String = "."
    )
}
```

Домашнее задание (и читателю, и автору) — реализовать проверку `Input` функций. Задача со звездочкой — сделать это в compile time. 

Остальные функции можете описать самостоятельно или скопировать с проекта [ko-agent](https://github.com/D00mch/ko-agent/tree/main/src/main/kotlin/tool/files).

Теперь нам нужен способ перевести имеющиеся функции в удобоваримый для Гигачата вариант, что-то вроде:

```kotlin
interface GigaToolSetup {
    val fn: GigaRequest.Function
    operator fun invoke(
        functionCall: GigaResponse.FunctionCall
    ): GigaRequest.Message
}
```

До реализации напишем тест на то, что мы хотели бы видеть:

```kotlin
class GigaToolTest {
    private val gigaJsonMapper = jacksonObjectMapper()

    @Test
    fun `test function name and parameters setup`() {
        val fn = ToolListFiles.toGiga().fn
        assertEquals(fn.name, "ListFiles")
        val jsonParams = gigaJsonMapper.writeValueAsString(fn.parameters)
        assertEquals(
            """
            {"type":"object","properties":{"path":{"type":"string","description":"Relative path to list files from"}}}
        """.trimIndent(),
            jsonParams
        )
    }

    @Test
    fun `test function invocation`() {
        val toolsMap: Map<String, GigaToolSetup> = listOf(ToolListFiles.toGiga()).associateBy { it.fn.name }

        val functionCall = GigaResponse.FunctionCall(
            name = "ListFiles",
            arguments = mapOf("path" to "src/test/resources"),
        )

        val result = toolsMap[functionCall.name]!!.invoke(functionCall)
        assertEquals(
            GigaRequest.Message(
                role = GigaMessageRole.function,
                content = """{"result":"[directory/,directory/file.txt,test.txt]"}""",
            ),
            result
        )
    }
}
```

Прочесть аннотации можно через рефлексию, но если сделать это только один раз на старте приложения, то несколько миллисекунд ни на что не повлияют.

Добавим зависимость:
```kotlin
dependencies {
    implementation(kotlin("reflect"))
}
```

```kotlin
val gigaJsonMapper = jacksonObjectMapper()

inline fun <reified Input> ToolSetup<Input>.toGiga(): GigaToolSetup {
    val toolSetup = this
    return object : GigaToolSetup {
        override val fn: GigaRequest.Function = GigaRequest.Function(
            name = toolSetup.name,
            description = toolSetup.description,
            parameters = GigaRequest.Parameters(
                "object",
                properties = HashMap<String, GigaRequest.Property>().apply {
                    val clazz = Input::class
                    for (kProperty: KCallable<*> in clazz.declaredMembers) {
                        val annotation = kProperty.findAnnotation<InputParamDescription>() ?: continue
                        val description = annotation.value
                        val type = kProperty.returnType.toString().substringAfterLast(".").lowercase()
                        val gigaProperty = GigaRequest.Property(type, description)
                        put(kProperty.name, gigaProperty)
                    }
                }
            )
        )

        override fun invoke(
            functionCall: GigaResponse.FunctionCall,
        ): GigaRequest.Message {
            return try {
                val input: Input = gigaJsonMapper.convertValue(functionCall.arguments, Input::class.java)
                val toolResult = toolSetup.invoke(input)
                val gigaResult = gigaJsonMapper.writeValueAsString(
                    mapOf("result" to toolResult)
                )
                GigaRequest.Message(
                    role = GigaMessageRole.function,
                    content = gigaResult,
                )
            } catch (e: Exception) {
                e.toGigaToolMessage()
            }
        }
    }
}

fun Exception.toGigaToolMessage(): GigaRequest.Message {
    return GigaRequest.Message(
        role = GigaMessageRole.function,
        content = """{"result": "${message ?: toString()}"}""",
    )
}
```

## Реализация агента

Нам осталось реализовать Агента. Так будет выглядеть первый упрощенный алгоритм:

```bash
┌─────────────────────────────── Loop 1-5 ───────────────────┐
│ User          Agent                                   LLM  │
│  |              |                                      |   │
│  |1. msg input  |                                      |   │
│  └─────────────▶|2. add msg into msgs                  |   │
│  |              |                                      |   │
│  |              | ┌──────────── Loop 3-5 ──────────────┐   │
│  |              | │                                    |   │
│  |              | │3. send(msgs, tools) ──────────────▶|   │
│  |              | │                                    |   │
│  |              | │4-1. plain text  ◀──────────────────|   │
│  |              | │      └─► print text                |   │
│  |              | │                                    |   │
│  |              | │4-2. function call ◀──────────────  |   │
│  |              | │               │                    |   │
│  |              | │               │ exec tool          |   │
│  |              | │               │ add result→msgs    |   │
│  |              | │               │                    |   │
│  |              | │◀───────────5. fn call? ───────────────▶│
│  |              | └────────────────────────────────────┘   │
│  |              |                                      |   │
│  |◀─────────────|                                      |   │
│  | 6. go to 1   |                                      |   │
└──┴──────────────┴──────────────────────────────────────┴───┘
```

1. Пользователь вводит сообщение.
2. Агент добавляет его в список сообщений.
3. Агент отправляет все сообщения + список функций в LLM.
4. LLM возвращает:
    - 4.1. Обычный текст → печатаем текст.
    - 4.2. Вызов функций → выполняем функции и добавляем результат в список сообщений.
5. Если был вызов функций, идем в шаг 3.
6. Возвращаемся к шагу 1.

Тизер — вот чего мне удалось добиться с простой реализацией, которую мы сейчас напишем:

```bash
Type `exit` to quit
> Whats inside the settings.gradle.kts file?

🪐:
 If there were any subprojects or additional configurations, they would also appear here. However, based on the information you've shared, these two sections (`plugins` and `rootProject`) are the only parts present.

> Can you update this file and add a comment of what it does?

🪐:
 😊🚀

> Can you remove this project?

🪐:
 😉📌ei

🪐:
Here's an overview of both options:
1. **Remove Only File:** Deletes the `settings.gradle.kts` file while keeping other project components intact.
2. **Remove Entire Project:** Removes everything related to the project, including source code, resources, etc., assuming you're okay with losing data permanently.

> exit
```

Код агента c комментариями.

```kotlin
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class GigaAgent(
    private val userMessages: Flow<String>,
    private val api: GigaChatAPI,
    private val tools: Map<String, GigaToolSetup>,
) {
    private val functions: List<GigaRequest.Function> = tools.map { it.value.fn }

    // Чтобы самим не думать об управлении ЖЦ, воспользуемся имеющимся channelFlow
    fun run(): Flow<String> = channelFlow {
        val conversation = ArrayList<GigaRequest.Message>() // TODO: нужно будет резюмировать историю

        userMessages.collect { userText ->
            // Добавляем в историю чата сообщения пользователя
            conversation.add(GigaRequest.Message(GigaMessageRole.user, userText))

            while (true) { // TODO: защититься от бесконечного цикла
                if (!isActive) break

                val response: GigaResponse.Chat = withContext(Dispatchers.IO) {
                    chat(conversation)
                }
                when (response) {
                    is GigaResponse.Chat.Error -> {
                        // Прерываем работу на ошибках от API Гигачата
                        send(response.message)
                        close()
                        return@collect
                    }

                    is GigaResponse.Chat.Ok -> response
                }
        
                // Добавляем в историю чата сообщения Гигачата
                conversation.addAll(response.toRequestMessages())

                val toolAwaits = ArrayList<Deferred<GigaRequest.Message>>()
                for (ch in response.choices) {
                    val msg = ch.message
                    when {
                        // Обычный текст просто печатаем
                        msg.content.isNotBlank() && msg.functionsStateId == null -> send(msg.content)

                        // Функции выполняем асинхронно
                        msg.functionCall != null && msg.functionsStateId != null -> {
                            val deferred = async(Dispatchers.IO) { executeTool(msg.functionCall) }
                            toolAwaits.add(deferred)
                        }
                    }
                }
                if (toolAwaits.isEmpty()) break
                conversation.addAll(toolAwaits.awaitAll())
            }
        }
    }

    private fun GigaResponse.Chat.Ok.toRequestMessages(): Collection<GigaRequest.Message> {
        return choices.map { ch ->
            val msg = ch.message
            val content: String = when {
                msg.content.isNotBlank() -> msg.content

                // формат, в котором ГигаЧат требует возвращать ему назад functionCall — текст в json
                msg.functionCall != null -> gigaJsonMapper.writeValueAsString(
                    mapOf("name" to msg.functionCall.name, "arguments" to msg.functionCall.arguments)
                )

                else -> throw IllegalStateException("Can't get content from ${ch}")
            }
            GigaRequest.Message(
                role = ch.message.role,
                content = content,
                functionsStateId = msg.functionsStateId
            )
        }
    }

    private fun executeTool(functionCall: GigaResponse.FunctionCall): GigaRequest.Message {
        val fn = tools[functionCall.name] ?: return GigaRequest.Message(
            GigaMessageRole.function, """{"result":"no such function ${functionCall.name}"}"""
        )
        return fn.invoke(functionCall)
    }

    private suspend fun chat(conversation: ArrayList<GigaRequest.Message>): GigaResponse.Chat {
        val body = GigaRequest.Chat(
            messages = conversation,
            functions = functions,
        )
        return api.message(body)
    }
}
```

Пробуйте запускаться. Лучше с дебаггером, чтобы понаблюдать за ходом работы.

# Реализация Anthropic-агента через SDK

Попробуем написать агента с SDK, пользуясь имеющимися функциями (тулами). Будет видно, что независимо от LLM и способа интеграции (REST API или SDK) в общем-то ничего не меняется.

Весомая причина включения второй LLM — дать возможность читателям ощутить результативность агента. С Гигачат лично у меня ничего не получилось.

## Подготовка

Создаем аккаунт на [anthropic](https://console.anthropic.com/dashboard), покупаем API Key. Пользователям из России придется повозиться: для работы с моделью понадобится VPN. У автора получилось оплатить ключ Казахстанской картой.

Ключ нужно положить в переменные окружения.

```bash
export ANTHROPIC_API_KEY=sk-ant-api....
```

К проекту SDK подключается добавлением одной зависимости:
```kotlin
dependencies {
    // ...
    implementation("com.anthropic:anthropic-java:1.0.0")
}
```

## Адаптер над функциями

С Anthropic всё то же самое, что и с Гигачатом, только будем использовать объекты их SDK вместо написанных нами DTO:

```kotlin
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlock
import com.dumch.tool.InputParamDescription
import com.dumch.tool.ToolSetup
import kotlin.reflect.KCallable
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.full.findAnnotation

interface AnthropicToolSetup {
    val tool: Tool
    operator fun invoke(toolUse: ToolUseBlock): ToolResultBlockParam
}

val anthropicJsonMapper = jsonMapper()

inline fun <reified Input> ToolSetup<Input>.toAnthropic(): AnthropicToolSetup {
    val toolSetup = this
    val inputSchema: Tool.InputSchema = HashMap<String, Any>().let { schema ->
        val clazz = Input::class
        for (property: KCallable<*> in clazz.declaredMembers) {
            val annotation = property.findAnnotation<InputParamDescription>() ?: continue
            val description = annotation.value
            val type = property.returnType.toString().substringAfterLast(".").lowercase()
            val desc = mapOf("type" to type, "description" to description)
            schema.put(property.name, desc)
        }
        Tool.InputSchema.builder()
            .properties(JsonValue.from(schema))
            .build()
    }

    return object : AnthropicToolSetup {
        override val tool: Tool = Tool.Companion.builder()
            .name(toolSetup.name)
            .description(toolSetup.description)
            .inputSchema(inputSchema)
            .build()

        override fun invoke(toolUse: ToolUseBlock): ToolResultBlockParam {
            try {
                val input: JsonValue = toolUse._input()
                val typed: Input = anthropicJsonMapper.convertValue(input, Input::class.java)
                val result = toolSetup.invoke(typed)
                return ToolResultBlockParam.builder()
                    .content(result)
                    .toolUseId(toolUse.id())
                    .isError(false)
                    .build()
            } catch (e: Exception) {
                return ToolResultBlockParam.Companion.builder()
                    .content("Unpredicted exception with the tool '$name': ${e.message}")
                    .isError(true)
                    .build()
            }
        }
    }
}
```

Агент 1 в 1, как GigaAgent. Если хотите, можете вынести общую часть в абстракцию. Я этого делать не стал, чтобы не усложнять статью.

```kotlin
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.*
import com.dumch.tool.files.*
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AnthropicAgent(
    private val client: AnthropicClient,
    private val tools: Map<String, AnthropicToolSetup>,
    private val model: Model,
    private val userMessages: Flow<String>,
) {
    private val anthropicTools: List<ToolUnion> = tools.map { (_, tool) ->
        ToolUnion.ofTool(tool.tool)
    }

    fun run(): Flow<String> = channelFlow {
        // TODO: summarize conversation
        val conversation = ArrayList<MessageParam>()
        userMessages.collect { userText ->
            val userMessageParam = MessageParam.Companion.builder()
                .role(MessageParam.Role.USER)
                .content(userText)
                .build()
            conversation.add(userMessageParam)

            for (i in 1..MAX_TOOL_ITERATIONS) { // infinite loop protection
                if (!isActive) break
                val response = withContext(Dispatchers.IO) {
                    continueChat(conversation)
                }
                conversation.add(response.toParam())

                val toolAwaits = ArrayList<Deferred<ToolResultBlockParam>>()
                for (content in response.content()) {
                    when {
                        content.isToolUse() -> {
                            val deferred = async(Dispatchers.IO) { executeTool(content.asToolUse()) }
                            toolAwaits.add(deferred)
                        }

                        content.isText() -> send(content.asText().text())
                    }
                }
                if (toolAwaits.isEmpty()) break
                val toolResults = toolAwaits.awaitAll()
                val toolContentBlockParams = toolResults.map(ContentBlockParam.Companion::ofToolResult)
                val toolUseResultMessageParam = MessageParam.Companion.builder()
                    .role(MessageParam.Role.USER)
                    .content(MessageParam.Content.ofBlockParams(toolContentBlockParams))
                    .build()
                conversation.add(toolUseResultMessageParam)
            }
        }
    }

    private fun executeTool(toolBlock: ToolUseBlock): ToolResultBlockParam {
        val name = toolBlock.name()
        val tool = tools[name] ?: return ToolResultBlockParam.Companion.builder()
            .content("Tool $name not found")
            .isError(true)
            .build()
        return tool.invoke(toolBlock)
    }

    private fun continueChat(conversation: List<MessageParam>): Message {
        val paramsBuilder = MessageCreateParams.Companion.builder()
            .model(model)
            .maxTokens(1024)
            .temperature(1.0)
            .messages(conversation)

        paramsBuilder.tools(anthropicTools)

        return client.messages().create(paramsBuilder.build())
    }

    companion object {
        private val MAX_TOOL_ITERATIONS = 10

        fun instance(
            userInputFlow: Flow<String>,
            model: Model = Model.CLAUDE_3_5_SONNET_20241022,
        ): AnthropicAgent {
            val client: AnthropicClient = AnthropicOkHttpClient.fromEnv()
            val tools: Map<String, AnthropicToolSetup> = listOf(
                ToolReadFile.toAnthropic(),
                ToolListFiles.toAnthropic(),
                ToolNewFile.toAnthropic(),
                ToolDeleteFile.toAnthropic(),
                ToolModifyFile.toAnthropic(),
                ToolFindTextInFiles.toAnthropic(),
            ).associateBy { it.tool.name() }
            return AnthropicAgent(client, tools, model, userInputFlow)
        }
    }
}
```

И запуск:
```kotlin
import com.dumch.anth.AnthropicAgent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val AGENT_ALIAS = "🪐"

suspend fun main() {
    val agent = AnthropicAgent.instance(userInputFlow())
    agent.run().collect { text -> print("$AGENT_ALIAS: $text") }
}

private fun userInputFlow(): Flow<String> = flow {
    println("Type `exit` to quit")
    while (true) {
        print("> ")
        val input = readLine() ?: break
        if (input.lowercase() == "exit") break
        emit(input)
        println("\n")
    }
}
```

# Что дальше?

А дальше — самое интересное. Попробуйте, используя агента, дописать другие функции. Например, я попросил написать функцию для терминала вот таким промптом:

> Similar with what I already have, help me implement a tool that is capable of using bash. For example, ls, echo, find, ./gradlew commands

И вот что получил:

```kotlin
package com.dumch.tool

import java.io.BufferedReader
import java.io.InputStreamReader

object ToolRunBashCommand : ToolSetup<ToolRunBashCommand.Input> {
    override val name = "RunBashCommand"
    override val description = "Executes a bash command and returns its output"

    override fun invoke(input: Input): String {
        val process = ProcessBuilder("bash", "-c", input.command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val exitCode = process.waitFor()
        if (exitCode != 0) throw RuntimeException("Command failed with exit code $exitCode")
        return output.trim()
    }

    data class Input(
        @InputParamDescription("The bash command to run, e.g., 'ls', 'echo Hello', './gradlew tasks'")
        val command: String
    )
}
```

Антропик написал такую реализацию, которая позволит ему украсть наши ключи. Лучше ограничить список команд, которые он может выполнять. Для начала можно обойтись одной: `./gradlew`.

Тест ниже тоже написан антропиком:
```kotlin
class ToolRunBashCommandTest {
    @Test
    fun `test ls command execution`() {
        // Execute the ls command
        val result = ToolRunBashCommand.invoke(ToolRunBashCommand.Input("ls"))
        
        // Verify the result contains some common files/directories
        assertTrue(result.contains("src"), "Output should contain 'src' directory")
        assertTrue(result.contains("build.gradle.kts"), "Output should contain 'build.gradle.kts' file")
    }
}
```

Не забудьте реализовать суммаризацию диалога с чатом, чтобы и токены сэкономить и помещаться в контекстное окно. Пример, как можно решить:

```kotlin
// ... inside AnthropicAgent
private suspend fun trySummarize(conversation: ArrayList<MessageParam>) {
    val msg = MessageCountTokensParams.builder().model(model).messages(conversation).build()
    val inputTokens: Long = client.messages().countTokens(msg).inputTokens()
    if (inputTokens < MAX_TOKENS * THRESHOLD_PCT /* 8096 */) return

    val summary = withContext(Dispatchers.IO) {
        client.messages().create(
            MessageCreateParams.builder()
                .model(model)
                .temperature(0.7)
                .messages(conversation)
                .system("Summarize the conversation so far")
                .build()
        )
    }

    val lastMessage = conversation.last()
    conversation.clear()
    conversation.add(summary.toParam())
    conversation.add(lastMessage)
}
```

Хорошо бы добавить обработку ошибок. Не просто завершать программу, но сохранить имеющийся «разговор» на диск, вдруг понадобится.

Можно написать обертку над [GitHub API](https://docs.github.com/en/rest/orgs?apiVersion=2022-11-28), чтобы Агент мог смотреть код открытых проектов. Или пойти в сторону Web Scraping, что будет посложнее.

Инфраструктурно явно не хватает логов для понимая, что происходит.

Если захочется встроить агента в редактор кода, хороший вариант — реализовать [LSP-сервер](https://microsoft.github.io/language-server-protocol/).

Как видите, написать агента несложно, сложно оплачивать счета за Anthropic.
