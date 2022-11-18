package app.passwordstore.util.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.passwordstore.crypto.GpgIdentifier
import app.passwordstore.crypto.KeyUtils
import app.passwordstore.crypto.PGPKeyManager
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.map
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PGPKeyListViewModel @Inject constructor(private val keyManager: PGPKeyManager) : ViewModel() {
  var keys: List<GpgIdentifier> by mutableStateOf(emptyList())

  init {
    updateKeySet()
  }

  fun updateKeySet() {
    viewModelScope.launch {
      when (
        val result =
          keyManager.getAllKeys().map { keys ->
            keys.mapNotNull { key -> KeyUtils.tryGetEmail(key) }
          }
      ) {
        is Ok -> keys = result.value
        is Err -> TODO()
      }
    }
  }

  fun deleteKey(identifier: GpgIdentifier) {
    viewModelScope.launch {
      keyManager.removeKey(identifier)
      updateKeySet()
    }
  }
}
