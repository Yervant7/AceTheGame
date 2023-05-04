package com.kuhakupixel.atg.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kuhakupixel.atg.R
import com.kuhakupixel.atg.ui.menu.AddressTableMenu
import com.kuhakupixel.atg.ui.menu.MemoryMenu
import com.kuhakupixel.atg.ui.menu.ProcessMenu
import com.kuhakupixel.atg.ui.menu.SettingsMenu
import com.kuhakupixel.atg.ui.util.WarningDialog
import com.topjohnwu.superuser.Shell


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    // =================== Check if root has been granted ===========
    // https://topjohnwu.github.io/libsu/com/topjohnwu/superuser/Shell.html#isAppGrantedRoot()
    val isRootGranted: Boolean? = Shell.isAppGrantedRoot()
    if (isRootGranted != null) {
        // apk not granted root, show warning
        if (!isRootGranted) {
            val showDialog: MutableState<Boolean> = remember { mutableStateOf(true) }
            if (showDialog.value) {
                WarningDialog(msg = "Root not granted, This apk need root in order to work :(",
                    onClose = { showDialog.value = false },
                    onConfirm = {})
            }

        }
    } else {
        // root access can't be determined if [isRootGranted] is null
        val showDialog: MutableState<Boolean> = remember { mutableStateOf(true) }
        if (showDialog.value) {
            WarningDialog(msg = "Cannot determine whether Root has been granted or not :(",
                onClose = { showDialog.value = false },
                onConfirm = {})
        }
    }
    // ==============================================================
    val navController = rememberNavController()
    var globalConf: GlobalConf = GlobalConf(LocalContext.current)
    // ============================ each menu in bottom nav ===================
    val menus = listOf(
        BottomBarMenu(
            route = "Process",
            title = "Process",
            iconId = R.drawable.ic_process,
            content = { ProcessMenu(globalConf) },
        ),

        BottomBarMenu(
            route = "Memory",
            title = "Memory",
            iconId = R.drawable.ic_memory,
            content = { MemoryMenu(globalConf) },
        ),

        BottomBarMenu(
            route = "Address Table",
            title = "Address Table",
            iconId = R.drawable.ic_table,
            content = { AddressTableMenu(globalConf) },
        ),

        BottomBarMenu(
            route = "settings",
            title = "Settings",
            iconId = R.drawable.ic_setting,
            content = { SettingsMenu(globalConf) },
        ),
    )
    // =====================================================
    Scaffold(bottomBar = {
        BottomBar(
            navController = navController,
            menus = menus
        )
    }) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            BottomNavGraph(navController = navController, menus = menus, startDestinationIndex = 0)
        }
    }
}

@Composable
fun BottomBar(
    navController: NavHostController,
    menus: List<BottomBarMenu>
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    BottomNavigation(backgroundColor = MaterialTheme.colorScheme.primaryContainer) {
        menus.forEach { menu: BottomBarMenu ->
            AddItem(
                rowScope = this,
                screen = menu,
                currentDestination = currentDestination,
                navController = navController
            )
        }
    }
}

@Composable
fun AddItem(
    rowScope: RowScope,
    screen: BottomBarMenu,
    currentDestination: NavDestination?,
    navController: NavHostController
) {
    rowScope.BottomNavigationItem(
        label = {
            Text(text = screen.title, fontSize = 10.sp)
        },
        icon = {
            Icon(
                imageVector = ImageVector.vectorResource(id = screen.iconId),
                contentDescription = "Navigation Icon",
                modifier = Modifier.size(20.dp),
            )

        },

        selected = currentDestination?.hierarchy?.any { navDest: NavDestination ->
            navDest.route == screen.route
        } == true,
        unselectedContentColor = LocalContentColor.current.copy(alpha = ContentAlpha.disabled),
        onClick = {
            navController.navigate(screen.route) {
                // pop every destination up to the starter destination from backstack
                // before we navigate
                // https://developer.android.com/jetpack/compose/navigation#nav-to-composable
                this.popUpTo(navController.graph.findStartDestination().id) {
                    this.inclusive = true
                }
                // Navigate to the destination only if we’re not already on
                // the destination to avoid multiple copies in backstack
                this.launchSingleTop = true
            }
        },
    )
}












