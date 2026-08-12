package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.theme.MyApplicationTheme
import com.example.zoya.ui.ZoyaHomeScreen
import com.example.zoya.viewmodel.ZoyaViewModel

class MainActivity : ComponentActivity() {
  private val zoyaViewModel: ZoyaViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        ZoyaHomeScreen(viewModel = zoyaViewModel)
      }
    }
  }
}

