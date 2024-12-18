package com.plcoding.cryptotracker.crypto.presentation.coin_list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plcoding.cryptotracker.core.domain.util.onError
import com.plcoding.cryptotracker.core.domain.util.onSuccess
import com.plcoding.cryptotracker.crypto.domain.CoinDataSource
import com.plcoding.cryptotracker.crypto.presentation.model.CoinUi
import com.plcoding.cryptotracker.crypto.presentation.model.coin_detail.DataPoint
import com.plcoding.cryptotracker.crypto.presentation.model.toCoinUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CoinListViewModel(
    private val coinDataSource: CoinDataSource
) : ViewModel() {

    private var isInitialDataLoaded = false

    private val _uiState = MutableStateFlow(CoinListState())
    val uiState = _uiState
        .onStart {
            if (!isInitialDataLoaded) {
                isInitialDataLoaded = true
                loadCoins()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = CoinListState()
        )

    private val _uiEvent = Channel<CoinListEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onUiAction(action: CoinListAction) {
        when (action) {
            is CoinListAction.OnCoinClick -> {
                selectedCoin(action.coinUi)
            }
        }
    }

    private fun loadCoins() = viewModelScope.launch {
        _uiState.update {
            it.copy(
                isLoading = true
            )
        }

        coinDataSource
            .getCoins()
            .onSuccess { coins ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        coins = coins.map { it.toCoinUi() }
                    )
                }
            }.onError { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false
                    )
                }
                _uiEvent.send(
                    CoinListEvent.Error(error)
                )
            }
    }

    private fun selectedCoin(coinUi: CoinUi) = viewModelScope.launch {
        _uiState.update {
            it.copy(
                selectedCoin = coinUi
            )
        }

        coinDataSource.getCoinHistory(
            coinId = coinUi.id,
            start = ZonedDateTime.now().minusDays(5),
            end = ZonedDateTime.now()
        ).onSuccess { history ->
            val dataPoints = history
                .sortedBy { it.dateTime }
                .map {
                    DataPoint(
                        x = it.dateTime.hour.toFloat(),
                        y = it.priceUsd.toFloat(),
                        xLabel = DateTimeFormatter
                            .ofPattern("ha\nM/d")
                            .format(it.dateTime)
                    )
                }

            _uiState.update {
                it.copy(
                    selectedCoin = it.selectedCoin?.copy(
                        coinPriceHistory = dataPoints
                    )
                )
            }
        }.onError { error ->
            _uiEvent.send(CoinListEvent.Error(error))
        }
    }
}
