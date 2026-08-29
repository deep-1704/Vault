package com.project.vault

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.project.vault.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/**
 * The single activity host for the entire app.
 *
 * - Annotated with [@AndroidEntryPoint] so Hilt can inject into this activity.
 * - Uses ViewBinding ([ActivityMainBinding]) for type-safe view access.
 * - Hosts the [NavHostFragment] that drives all fragment navigation.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.fragmentContainer) as NavHostFragment
        navController = navHostFragment.navController
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}