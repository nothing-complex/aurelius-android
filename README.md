# Aurelius v2

AI Chat Android Application with MiniMax MCP Integration

## Build Instructions

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17
- Android SDK 35

### Setup

1. **Generate Gradle Wrapper** (if not already present):
   ```bash
   cd Aurelius
   gradle wrapper
   ```

2. **Build the project**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Run on device/emulator**:
   ```bash
   ./gradlew installDebug
   ```

## Features

### Core
- AI Chat with streaming responses
- Conversation management (create, rename, delete, list)
- Conversation branching support
- Persona system

### MiniMax MCP Tools
1. **text_to_audio** - Voice synthesis with emotion control
2. **understand_image** - Image analysis
3. **web_search** - Live web search
4. **text_to_image** - Image generation
5. **music_generation** - Music creation
6. **generate_video** - Video generation

### Security
- API keys stored in EncryptedSharedPreferences
- No hardcoded secrets
- Coding Plan key (sk-cp-...) stored separately

## Architecture

- **UI Layer**: Jetpack Compose, Material 3
- **Domain Layer**: Use Cases, Domain Models
- **Data Layer**: Room DB, Repositories, ToolExecutor
- **DI**: Koin 3.5.3

## API Configuration

On first launch, configure in Settings:
- MiniMax API Key (for standard tools)
- Coding Plan Key (sk-cp-...) for web_search and understand_image
- Region: Global or China
- Plan Type: Standard or Coding Plan Plus
