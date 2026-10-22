package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.UserRole
import com.example.ui.admin.AdminUsersScreen
import com.example.ui.admin.AdminUsersViewModel
import com.example.ui.admin.AdminUsersViewModelFactory
import com.example.ui.admin.store.AdminStoresScreen
import com.example.ui.admin.store.AdminStoresViewModel
import com.example.ui.admin.audit.AuditLogScreen
import com.example.ui.admin.audit.AuditLogViewModel
import com.example.ui.admin.audit.AuditLogViewModelFactory
import com.example.ui.admin.product.AdminProductsScreen
import com.example.ui.admin.product.AdminProductsViewModel
import com.example.ui.admin.product.AdminProductsViewModelFactory
import com.example.ui.auth.AuthState
import com.example.ui.auth.ChangePasswordScreen
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.LoginViewModel
import com.example.ui.conference.ConferenceDetailScreen
import com.example.ui.conference.ConferenceViewModel
import com.example.ui.conference.PendingConferencesScreen
import com.example.ui.history.HistoryScreen
import com.example.ui.history.HistoryViewModel
import com.example.ui.home.HomeAuthenticatedScreen
import com.example.ui.operational.OperationalCenterScreen
import com.example.ui.operational.OperationalCenterViewModel
import com.example.ui.reports.ReportsScreen
import com.example.ui.reports.ReportsViewModel
import com.example.ui.scanner.ScannerScreen
import com.example.ui.scanner.ScannerViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeManager
import com.example.ui.withdrawal.MyRequestsScreen
import com.example.ui.withdrawal.NewRequestScreen
import com.example.ui.withdrawal.NewRequestViewModel

/**
 * Atividade principal do aplicativo QRZ Stock.
 * Gerencia o roteamento entre Login, Home Autenticada, Administração, Produtos, Scanner, Solicitações, Conferências e Histórico (Etapa 9).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.initialize(applicationContext)
        enableEdgeToEdge()
        setContent {
            val isDark = ThemeManager.isDark()
            MyApplicationTheme(darkTheme = isDark) {
                val loginViewModel: LoginViewModel = viewModel()
                val context = LocalContext.current.applicationContext
                val adminUsersViewModel: AdminUsersViewModel = viewModel(
                    factory = AdminUsersViewModelFactory(context)
                )
                val adminStoresViewModel: AdminStoresViewModel = viewModel()
                val adminProductsViewModel: AdminProductsViewModel = viewModel(
                    factory = AdminProductsViewModelFactory()
                )
                val scannerViewModel: ScannerViewModel = viewModel()
                val newRequestViewModel: NewRequestViewModel = viewModel()
                val conferenceViewModel: ConferenceViewModel = viewModel()
                val historyViewModel: HistoryViewModel = viewModel()
                val auditLogViewModel: AuditLogViewModel = viewModel(
                    factory = AuditLogViewModelFactory()
                )
                val operationalCenterViewModel: OperationalCenterViewModel = viewModel()
                val reportsViewModel: ReportsViewModel = viewModel()
                val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()

                val authenticatedState = uiState.authState as? AuthState.Authenticated
                val mustChangePassword = authenticatedState?.userProfile?.mustChangePassword == true

                var currentScreen by remember { mutableStateOf("home") }
                var selectedConferenceRequestId by remember { mutableStateOf("") }
                var scannerSourceScreen by remember { mutableStateOf("new_request") }

                // Intercepta a tecla voltar do sistema operacional Android para não fechar o app em telas secundárias
                BackHandler(enabled = uiState.authState is AuthState.Authenticated && !mustChangePassword && currentScreen != "home") {
                    when (currentScreen) {
                        "scanner" -> currentScreen = scannerSourceScreen
                        "new_request" -> currentScreen = "my_requests"
                        "conference_detail" -> currentScreen = "pending_conferences"
                        else -> currentScreen = "home"
                    }
                }

                when {
                    uiState.authState !is AuthState.Authenticated -> {
                        LoginScreen(viewModel = loginViewModel)
                    }
                    mustChangePassword -> {
                        ChangePasswordScreen(
                            viewModel = loginViewModel,
                            onPasswordChanged = {
                                loginViewModel.checkExistingSession()
                            }
                        )
                    }
                    currentScreen == "admin_users" -> {
                        AdminUsersScreen(
                            viewModel = adminUsersViewModel,
                            onBackToHome = { currentScreen = "home" }
                        )
                    }
                    currentScreen == "admin_stores" -> {
                        AdminStoresScreen(
                            viewModel = adminStoresViewModel,
                            onOpenSectors = { /* handled */ },
                            onBackToHome = { currentScreen = "home" }
                        )
                    }
                    currentScreen == "admin_audit" -> {
                        AuditLogScreen(
                            viewModel = auditLogViewModel,
                            onBackToHome = { currentScreen = "home" }
                        )
                    }
                    currentScreen == "reports" -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        ReportsScreen(
                            viewModel = reportsViewModel,
                            userProfile = profile,
                            onOpenOperationalStatus = { status ->
                                operationalCenterViewModel.setStatusFilter(status)
                                currentScreen = "operational_center"
                            },
                            onBackToHome = { currentScreen = "home" }
                        )
                    }
                    currentScreen == "operational_center" -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        com.example.ui.operational.OperationalCenterScreen(
                            viewModel = operationalCenterViewModel,
                            userProfile = profile,
                            onOpenRequestDetail = { reqId ->
                                selectedConferenceRequestId = reqId
                                currentScreen = "conference_detail"
                            },
                            onOpenConferenceDetail = { reqId ->
                                selectedConferenceRequestId = reqId
                                currentScreen = "conference_detail"
                            },
                            onBackToHome = { currentScreen = "home" }
                        )
                    }
                    currentScreen == "products" -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        AdminProductsScreen(
                            viewModel = adminProductsViewModel,
                            onBackToHome = { currentScreen = "home" },
                            isAdmin = profile.role == UserRole.ADMIN,
                            onOpenScanner = {
                                scannerSourceScreen = "products"
                                currentScreen = "scanner"
                            }
                        )
                    }
                    currentScreen == "history" -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        androidx.compose.runtime.LaunchedEffect(profile) {
                            historyViewModel.loadHistory(profile)
                        }
                        HistoryScreen(
                            viewModel = historyViewModel,
                            userProfile = profile,
                            onBackToHome = { currentScreen = "home" }
                        )
                    }
                    currentScreen == "my_requests" -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        MyRequestsScreen(
                            userProfile = profile,
                            onNewRequest = { currentScreen = "new_request" },
                            onBackToHome = { currentScreen = "home" }
                        )
                    }
                    currentScreen == "new_request" -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        NewRequestScreen(
                            viewModel = newRequestViewModel,
                            userProfile = profile,
                            onOpenScanner = {
                                scannerSourceScreen = "new_request"
                                currentScreen = "scanner"
                            },
                            onBackToHome = { currentScreen = "my_requests" }
                        )
                    }
                    currentScreen == "pending_conferences" -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        PendingConferencesScreen(
                            userProfile = profile,
                            onOpenConferenceDetail = { reqId ->
                                selectedConferenceRequestId = reqId
                                currentScreen = "conference_detail"
                            },
                            onBackToHome = { currentScreen = "home" }
                        )
                    }
                    currentScreen == "conference_detail" -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        ConferenceDetailScreen(
                            requestId = selectedConferenceRequestId,
                            viewModel = conferenceViewModel,
                            userProfile = profile,
                            onBackToList = { currentScreen = "pending_conferences" }
                        )
                    }
                    currentScreen == "scanner" -> {
                        ScannerScreen(
                            viewModel = scannerViewModel,
                            onBackToHome = { currentScreen = scannerSourceScreen },
                            onProductSelected = { product ->
                                if (scannerSourceScreen == "products") {
                                    adminProductsViewModel.startCreatingProduct()
                                    adminProductsViewModel.onNewBarcodeChanged(product.barcode)
                                    adminProductsViewModel.onNewNameChanged(product.name)
                                    currentScreen = "products"
                                } else {
                                    newRequestViewModel.onProductScannedOrSelected(product)
                                    currentScreen = "new_request"
                                }
                            }
                        )
                    }
                    else -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        HomeAuthenticatedScreen(
                            userProfile = profile,
                            onLogout = { loginViewModel.logout() },
                            onOpenNewRequest = { currentScreen = "new_request" },
                            onOpenOperationalCenter = { currentScreen = "operational_center" },
                            onOpenReports = { currentScreen = "reports" },
                            onOpenAdminUsers = { currentScreen = "admin_users" },
                            onOpenStores = { currentScreen = "admin_stores" },
                            onOpenProducts = { currentScreen = "products" },
                            onOpenScanner = { currentScreen = "scanner" },
                            onOpenMyRequests = { currentScreen = "my_requests" },
                            onOpenConferences = { currentScreen = "pending_conferences" },
                            onOpenHistory = { currentScreen = "history" },
                            onOpenAuditLogs = { currentScreen = "admin_audit" }
                        )
                    }
                }
            }
        }
    }
}
