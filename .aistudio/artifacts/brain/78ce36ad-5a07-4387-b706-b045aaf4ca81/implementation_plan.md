# FocusNet - Contador de Economia de Dados e Gráfico de Consumo

Adição de uma central de economia de dados no FocusNet, trazendo um **Contador de Megabytes Economizados** e um **Gráfico de Consumo Visual**, permitindo que o usuário veja exatamente quanto da sua franquia de dados móveis e Wi-Fi foi poupada ao bloquear o tráfego oculto dos aplicativos em segundo plano.

---

### Decisões Críticas e Preferências Confirmadas

> [!IMPORTANT]
> As seguintes escolhas foram alinhadas com as respostas do usuário:

- **Funcionalidade Principal Escolhida**: Contador de Megabytes/Gigabytes economizados e Gráfico de Consumo comparativo.
- **Notificações**: Silencioso — apenas registrar as tentativas na aba "2º Plano" sem disparar notificações incômodas na tela.
- **Foco em Simplicidade**: Interface limpa e direta, mantendo a experiência fluida sem sobrecarregar o usuário.

---

### 1. Visão Geral e Conceito da Nova Funcionalidade

- **O que será entregue**:
  1. **Contador em Tempo Real de Dados Salvos**: Indicador destacado em MB / GB (ex: `+148.6 MB poupados`) calculado com base nas tentativas e pacotes barrados de apps em segundo plano (como pré-carregamento de vídeos do TikTok, sincronização do WhatsApp e feeds de redes sociais).
  2. **Gráfico de Consumo & Economia**: Visualizador visual moderno em Jetpack Compose exibindo o comparativo de dados economizados ao longo do tempo (últimas horas / dias) e repartição por aplicativo (ex: quanto o TikTok, Instagram ou WhatsApp deixaram de gastar).
  3. **Estimativa de Vida da Franquia**: Resumo prático mostrando a porcentagem de economia (ex: `~65% de dados preservados na sua recarga móvel`).

---

### 2. Experiência do Usuário e Design Visual

#### Novos Elementos Visuais no Dashboard
- **Card de Economia em Destaque (Data Savings Hero)**:
  - Número grande e brilhante em verde esmeralda neon: `184.2 MB` poupados hoje.
  - Subtítulo com taxa de preservação de franquia (`Economia de ~68%`).
- **Gráfico Comparativo de Barras / Áreas (Jetpack Compose Canvas)**:
  - Barras verticais com gradiente ciano e esmeralda representando as horas do dia ou dias da semana.
  - Toque interativo para inspecionar cada período.
- **Distribuição por Aplicativo**:
  - Lista ranqueada mostrando quem tentou gastar mais internet em segundo plano:
    - *TikTok*: 94.2 MB bloqueados
    - *Instagram*: 52.8 MB bloqueados
    - *WhatsApp*: 24.1 MB bloqueados
    - *YouTube*: 13.1 MB bloqueados

---

### 3. Decisões Técnicas e Arquitetura

```
┌────────────────────────────────────────────────────────┐
│             FocusNetVpnService / Repositório           │
│  - Captura pacotes descartados de apps em 2º plano     │
│  - Calcula bytes poupados por pacote e por app         │
│  - Agrega histórico temporal (hora em hora / diário)   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                 FocusNetRepository                     │
│  - StateFlow<Long> totalBytesSaved                     │
│  - StateFlow<Map<String, Long>> savingsByPackage       │
│  - StateFlow<List<DataSavingsPoint>> timeSeriesData    │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                   DashboardScreen                      │
│  - DataSavingsCard (Métricas & Economia estimada)      │
│  - SavingsChartCard (Gráfico com Canvas M3)            │
│  - TopSaversBreakdown (Rank dos apps mais contidos)    │
└────────────────────────────────────────────────────────┘
```

#### Modificações de Código Planejadas
1. `model/DataSavingsModels.kt`:
   - `DataSavingsPoint`: modelo para pontos temporais do gráfico.
   - `AppSavingsSummary`: resumo por aplicativo com bytes poupados.
2. `data/FocusNetRepository.kt`:
   - Persistência e acumulação de bytes poupados em `SharedPreferences`.
   - Métodos para incrementar dados salvos a cada conexão barrada em segundo plano.
3. `ui/components/SavingsChartCard.kt`:
   - Componente moderno com Canvas Jetpack Compose para desenhar as barras de economia com animação suave e gradientes do tema.
4. `ui/screens/DashboardScreen.kt`:
   - Integração do card de economia e do gráfico de consumo diretamente na tela principal.
