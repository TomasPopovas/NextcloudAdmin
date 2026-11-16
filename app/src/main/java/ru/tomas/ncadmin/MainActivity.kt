package ru.tomas.ncadmin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.tomas.ncadmin.ui.nav.NcAdminNavHost
import ru.tomas.ncadmin.ui.theme.NcAdminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NcAdminTheme {
                NcAdminNavHost()
            }
        }
    }
}
