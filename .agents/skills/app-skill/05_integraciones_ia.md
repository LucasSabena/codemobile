# Integraciones con Providers de IA

## Modelo de autenticación

**BYOK (Bring Your Own Key)** como base + **OAuth PKCE** para servicios que lo soportan. Sin backend propio — todas las llamadas van directo del dispositivo al provider.

### Opciones de autenticación por provider

| Provider | API Key directa | OAuth / Login | Plan de coding |
|----------|:-:|:-:|:-:|
| OpenAI | ✅ | ❌ | ✅ (ChatGPT Plus API access) |
| Anthropic/Claude | ✅ | ❌ | ✅ (Claude Pro) |
| Google Gemini | ✅ | ✅ (Google OAuth) | ✅ (Gemini Advanced) |
| GitHub Copilot | ❌ | ✅ (GitHub OAuth PKCE) | ✅ (Copilot Individual/Business) |
| OpenAI-compatible | ✅ | ❌ | N/A |

### OAuth PKCE (sin backend)

Para GitHub Copilot y otros providers que usen OAuth:

```
1. Registrar OAuth App en GitHub (client_id público, sin client_secret — PKCE)
2. Generar code_verifier + code_challenge (S256)
3. Abrir Custom Chrome Tab:
   https://github.com/login/oauth/authorize?
     client_id=XXX&
     code_challenge=YYY&
     code_challenge_method=S256&
     scope=copilot
4. Usuario autoriza en GitHub
5. GitHub redirige a deep link: codemobile://oauth/callback?code=ZZZ
6. App intercepta el deep link
7. Intercambiar código por token:
   POST https://github.com/login/oauth/access_token
   { client_id, code, code_verifier }
8. Guardar token en EncryptedSharedPreferences
9. Usar token para llamadas a Copilot API
```

Esto funciona **sin servidor propio** porque PKCE no requiere client_secret.

### Planes de coding / suscripciones

Muchos developers ya pagan por planes de IA:
- **GitHub Copilot** (Individual $10/mes, Business $19/mes) → acceso via OAuth
- **ChatGPT Plus** ($20/mes) → la API key tiene rate limits más generosos
- **Claude Pro** ($20/mes) → API key con más capacidad
- **Gemini Advanced** ($20/mes) → Google OAuth

La app les permite **usar esas suscripciones que ya pagan** desde el celular.

---

## Abstracción de providers

### Interface principal

```kotlin
interface AIProvider {
    val id: String
    val name: String
    val iconRes: Int

    /** Enviar mensajes y recibir respuesta en streaming */
    fun sendMessage(
        messages: List<AIMessage>,
        model: String,
        tools: List<AITool>? = null,
        config: AIConfig = AIConfig()
    ): Flow<AIStreamEvent>

    /** Listar modelos disponibles */
    suspend fun listModels(): List<AIModel>

    /** Validar que las credenciales funcionan */
    suspend fun validateCredentials(): Boolean
}
```

### Modelos de datos

```kotlin
data class AIMessage(
    val role: Role,          // USER, ASSISTANT, SYSTEM, TOOL
    val content: String,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
)

sealed class AIStreamEvent {
    data class TextDelta(val text: String) : AIStreamEvent()
    data class ToolCallDelta(val toolCall: ToolCall) : AIStreamEvent()
    data class Usage(val inputTokens: Int, val outputTokens: Int) : AIStreamEvent()
    data object Done : AIStreamEvent()
    data class Error(val message: String) : AIStreamEvent()
}

data class AIModel(
    val id: String,
    val name: String,
    val contextWindow: Int,
    val supportsTools: Boolean,
    val supportsVision: Boolean
)

data class AIConfig(
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null
)
```

### Implementaciones concretas

#### OpenAI
```kotlin
class OpenAIProvider(private val apiKey: String) : AIProvider {
    // POST https://api.openai.com/v1/chat/completions
    // Headers: Authorization: Bearer $apiKey
    // Streaming via SSE (stream: true)
    // Modelos: gpt-4o, gpt-4o-mini, o1, o1-mini, etc.
}
```

#### Anthropic/Claude
```kotlin
class ClaudeProvider(private val apiKey: String) : AIProvider {
    // POST https://api.anthropic.com/v1/messages
    // Headers: x-api-key: $apiKey, anthropic-version: 2023-06-01
    // Streaming via SSE
    // Modelos: claude-sonnet-4-20250514, claude-3-5-haiku, etc.
}
```

#### Google Gemini
```kotlin
class GeminiProvider(private val apiKey: String) : AIProvider {
    // POST https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent
    // Query param: key=$apiKey
    // Modelos: gemini-2.0-flash, gemini-2.0-pro, etc.
}
```

#### GitHub Copilot
```kotlin
class CopilotProvider(private val oauthToken: String) : AIProvider {
    // Requiere OAuth token obtenido via PKCE flow
    // POST https://api.githubcopilot.com/chat/completions
    // Headers: Authorization: Bearer $oauthToken
    // Formato compatible con OpenAI
}
```

#### OpenAI-Compatible (genérico)
```kotlin
class OpenAICompatibleProvider(
    private val baseUrl: String,  // e.g., "http://localhost:11434/v1" (Ollama)
    private val apiKey: String?   // opcional
) : AIProvider {
    // Mismo formato que OpenAI pero apuntando a cualquier endpoint
    // Útil para: Ollama, LM Studio, Groq, Together, Fireworks, vLLM, etc.
}
```

---

## Streaming

Todas las respuestas se reciben en streaming para mostrar texto en tiempo real:

1. **SSE (Server-Sent Events)**: OpenAI, Claude, Copilot usan este formato
2. **HTTP chunked**: Gemini usa streamGenerateContent con chunks
3. Cada chunk se parsea y emite como `AIStreamEvent` via Kotlin `Flow`
4. La UI collect el Flow y actualiza el `ChatBubble` en tiempo real

### Implementación de SSE con OkHttp

```kotlin
fun streamSSE(request: Request): Flow<String> = callbackFlow {
    val client = OkHttpClient()
    val call = client.newCall(request)
    
    call.enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            response.body?.source()?.let { source ->
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ")
                        if (data != "[DONE]") {
                            trySend(data)
                        }
                    }
                }
            }
            close()
        }
        override fun onFailure(call: Call, e: IOException) {
            close(e)
        }
    })
    
    awaitClose { call.cancel() }
}
```

---

## Almacenamiento seguro de credenciales

```kotlin
// Android Keystore + EncryptedSharedPreferences
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val securePrefs = EncryptedSharedPreferences.create(
    context,
    "secure_provider_keys",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

// Guardar
securePrefs.edit().putString("openai_api_key", apiKey).apply()

// Leer
val apiKey = securePrefs.getString("openai_api_key", null)
```

## Pantalla de configuración de providers

Para cada provider, el usuario puede:
1. **Agregar API key** — input de texto + botón "Test conexión"
2. **Conectar via OAuth** — botón que abre el flujo OAuth (para Copilot, Gemini)
3. **Seleccionar modelo por defecto** — dropdown con modelos disponibles
4. **Eliminar provider** — borra credenciales y configuración
5. **Provider genérico** — input de URL base + API key opcional (para Ollama, Groq, etc.)

Cada provider muestra un indicador de estado: ✅ Conectado | ❌ Error | ⏳ No configurado
