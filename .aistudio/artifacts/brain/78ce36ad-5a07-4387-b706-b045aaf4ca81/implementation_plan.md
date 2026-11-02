# FocusNet - Hardening de Tráfego, Multi-App Grace e Segurança

Refinamentos de precisão aplicados no núcleo do FocusNet para garantir isolamento de tráfego sem vazamento, suporte a múltiplos apps em carência, persistência de passes e compatibilidade em todas as versões do Android.

---

### Melhorias Implementadas:

1. **Multi-App Grace Period (`Map<String, Long>`)**:
   - Substituição de variável única por mapa reativo de expiração independente. Se o usuário navegar de A para B e logo para C, tanto A quanto B mantêm seus respectivos períodos de carência ativos para concluir downloads e uploads.
   - O aviso de carência no Dashboard e nas notificações exibe os nomes amigáveis dos apps com cronômetro em tempo real.

2. **Migração Inteligente da Lista Branca**:
   - `FocusNetRepository` agora possui versionamento de esquema (`KEY_WHITELIST_VERSION = 2`).
   - Aparelhos com instalações existentes têm os serviços essenciais (Google Play Services, Play Store, Discador/Telefone, SMS e Telefonia) mesclados automaticamente sem apagar as personalizações do usuário.

3. **Detecção Exata de Teclados (sem heurísticas falsas)**:
   - Removidas heurísticas genéricas como `.ime` ou `keyboard` que podiam bloquear apps normais.
   - Consulta direta ao `InputMethodManager.enabledInputMethodList` para capturar apenas os IMEs reais ativos no sistema.

4. **UsageStats Fora da UI Thread**:
   - Consulta ao `UsageStatsManager` e o debounce de 300ms transferidos para `Dispatchers.Default`, garantindo fluidez total na interface e evitando travamentos.

5. **Compatibilidade com Android 10 a 13**:
   - `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` protegido especificamente por `Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE` (API 34), evitando falhas em versões anteriores do Android.

6. **Persistência de Passes Temporários & Logs de Expiração**:
   - Passes temporários salvos em `SharedPreferences`, sobrevivendo caso o app seja encerrado pelo sistema.
   - Expirações de passes geram eventos nos logs do firewall (`TrafficEventType.BACKGROUND_BLOCKED`).

7. **Ajustes de Segurança e Manifest**:
   - `android:allowBackup="false"` no `AndroidManifest.xml`.
   - Remoção de nomes de pacotes técnicos nas interfaces visuais em favor dos nomes amigáveis.

8. **Cobertura de Testes**:
   - Testes unitários completos em `ExampleUnitTest.kt` cobrindo valores padrão, lógica de carência multi-app, serialização/expiração de passes e proteção contra falsos positivos de teclados.
