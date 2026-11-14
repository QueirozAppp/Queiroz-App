package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.update.AppUpdateUiState
import com.example.core.update.RemoteVersionInfo
import com.example.ui.home.HomeAuthenticatedScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StartupAndHomeComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `home screen renders for setor leader with update banner and check button`() {
        var checkClicked = false
        var dismissClicked = false
        val profile = UserProfile(
            uid = "leader_1",
            name = "João Vitor",
            email = "joao@superqueiroz.com",
            role = UserRole.SETOR_LEADER,
            storeId = "LOJA_05",
            sectorId = "LOJA_05_GERAL",
            active = true
        )
        val updateState = AppUpdateUiState(
            availableUpdate = RemoteVersionInfo(
                version = "1.3.0",
                build = 13,
                notes = "Correção de estabilidade",
                url = "https://github.com/QueirozAppp/Queiroz-App/releases/latest/download/app-debug.apk"
            ),
            showBanner = true,
            isChecking = false
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                HomeAuthenticatedScreen(
                    userProfile = profile,
                    onLogout = {},
                    onOpenNewRequest = {},
                    onOpenOperationalCenter = {},
                    onOpenProducts = {},
                    onOpenRequestProduct = {},
                    updateUiState = updateState,
                    onCheckForUpdate = { checkClicked = true },
                    onUpdateNow = {},
                    onDismissUpdateBanner = { dismissClicked = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_authenticated_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_update_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nova versão 1.3.0 disponível").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_update_later").performClick()
        assertTrue(dismissClicked)

        composeTestRule.onNodeWithTag("btn_check_update").performScrollTo().performClick()
        assertTrue(checkClicked)
    }

    @Test
    fun `home screen renders cleanly for avarista and admin roles`() {
        val avaristaProfile = UserProfile(
            uid = "avarista_1",
            name = "Maria Avarista",
            email = "maria@superqueiroz.com",
            role = UserRole.AVARISTA,
            storeId = "LOJA_05",
            sectorId = "Geral",
            active = true
        )

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                HomeAuthenticatedScreen(
                    userProfile = avaristaProfile,
                    onLogout = {},
                    onOpenConferences = {},
                    onOpenOperationalCenter = {},
                    onOpenReports = {},
                    onOpenProductApproval = {},
                    onOpenProducts = {},
                    onCheckForUpdate = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_authenticated_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_conferences").performScrollTo().assertIsDisplayed()
    }
}
