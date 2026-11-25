# Cipherly

Cofre de senhas, chaves de API e notas seguras para Android, desenhado para funcionar inteiramente offline.

> **Estado atual: Fase 0 — fundação do projeto.** Esta versão é apenas a estrutura inicial da aplicação. Ainda não armazena nem protege segredos; não a uses como cofre até as fases de criptografia e armazenamento estarem concluídas e revistas.

## Princípios

- Sem permissão `INTERNET`, contas, anúncios, analytics ou telemetria.
- Senha mestra não persistida.
- Segurança por padrão: bloqueio, proteção contra screenshots e clipboard temporário.
- Código Kotlin, UI Jetpack Compose/Material 3 e arquitetura MVVM.

## Stack inicial

- Kotlin 2.4.21, Android Gradle Plugin 9.5.2 e Gradle 9.6.0.
- Android 8.0 (API 26) como mínimo; Android 17 (API 37) como compile/target.
- Jetpack Compose + Material 3, Navigation Compose.
- Hilt para injeção de dependências.
- DataStore apenas para preferências não sensíveis.
- Testes locais com JUnit 5.

## Construir

Requer JDK 17 ou superior e Android SDK Platform 37.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
```

O APK debug é criado em `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap

| Fase | Estado |
|---|---|
| 0. Setup Android, Hilt, tema, CI | Concluída |
| 1. Núcleo criptográfico: Argon2id, AES-GCM, wrappers e testes | Concluída |
| 2. Cofre, onboarding e bloqueio | Concluída |
| 3. Biometria e PIN | Concluída |
| 4. Itens com Room + SQLCipher | Concluída |
| 5. Gerador, clipboard e auto-lock | Concluída |
| 6. Backup cifrado | Concluída |
| 7. Polimento e release | Em implementação (assinatura de release e workflow por tag feitos) |
| 8. Revisão de segurança | Pendente |

As decisões funcionais e os requisitos de segurança seguem o documento de design Cipherly fornecido para este projeto.
