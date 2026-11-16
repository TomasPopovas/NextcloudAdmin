package ru.tomas.ncadmin.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.tomas.ncadmin.NcAdminApp
import ru.tomas.ncadmin.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onUsers: () -> Unit, onGroups: () -> Unit, onSwitchAccount: () -> Unit) {
    val context = LocalContext.current
    val account = remember { (context.applicationContext as NcAdminApp).container.accounts.current }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(account?.host ?: stringResource(R.string.home_title))
                        if (account != null) {
                            Text(
                                buildString {
                                    append(stringResource(R.string.home_logged_as, account.displayName.ifBlank { account.loginName }))
                                    if (account.serverVersion.isNotBlank()) append(" · ").append(stringResource(R.string.home_server_version, account.serverVersion))
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSwitchAccount) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.home_switch_account))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeCard(Icons.Default.Person, stringResource(R.string.home_users), null, onUsers)
            HomeCard(Icons.Default.Group, stringResource(R.string.home_groups), null, onGroups)
            HomeCard(Icons.Default.Settings, stringResource(R.string.home_settings), stringResource(R.string.home_settings_soon), null)
        }
    }
}

@Composable
private fun HomeCard(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(
            containerColor = if (onClick != null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp), tint = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
