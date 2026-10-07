package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.dao.ListingDao
import com.example.data.model.Listing
import com.example.data.repository.EBomaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for staycation browsing and verified property searches.
 * Injected with both EBomaRepository and ListingDao via Hilt.
 */
@HiltViewModel
class StaycationViewModel @Inject constructor(
    private val repository: EBomaRepository,
    private val listingDao: ListingDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Verified & physically inspected listings (Rule 1)
    val verifiedListings: StateFlow<List<Listing>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            listingDao.observeVerifiedGuestListings()
        } else {
            listingDao.searchVerifiedGuestListings(query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun seedDataIfEmpty() {
        viewModelScope.launch {
            repository.seedKisumuStaycationDataIfEmpty()
        }
    }
}
