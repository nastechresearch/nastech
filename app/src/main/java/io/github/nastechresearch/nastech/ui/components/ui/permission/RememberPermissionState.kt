package io.github.nastechresearch.nastech.ui.components.ui.permission

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Creates and remembers permission state.
 *
 * @param permissions Set of permission definitions.
 * @return PermissionState manager.
 *
 * Example:
 * ```
 * val permissionState = rememberPermissionState(
 *     permissions = setOf(
 *         PermissionInfo(
 *             permission = Manifest.permission.CAMERA,
 *             usage = { Text("Camera access is needed to take photos") },
 *             required = true
 *         ),
 *         PermissionInfo(
 *             permission = Manifest.permission.RECORD_AUDIO,
 *             usage = { Text("Audio recording permission is needed to record video") },
 *             required = false
 *         )
 *     )
 * )
 *
 * // Request permissions.
 * Button(onClick = { permissionState.requestPermissions() }) {
 *     Text("Request permissions")
 * }
 *
 * // Check permission status.
 * if (permissionState.allRequiredPermissionsGranted) {
 *     Text("All required permissions granted")
 * }
 * ```
 */
@Composable
fun rememberPermissionState(
    permissions: Set<PermissionInfo>
): PermissionState {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
        ?: throw IllegalStateException("rememberPermissionState must be used within a ComponentActivity")

    // Create the permission state object.
    val permissionState = remember(permissions) {
        PermissionState(permissions, context, activity)
    }

    // Launcher for multiple permission requests.
    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionState.handlePermissionResult(results)
    }

    // Launcher for a single permission request.
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Infer the last requested permission from the current rationale or denied permissions.
        val lastRequestedPermission = permissionState.currentRationalePermissions.firstOrNull()?.permission
            ?: permissionState.deniedPermissions.firstOrNull()?.permission

        lastRequestedPermission?.let { permission ->
            permissionState.handleSinglePermissionResult(permission, granted)
        }
    }

    // Register the launchers.
    LaunchedEffect(multiplePermissionLauncher, singlePermissionLauncher) {
        permissionState.setPermissionLaunchers(multiplePermissionLauncher, singlePermissionLauncher)
    }

    // Observe lifecycle changes and refresh permission state.
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // Refresh when the app returns to the foreground to capture changes made in settings.
                    permissionState.refreshPermissionStates()
                }

                Lifecycle.Event.ON_RESUME -> {
                    // Refresh again on resume to keep the state current.
                    permissionState.refreshPermissionStates()
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Refresh permission state during initialization.
    LaunchedEffect(Unit) {
        permissionState.updatePermissionStates()
    }

    return permissionState
}

/**
 * Creates and remembers a single permission state.
 *
 * @param permission Permission identifier.
 * @param usage Permission usage explanation.
 * @param required Whether the permission is required.
 * @return PermissionState manager.
 */
@Composable
fun rememberPermissionState(
    permission: String,
    displayName: @Composable () -> Unit,
    usage: @Composable () -> Unit,
    required: Boolean = false
): PermissionState {
    return rememberPermissionState(
        permissions = setOf(
            PermissionInfo(
                permission = permission,
                displayName = displayName,
                usage = usage,
                required = required,
            )
        )
    )
}

@Composable
fun rememberPermissionState(
    info: PermissionInfo
): PermissionState {
    return rememberPermissionState(
        permissions = setOf(info)
    )
}
