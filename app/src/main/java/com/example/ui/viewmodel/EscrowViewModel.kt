package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.dao.EscrowDao
import com.example.data.model.EscrowTransaction
import com.example.data.repository.EBomaRepository
import com.example.domain.rules.EBomaRulesEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing escrow states, guest freeze, and damage deposit releases.
 * Injected with both EBomaRepository and EscrowDao via Hilt.
 */
@HiltViewModel
class EscrowViewModel @Inject constructor(
    private val repository: EBomaRepository,
    private val escrowDao: EscrowDao
) : ViewModel() {

    // Real-time observation of frozen or disputed escrows
    val frozenOrDisputedEscrows: StateFlow<List<EscrowTransaction>> =
        escrowDao.observeFrozenOrDisputedEscrows().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _lastOperationMessage = MutableStateFlow<String?>(null)
    val lastOperationMessage: StateFlow<String?> = _lastOperationMessage.asStateFlow()

    fun recordCheckIn(bookingId: String) {
        viewModelScope.launch {
            try {
                repository.recordCheckInQrScan(bookingId)
                _lastOperationMessage.value = "Check-in QR scanned successfully. 24h escrow timer started."
            } catch (e: Exception) {
                _lastOperationMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun freezeEscrow(bookingId: String, reason: String) {
        viewModelScope.launch {
            try {
                repository.freezeEscrowByGuest(bookingId, reason)
                _lastOperationMessage.value = "Escrow held and frozen by guest dispute."
            } catch (e: Exception) {
                _lastOperationMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun confirmCleanCheckout(bookingId: String, isGuest: Boolean, confirmed: Boolean) {
        viewModelScope.launch {
            try {
                val (_, escrow) = repository.confirmCheckoutCleanliness(bookingId, isGuest, confirmed)
                val status = if (escrow.damage_deposit_status == "released") {
                    "Both confirmed: Damage deposit released!"
                } else {
                    "Confirmation recorded. Waiting for mutual sign-off."
                }
                _lastOperationMessage.value = status
            } catch (e: Exception) {
                _lastOperationMessage.value = "Error: ${e.message}"
            }
        }
    }
}
