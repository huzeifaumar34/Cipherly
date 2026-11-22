package app.cipherly.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cipherly.data.db.VaultSession
import app.cipherly.domain.model.ItemCheck
import app.cipherly.domain.model.ItemRules
import app.cipherly.domain.model.ItemType
import app.cipherly.domain.model.VaultItem
import app.cipherly.vault.VaultRepository
import app.cipherly.vault.VaultState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface ItemsScreen {
    data object List : ItemsScreen
    data object Edit : ItemsScreen
    data object Security : ItemsScreen
}

enum class ItemError { TITLE_REQUIRED, TOO_LONG, SAVE_FAILED }

@HiltViewModel
class ItemsViewModel @Inject constructor(
    private val session: VaultSession,
    vault: VaultRepository,
) : ViewModel() {

    private val _screen = MutableStateFlow<ItemsScreen>(ItemsScreen.List)
    val screen: StateFlow<ItemsScreen> = _screen.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** The item being edited, or null when creating a new one. */
    private val _editing = MutableStateFlow<VaultItem?>(null)
    val editing: StateFlow<VaultItem?> = _editing.asStateFlow()

    private val _error = MutableStateFlow<ItemError?>(null)
    val error: StateFlow<ItemError?> = _error.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<VaultItem>> = vault.state
        .flatMapLatest { state ->
            if (state == VaultState.UNLOCKED) {
                _query.flatMapLatest { q ->
                    session.observe(q.trim()).catch { emit(emptyList()) }
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Never keep decrypted data or UI state around once the vault is locked.
        viewModelScope.launch {
            vault.state.collect { state ->
                if (state != VaultState.UNLOCKED) reset()
            }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun openNew() {
        _editing.value = null
        _error.value = null
        _screen.value = ItemsScreen.Edit
    }

    fun openEdit(id: String) {
        viewModelScope.launch {
            _editing.value = try {
                session.get(id)
            } catch (e: Exception) {
                null
            }
            _error.value = null
            _screen.value = ItemsScreen.Edit
        }
    }

    fun openSecurity() {
        _screen.value = ItemsScreen.Security
    }

    fun back() {
        _error.value = null
        _editing.value = null
        _screen.value = ItemsScreen.List
    }

    fun save(
        type: ItemType,
        title: String,
        username: String,
        secret: String,
        url: String,
        notes: String,
    ) {
        when (ItemRules.check(title, username, secret, url, notes)) {
            ItemCheck.TITLE_REQUIRED -> { _error.value = ItemError.TITLE_REQUIRED; return }
            ItemCheck.TOO_LONG -> { _error.value = ItemError.TOO_LONG; return }
            ItemCheck.OK -> Unit
        }
        val now = System.currentTimeMillis()
        val existing = _editing.value
        val item = VaultItem(
            id = existing?.id ?: UUID.randomUUID().toString(),
            type = type,
            title = title.trim(),
            username = username,
            secret = secret,
            url = url,
            notes = notes,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            try {
                session.save(item)
                back()
            } catch (e: Exception) {
                _error.value = ItemError.SAVE_FAILED
            }
        }
    }

    fun delete() {
        val id = _editing.value?.id ?: return
        viewModelScope.launch {
            try {
                session.delete(id)
                back()
            } catch (e: Exception) {
                _error.value = ItemError.SAVE_FAILED
            }
        }
    }

    private fun reset() {
        _screen.value = ItemsScreen.List
        _editing.value = null
        _query.value = ""
        _error.value = null
    }
}
