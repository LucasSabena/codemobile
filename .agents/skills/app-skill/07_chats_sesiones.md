# Chats, Sesiones y Persistencia

## Modelo conceptual

```
Proyecto (carpeta/repo)
  └── Sesión 1 (chat)
       ├── Mensaje 1 (user)
       ├── Mensaje 2 (assistant)
       ├── Mensaje 3 (tool call)
       └── ...
  └── Sesión 2 (otro chat)
       └── ...
```

Cada **proyecto** es una carpeta en el filesystem (ej: un repo clonado). Cada **sesión** es un chat independiente dentro de ese proyecto. Las sesiones se guardan en local y se pueden borrar individualmente.

---

## Modelo de datos (Room Database)

### Entidades

```kotlin
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,           // ruta en filesystem
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sessions",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Session(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,          // auto-generado del primer mensaje o manual
    val providerId: String,     // qué provider de IA usa
    val modelId: String,        // qué modelo específico
    val mode: SessionMode,      // BUILD o PLAN
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0
)

enum class SessionMode { BUILD, PLAN }

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = Session::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Message(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: MessageRole,      // USER, ASSISTANT, SYSTEM, TOOL
    val content: String,
    val toolCalls: String? = null,    // JSON serializado de List<ToolCall>
    val toolCallId: String? = null,   // si role == TOOL
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole { USER, ASSISTANT, SYSTEM, TOOL }

@Entity(tableName = "provider_configs")
data class ProviderConfig(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: ProviderType,     // OPENAI, CLAUDE, GEMINI, COPILOT, OPENAI_COMPATIBLE
    val displayName: String,
    val defaultModelId: String? = null,
    val baseUrl: String? = null,    // solo para OPENAI_COMPATIBLE
    val isOAuth: Boolean = false,
    val isActive: Boolean = true
)

enum class ProviderType {
    OPENAI, CLAUDE, GEMINI, COPILOT, OPENAI_COMPATIBLE
}
```

### DAOs

```kotlin
@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC")
    fun getAll(): Flow<List<Project>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: Project)
    
    @Delete
    suspend fun delete(project: Project)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun getByProject(projectId: String): Flow<List<Session>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session)
    
    @Delete
    suspend fun delete(session: Session)
    
    @Query("UPDATE sessions SET updatedAt = :time, totalInputTokens = totalInputTokens + :input, totalOutputTokens = totalOutputTokens + :output WHERE id = :sessionId")
    suspend fun updateTokens(sessionId: String, input: Int, output: Int, time: Long = System.currentTimeMillis())
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: String): Flow<List<Message>>
    
    @Insert
    suspend fun insert(message: Message)
    
    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String)
    
    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: String): Int
}
```

---

## Flujo de una conversación

```
1. Usuario abre sesión existente o crea nueva
   └→ Session record en Room, messages cargados como Flow

2. Usuario escribe un prompt
   └→ Se crea Message(role=USER, content=prompt)
   └→ Se guarda en Room

3. Se construye el array de messages para enviar al provider:
   └→ System prompt + historial de mensajes de la sesión

4. Se envía al AIProvider.sendMessage()
   └→ Streaming: cada delta se muestra en el chat en tiempo real
   └→ Al finalizar: se crea Message(role=ASSISTANT, content=respuesta_completa)
   └→ Se guardan token counts

5. Si la IA hace tool calls (modo BUILD):
   └→ Se crea Message(role=ASSISTANT, toolCalls=[...])
   └→ Se ejecutan las herramientas
   └→ Se crea Message(role=TOOL, content=resultado, toolCallId=X)
   └→ Se re-envía al provider para continuar
   └→ Loop hasta que la IA responda sin tool calls

6. Tokens acumulados se actualizan en la Session
```

---

## Gestión de sesiones

### Crear sesión
- El usuario elige un proyecto y toca "+"
- Se crea con el provider/modelo seleccionado actualmente
- Título se auto-genera del primer mensaje (o "Nueva sesión" por defecto)

### Borrar sesión
- Swipe left en la lista → confirmar borrado
- Se borra la Session y todos sus Messages (CASCADE)
- **No borra archivos del proyecto** — solo el historial del chat

### Cambiar provider/modelo mid-session
- El usuario puede cambiar el provider o modelo en cualquier momento
- Los mensajes anteriores se mantienen
- El cambio se guarda en la Session

### Context window management
- Antes de enviar, se calcula cuántos tokens ocupan los mensajes
- Si excede el context window del modelo, se truncan los mensajes más viejos
- Se mantiene siempre el system prompt + último N mensajes
- Indicador visual de "tokens usados / disponibles" en la UI

---

## Cache y almacenamiento

```
Datos persistentes (no se borran automáticamente):
├── Room Database: ~/databases/codemobile.db
├── Archivos de proyecto: ~/projects/**
├── API keys: EncryptedSharedPreferences
└── Settings: DataStore Preferences

Cache (se puede limpiar):
├── npm cache: ~/.npm/_cache
├── Node modules: ~/projects/*/node_modules
└── Tmp: ~/tmp
```

### Exportar sesión
- Opción en menú de contexto de la sesión
- Exporta como JSON con: metadata de sesión + todos los mensajes
- Se puede compartir via Android Share sheet

### Importar sesión
- Futuro: importar JSON de sesión exportada
- Futuro: sincronizar sesiones via git (como OpenCode)
