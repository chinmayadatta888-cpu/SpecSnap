package com.specsnap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scanViewModel = ViewModelProvider(this)[CameraScanViewModel::class.java]
        setContent {
            MaterialTheme {
                Surface {
                    SpecSnapApp(scanViewModel)
                }
            }
        }
    }
}

@Composable
private fun SpecSnapApp(viewModel: CameraScanViewModel) {
    RequestCameraPermission(viewModel)
}

@Preview(showBackground = true)
@Composable
private fun SpecSnapPreview() {
    MaterialTheme {
        Surface {
            Text("Point the camera at an electronic component")
        }
    }
}
