package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppInfo
import com.example.ui.components.AppIconImage
import com.example.ui.theme.DarkEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.SlateCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonBlocked

@Composable
fun WhitelistScreen(
    apps: List<AppInfo>,
    searchQuery: String,
    temporaryPasses: Map<String, Long> = emptyMap(),
    onSearchQueryChange: (String) -> Unit,
    onToggleWhitelist: (String, Boolean) -> Unit,
    onGrantTemporaryPass: (String, Int) -> Unit = { _, _ -> },
    onRevokeTemporaryPass: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_apps_input"),
            placeholder = { Text("Buscar aplicativo...", color = TextMuted) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = TextMuted
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpar busca",
                            tint = TextMuted
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF131D2E),
                unfocusedContainerColor = Color(0xFF131D2E),
                focusedBorderColor = NeonEmerald,
                unfocusedBorderColor = SlateCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Smart System Default Advice Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F261C), RoundedCornerShape(12.dp))
                .border(1.dp, NeonEmerald.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = NeonEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Lista Branca Inteligente de Sistema",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonEmerald
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Google Play Services, Telefone e SMS vêm liberados por padrão para não perder notificações push globais nem chamadas telefônicas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Suggestion for WhatsApp & VoIP calling apps
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111E30), RoundedCornerShape(12.dp))
                .border(1.dp, ElectricCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Sugestão para Chamadas e Mensagens",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Se quiser receber ligações do WhatsApp ou Telegram mesmo com outro app aberto, ative o botão de 2º plano ou use o Passe Temporário de 5 min.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Apps List
        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nenhum aplicativo encontrado",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    val expiry = temporaryPasses[app.packageName]
                    val hasTempPass = expiry != null && expiry > now
                    val remainingMins = if (hasTempPass && expiry != null) {
                        ((expiry - now) / 60000L).coerceAtLeast(1)
                    } else 0L

                    AppWhitelistItem(
                        app = app,
                        hasTempPass = hasTempPass,
                        tempRemainingMinutes = remainingMins,
                        onToggle = { isAllowed -> onToggleWhitelist(app.packageName, isAllowed) },
                        onGrantTempPass = { onGrantTemporaryPass(app.packageName, 5) },
                        onRevokeTempPass = { onRevokeTemporaryPass(app.packageName) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AppWhitelistItem(
    app: AppInfo,
    hasTempPass: Boolean,
    tempRemainingMinutes: Long,
    onToggle: (Boolean) -> Unit,
    onGrantTempPass: () -> Unit,
    onRevokeTempPass: () -> Unit
) {
    val isHighlighted = app.isWhitelisted || hasTempPass

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_item_${app.packageName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                app.isWhitelisted -> Color(0xFF162539)
                hasTempPass -> Color(0xFF261D12)
                else -> Color(0xFF131D2E)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                app.isWhitelisted -> NeonEmerald.copy(alpha = 0.5f)
                hasTempPass -> AmberAlert.copy(alpha = 0.5f)
                else -> SlateCardBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    drawable = app.icon,
                    contentDescription = app.appName,
                    size = 42.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        if (app.isWhitelisted) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(DarkEmerald, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "2º PLANO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonEmerald
                                )
                            }
                        } else if (hasTempPass) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF452A0A), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PASSE (${tempRemainingMinutes}m)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AmberAlert
                                )
                            }
                        }
                    }

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        maxLines = 1,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!app.isWhitelisted) {
                    if (hasTempPass) {
                        OutlinedButton(
                            onClick = onRevokeTempPass,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 2.dp
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("revoke_temp_${app.packageName}")
                        ) {
                            Text(
                                text = "Revogar",
                                style = MaterialTheme.typography.labelSmall,
                                color = CrimsonBlocked,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onGrantTempPass,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 8.dp,
                                vertical = 2.dp
                            ),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_temp_pass_${app.packageName}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+5 min",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Switch(
                    checked = app.isWhitelisted,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("switch_${app.packageName}"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonEmerald,
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF1E293B)
                    )
                )
            }
        }
    }
}
