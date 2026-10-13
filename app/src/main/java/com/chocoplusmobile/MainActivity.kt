package com.chocoplusmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.chocoplusmobile.navigation.ChocoNavGraph
import com.chocoplusmobile.state.AppViewModel
import com.chocoplusmobile.ui.theme.ChocoColors
import com.chocoplusmobile.ui.theme.ChocoTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels { AppViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ChocoTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = ChocoColors.bg) {
                    ChocoNavGraph(appViewModel)
                }
            }
        }
    }
}
