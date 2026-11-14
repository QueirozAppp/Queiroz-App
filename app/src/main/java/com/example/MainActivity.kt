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
import com.example.core.firebase.FirebaseProvider
import com.example.core.model.UserRole
import com.example.ui.admin.AdminUsersScreen
import com.example.ui.admin.AdminUsersViewModel
import com.example.ui.admin.AdminUsersViewModelFactory
import com.example.ui.admin.store.AdminStoresScreen
import com.example.ui.admin.store.AdminStoresViewModel
import com.example.ui.admin.sector.AdminSectorsScreen
import com.example.ui.admin.sector.AdminSectorsViewModel
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
import com.example.ui.product.approval.ProductApprovalScreen
import com.example.ui.product.approval.ProductApprovalViewModel
import com.example.ui.product.request.RequestProductScreen
import com.example.ui.product.request.RequestProductViewModel
import com.example.ui.reports.ReportsScreen
import com.example.ui.reports.ReportsViewModel
import com.example.ui.scanner.ScannerScreen
import com.example.ui.scanner.ScannerViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeManager
import com.example.ui.update.AppUpdateViewModel
import com.example.ui.update.AppUpdateViewModelFactory
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
        try {
            ThemeManager.initialize(applicationContext)
        } catch (_: Exception) {
        }
        try {
            FirebaseProvider.getStatus(applicationContext)
        } catch (_: Exception) {
        }
        enableEdgeToEdge()
        setContent {
            val isDark = ThemeManager.isDark()
            MyApplicationTheme(darkTheme = isDark) {
                val loginViewModel: LoginViewModel = viewModel()
                val context = LocalContext.current.applicationContext
                val appUpdateViewModel: AppUpdateViewModel = viewModel(
                    factory = AppUpdateViewModelFactory(application)
                )
                val uiState by loginViewModel.uiState.collectAsStateWithLifecycle()
                val updateUiState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()

                val authenticatedState = uiState.authState as? AuthState.Authenticated
                val mustChangePassword = authenticatedState?.userProfile?.mustChangePassword == true

                androidx.compose.runtime.LaunchedEffect(authenticatedState?.userProfile?.uid) {
                    if (authenticatedState != null && !mustChangePassword) {
                        appUpdateViewModel.checkAutomaticallyIfNeeded()
                    }
                }

                var currentScreen by remember { mutableStateOf("home") }
                var selectedConferenceRequestId by remember { mutableStateOf("") }
                var selectedStoreIdForSectors by remember { mutableStateOf("") }
                var scannerSourceScreen by remember { mutableStateOf("new_request") }
                var newRequestSourceScreen by remember { mutableStateOf("home") }
                var conferenceSourceScreen by remember { mutableStateOf("pending_conferences") }
                var conferenceReadOnly by remember { mutableStateOf(false) }

                // Intercepta a tecla voltar do sistema operacional Android para não fechar o app em telas secundárias
                BackHandler(enabled = uiState.authState is AuthState.Authenticated && !mustChangePassword && currentScreen != "home") {
                    when (currentScreen) {
                        "scanner" -> currentScreen = scannerSourceScreen
                        "new_request" -> currentScreen = newRequestSourceScreen
                        "conference_detail" -> currentScreen = conferenceSourceScreen
                        "admin_sectors" -> currentScreen = "admin_stores"
                        "request_product" -> currentScreen = "home"
                        "product_approval" -> currentScreen = "home"
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
                    else -> {
                        val profile = (uiState.authState as AuthState.Authenticated).userProfile
                        val adminProductsViewModel: AdminProductsViewModel = viewModel(
                            factory = AdminProductsViewModelFactory()
                        )
                        val scannerViewModel: ScannerViewModel = viewModel()
                        val newRequestViewModel: NewRequestViewModel = viewModel()
                        val operationalCenterViewModel: OperationalCenterViewModel = viewModel()
                        val requestProductViewModel: RequestProductViewModel = viewModel()

                        when (currentScreen) {
                            "admin_users" -> {
                                val adminUsersViewModel: AdminUsersViewModel = viewModel(
                                    factory = AdminUsersViewModelFactory(context)
                                )
                                AdminUsersScreen(
                                    viewModel = adminUsersViewModel,
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "admin_stores" -> {
                                val adminStoresViewModel: AdminStoresViewModel = viewModel()
                                AdminStoresScreen(
                                    viewModel = adminStoresViewModel,
                                    onOpenSectors = { storeId ->
                                        selectedStoreIdForSectors = storeId
                                        currentScreen = "admin_sectors"
                                    },
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "admin_sectors" -> {
                                val adminSectorsViewModel: AdminSectorsViewModel = viewModel()
                                AdminSectorsScreen(
                                    storeId = selectedStoreIdForSectors,
                                    viewModel = adminSectorsViewModel,
                                    onBackToStores = { currentScreen = "admin_stores" }
                                )
                            }
                            "admin_audit" -> {
                                val auditLogViewModel: AuditLogViewModel = viewModel(
                                    factory = AuditLogViewModelFactory()
                                )
                                AuditLogScreen(
                                    viewModel = auditLogViewModel,
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "reports" -> {
                                val reportsViewModel: ReportsViewModel = viewModel()
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
                            "operational_center" -> {
                                OperationalCenterScreen(
                                    viewModel = operationalCenterViewModel,
                                    userProfile = profile,
                                    onOpenRequestDetail = { reqId ->
                                        selectedConferenceRequestId = reqId
                                        conferenceSourceScreen = "operational_center"
                                        conferenceReadOnly = true
                                        currentScreen = "conference_detail"
                                    },
                                    onOpenConferenceDetail = { reqId ->
                                        selectedConferenceRequestId = reqId
                                        conferenceSourceScreen = "operational_center"
                                        conferenceReadOnly = false
                                        currentScreen = "conference_detail"
                                    },
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "products" -> {
                                androidx.compose.runtime.LaunchedEffect(profile) {
                                    adminProductsViewModel.setUserProfile(profile)
                                    adminProductsViewModel.refreshProducts()
                                }
                                AdminProductsScreen(
                                    viewModel = adminProductsViewModel,
                                    onBackToHome = { currentScreen = "home" },
                                    userProfile = profile,
                                    isAdmin = profile.role == UserRole.ADMIN,
                                    onOpenScanner = {
                                        scannerViewModel.tryAgain()
                                        scannerSourceScreen = "products"
                                        currentScreen = "scanner"
                                    },
                                    onOpenApprovals = {
                                        currentScreen = "product_approval"
                                    }
                                )
                            }
                            "request_product" -> {
                                RequestProductScreen(
                                    viewModel = requestProductViewModel,
                                    userProfile = profile,
                                    onOpenScanner = {
                                        scannerViewModel.tryAgain()
                                        scannerSourceScreen = "request_product"
                                        currentScreen = "scanner"
                                    },
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "product_approval" -> {
                                val productApprovalViewModel: ProductApprovalViewModel = viewModel()
                                ProductApprovalScreen(
                                    viewModel = productApprovalViewModel,
                                    userProfile = profile,
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "history" -> {
                                val historyViewModel: HistoryViewModel = viewModel()
                                androidx.compose.runtime.LaunchedEffect(profile) {
                                    historyViewModel.loadHistory(profile)
                                }
                                HistoryScreen(
                                    viewModel = historyViewModel,
                                    userProfile = profile,
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "my_requests" -> {
                                MyRequestsScreen(
                                    userProfile = profile,
                                    onNewRequest = {
                                        newRequestSourceScreen = "my_requests"
                                        currentScreen = "new_request"
                                    },
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "new_request" -> {
                                NewRequestScreen(
                                    viewModel = newRequestViewModel,
                                    userProfile = profile,
                                    onOpenScanner = {
                                        scannerViewModel.tryAgain()
                                        scannerSourceScreen = "new_request"
                                        currentScreen = "scanner"
                                    },
                                    onBackToHome = { currentScreen = newRequestSourceScreen },
                                    onRequestSubmitted = { currentScreen = "my_requests" }
                                )
                            }
                            "pending_conferences" -> {
                                PendingConferencesScreen(
                                    userProfile = profile,
                                    onOpenConferenceDetail = { reqId ->
                                        selectedConferenceRequestId = reqId
                                        conferenceSourceScreen = "pending_conferences"
                                        conferenceReadOnly = false
                                        currentScreen = "conference_detail"
                                    },
                                    onBackToHome = { currentScreen = "home" }
                                )
                            }
                            "conference_detail" -> {
                                val conferenceViewModel: ConferenceViewModel = viewModel()
                                ConferenceDetailScreen(
                                    requestId = selectedConferenceRequestId,
                                    viewModel = conferenceViewModel,
                                    userProfile = profile,
                                    readOnlyView = conferenceReadOnly,
                                    backButtonLabel = if (conferenceSourceScreen == "operational_center") {
                                        "Voltar ao Centro Operacional"
                                    } else {
                                        "Voltar para Fila"
                                    },
                                    onBackToList = { currentScreen = conferenceSourceScreen }
                                )
                            }
                            "scanner" -> {
                                val isRegSource = scannerSourceScreen == "products" || scannerSourceScreen == "request_product"
                                ScannerScreen(
                                    viewModel = scannerViewModel,
                                    onBackToHome = { currentScreen = scannerSourceScreen },
                                    isForProductRegistration = isRegSource,
                                    onProductSelected = { product ->
                                        when (scannerSourceScreen) {
                                            "products" -> {
                                                if (!adminProductsViewModel.uiState.value.isCreatingProduct) {
                                                    adminProductsViewModel.startCreatingProduct()
                                                }
                                                adminProductsViewModel.onNewBarcodeChanged(product.barcode)
                                                if (product.name.isNotBlank()) {
                                                    adminProductsViewModel.onNewNameChanged(product.name)
                                                }
                                                currentScreen = "products"
                                            }
                                            "request_product" -> {
                                                requestProductViewModel.onBarcodeChanged(product.barcode)
                                                if (product.name.isNotBlank()) {
                                                    requestProductViewModel.onNameChanged(product.name)
                                                }
                                                currentScreen = "request_product"
                                            }
                                            else -> {
                                                newRequestViewModel.onProductScannedOrSelected(product)
                                                currentScreen = "new_request"
                                            }
                                        }
                                    }
                                )
                            }
                            else -> {
                                HomeAuthenticatedScreen(
                                    userProfile = profile,
                                    onLogout = { loginViewModel.logout() },
                                    onOpenNewRequest = {
                                        newRequestSourceScreen = "home"
                                        currentScreen = "new_request"
                                    },
                                    onOpenOperationalCenter = { currentScreen = "operational_center" },
                                    onOpenReports = { currentScreen = "reports" },
                                    onOpenAdminUsers = { currentScreen = "admin_users" },
                                    onOpenStores = { currentScreen = "admin_stores" },
                                    onOpenProducts = { currentScreen = "products" },
                                    onOpenScanner = { currentScreen = "scanner" },
                                    onOpenMyRequests = { currentScreen = "my_requests" },
                                    onOpenConferences = { currentScreen = "pending_conferences" },
                                    onOpenHistory = { currentScreen = "history" },
                                    onOpenAuditLogs = { currentScreen = "admin_audit" },
                                    onOpenRequestProduct = { currentScreen = "request_product" },
                                    onOpenProductApproval = { currentScreen = "product_approval" },
                                    updateUiState = updateUiState,
                                    onCheckForUpdate = { appUpdateViewModel.checkManually() },
                                    onUpdateNow = { appUpdateViewModel.onUpdateNow(this@MainActivity) },
                                    onDismissUpdateBanner = { appUpdateViewModel.dismissBannerForLater() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
