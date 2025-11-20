package com.example.easyesp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.navigation.NavigationView
import androidx.activity.viewModels
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var navController: NavController

    private val connectionViewModel: ConnectionViewModel by viewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // **THE CRITICAL CHANGE IS HERE:** Load the new activity_main.xml
        setContentView(R.layout.activity_main)

        // Find the new UI components from activity_main.xml
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)

        // Get the NavController from the NavHostFragment
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Define which menu items are top-level destinations (show hamburger icon)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.wifiTerminalFragment, R.id.navigation_known_devices, // <-- ADD THIS
                R.id.nav_bluetooth_settings, R.id.nav_sandbox
            ), drawerLayout
        )

        // Connect the toolbar (ActionBar) to the NavController
        setupActionBarWithNavController(navController, appBarConfiguration)
        // Connect the navigation drawer to the NavController
        navView.setupWithNavController(navController)
        ViewModelHolder.connectionViewModel = this.connectionViewModel
    }

    // This function handles the "Up" button (back arrow) in the toolbar
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}