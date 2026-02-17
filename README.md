# CodeMobile 🚀

Un IDE móvil para Android impulsado por IA. Escribe, edita y gestiona tu código desde cualquier lugar con la ayuda de asistentes inteligentes.

## ✨ Características

- 🤖 **IA Integrada** - Conecta con OpenAI, Claude y GitHub Copilot
- 💬 **Chat Interactivo** - Conversa con la IA sobre tu código
- 📝 **Editor de Código** - Editor integrado con soporte multi-lenguaje
- 🖥️ **Terminal** - Terminal completa directamente en tu móvil
- 👁️ **Preview en Vivo** - Visualiza tus aplicaciones web en tiempo real
- 🔗 **GitHub** - Clona repos y gestiona tu código con Git
- 🎨 **Material Design 3** - Interfaz moderna y personalizable

## 🏗️ Arquitectura

El proyecto está modularizado en:

| Módulo | Descripción |
|--------|-------------|
| `:app` | UI principal, navegación y temas |
| `:core` | Database, repositorios, modelos, GitHub API |
| `:ai` | Proveedores de IA, autenticación, streaming |
| `:editor` | Componentes del editor de código |
| `:terminal` | Servicio de terminal integrado |
| `:preview` | Previsualización de desarrollo |

## 🛠️ Tecnologías

- **Kotlin** - Lenguaje principal
- **Jetpack Compose** - UI moderna y declarativa
- **Hilt** - Inyección de dependencias
- **Room** - Base de datos local
- **Kotlin Coroutines & Flow** - Programación asíncrona
- **Retrofit/OkHttp** - Networking
- **Material Design 3** - Sistema de diseño

## 🚀 Comenzar

### Requisitos

- Android Studio Hedgehog o superior
- JDK 17+
- Android SDK 24+

### Instalación

```bash
# Clonar el repositorio
git clone https://github.com/LucasSabena/codemobile.git

# Abrir en Android Studio
# Sincronizar Gradle y ejecutar
```

## ⚙️ Configuración de IA

CodeMobile soporta múltiples proveedores de IA:

1. **OpenAI** - GPT-4, GPT-3.5-turbo
2. **Claude** - Anthropic Claude 3
3. **GitHub Copilot** - Autocompletado inteligente

Configura tus API keys en Settings > AI Providers.

## 📸 Screenshots

*(Próximamente)*

## 🤝 Contribuir

¡Las contribuciones son bienvenidas! Por favor:

1. Haz fork del proyecto
2. Crea tu rama (`git checkout -b feature/AmazingFeature`)
3. Commit tus cambios (`git commit -m 'Add some AmazingFeature'`)
4. Push a la rama (`git push origin feature/AmazingFeature`)
5. Abre un Pull Request

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Ver `LICENSE` para más detalles.

---

<p align="center">Hecho con ❤️ para desarrolladores móviles</p>
