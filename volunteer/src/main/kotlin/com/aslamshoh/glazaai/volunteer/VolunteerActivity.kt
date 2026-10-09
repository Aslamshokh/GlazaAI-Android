package com.aslamshoh.glazaai.volunteer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aslamshoh.glazaai.volunteer.ui.VolunteerRoot
import com.aslamshoh.glazaai.volunteer.ui.VolunteerTheme

class VolunteerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VolunteerTheme {
                VolunteerRoot()
            }
        }
    }
}
