package com.example.exelgramm.ui.auth

import com.example.exelgramm.data.repository.AuthRepository
import com.example.exelgramm.domain.model.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Fake AuthRepository ---
    private class FakeAuthRepository(
        private val initialState: AuthState = AuthState(),
        private val loginResult: Boolean = true,
    ) : AuthRepository {
        val _state = MutableStateFlow(initialState)
        override val authState: Flow<AuthState> = _state

        override suspend fun login(username: String, password: String): Boolean = loginResult
        override suspend fun register(username: String, password: String) {}
        override suspend fun logout() {}
    }

    @Test
    fun `submit with blank username emits ShowError`() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())
        val effects = mutableListOf<LoginEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }

        vm.submit("", "password123")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(effects.any { it is LoginEffect.ShowError })
        job.cancel()
    }

    @Test
    fun `submit with blank password emits ShowError`() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())
        val effects = mutableListOf<LoginEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }

        vm.submit("username", "")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(effects.any { it is LoginEffect.ShowError })
        job.cancel()
    }

    @Test
    fun `register with short password emits ShowError`() = runTest {
        val vm = LoginViewModel(FakeAuthRepository(AuthState(isRegistered = false)))
        val effects = mutableListOf<LoginEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }

        vm.submit("user", "abc")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(effects.any { it is LoginEffect.ShowError })
        job.cancel()
    }

    @Test
    fun `register with valid inputs emits NavigateToMain`() = runTest {
        val vm = LoginViewModel(FakeAuthRepository(AuthState(isRegistered = false)))
        val effects = mutableListOf<LoginEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }

        vm.submit("newuser", "validpassword")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(effects.any { it is LoginEffect.NavigateToMain })
        job.cancel()
    }

    @Test
    fun `login with correct credentials emits NavigateToMain`() = runTest {
        val vm = LoginViewModel(
            FakeAuthRepository(
                initialState = AuthState(username = "user", isRegistered = true),
                loginResult = true,
            ),
        )
        val effects = mutableListOf<LoginEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }

        vm.submit("user", "correctpass")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(effects.any { it is LoginEffect.NavigateToMain })
        job.cancel()
    }

    @Test
    fun `login accepts any credentials until Google auth is enabled`() = runTest {
        val vm = LoginViewModel(
            FakeAuthRepository(
                initialState = AuthState(username = "user", isRegistered = true),
                loginResult = false,
            ),
        )
        val effects = mutableListOf<LoginEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }

        vm.submit("otheruser", "newpassword")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(effects.any { it is LoginEffect.NavigateToMain })
        job.cancel()
    }

    @Test
    fun `init emits NavigateToMain when already logged in`() = runTest {
        val vm = LoginViewModel(FakeAuthRepository(AuthState(isLoggedIn = true, isRegistered = true)))
        val effects = mutableListOf<LoginEffect>()
        val job = launch { vm.effects.collect { effects.add(it) } }

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(effects.any { it is LoginEffect.NavigateToMain })
        job.cancel()
    }
}
