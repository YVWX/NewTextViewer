package com.text.newtextviewer

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

const val START = "start"

@Composable
fun MainNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = START
    ) {
        composable(START) {
            MainRoute()
        }
    }
}

@Composable
fun MainRoute() {
    Main()
}