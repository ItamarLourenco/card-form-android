package com.mercadolibre.android.cardform.presentation.viewmodel.webview

import android.os.Bundle
import androidx.lifecycle.LiveData
import com.mercadolibre.android.cardform.base.BaseViewModel
import com.mercadolibre.android.cardform.base.getOrElse
import com.mercadolibre.android.cardform.domain.*
import com.mercadolibre.android.cardform.domain.FinishInscriptionUseCase
import com.mercadolibre.android.cardform.domain.InscriptionUseCase
import com.mercadolibre.android.cardform.presentation.model.ScreenState
import com.mercadolibre.android.cardform.presentation.model.TokenizeAssociationModel
import com.mercadolibre.android.cardform.presentation.model.WebUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val USER_FULL_NAME_EXTRA = "user_full_name"
private const val USER_IDENTIFICATION_NUMBER_EXTRA = "user_identification_number"
private const val USER_IDENTIFICATION_TYPE_EXTRA = "user_identification_type"

internal class CardFormWebViewModel(
    private val inscriptionUseCase: InscriptionUseCase,
    private val finishInscriptionUseCase: FinishInscriptionUseCase,
    private val tokenizeWebCardUseCase: TokenizeWebCardUseCase,
    private val associatedCardUseCase: AssociatedCardUseCase,
    private val liveDataProvider: CardFormWebViewLiveDataProvider = CardFormWebViewLiveDataProvider,
    private val flowRetryProvider: FlowRetryProvider = FlowRetryProvider
) : BaseViewModel() {

    val webUiStateLiveData: LiveData<WebUiState>
        get() = liveDataProvider.webUiStateLiveData

    val screenStateLiveData: LiveData<ScreenState>
        get() = liveDataProvider.screenStateMutableLiveData

    val loadWebViewLiveData: LiveData<Triple<String, String, ByteArray>>
        get() = liveDataProvider.loadWebViewMutableLiveData

    val canGoBackViewLiveData: LiveData<Boolean>
        get() = liveDataProvider.canGoBackMutableLiveData

    val cardResultLiveData: LiveData<String>
        get() = liveDataProvider.cardResultMutableLiveData

    private var userFullName = ""
    private var userIdentificationNumber = ""
    private var userIdentificationType = ""

    override fun recoverFromBundle(bundle: Bundle) {
        with(bundle) {
            getString(USER_FULL_NAME_EXTRA)?.let { userFullName = it }
            getString(USER_IDENTIFICATION_NUMBER_EXTRA)?.let { userIdentificationNumber = it }
            getString(USER_IDENTIFICATION_TYPE_EXTRA)?.let { userIdentificationType = it }
        }
    }

    override fun storeInBundle(bundle: Bundle) {
        with(bundle) {
            putString(USER_FULL_NAME_EXTRA, userFullName)
            putString(USER_IDENTIFICATION_NUMBER_EXTRA, userIdentificationNumber)
            putString(USER_IDENTIFICATION_TYPE_EXTRA, userIdentificationType)
        }
    }

    fun initInscription() {
        inscriptionUseCase.execute(Unit,
            success = {
                userFullName = it.fullName
                userIdentificationNumber = it.identifierNumber
                userIdentificationType = it.identifierType
                liveDataProvider.loadWebViewMutableLiveData.value =
                    Triple(it.redirectUrl, it.urlWebPay, it.token)
            },
            failure = {
                flowRetryProvider.setRetryFunction {
                    showProgressStartScreen()
                    initInscription()
                }
                showErrorState()
            })
    }

    fun finishInscription(token: String) {
        finishInscriptionUseCase.execute(token,
            success = {
                tokenizeAndAssociateCard(
                    TokenizeAssociationModel(
                        it.cardNumberId,
                        it.truncCardNumber,
                        it.expirationMonth,
                        it.expirationYear,
                        it.cardNumberLength,
                        it.issuerId,
                        it.paymentMethodId,
                        it.paymentMethodType
                    )
                )
            },
            failure = {
                flowRetryProvider.setRetryFunction {
                    showProgressBackScreen()
                    finishInscription(token)
                }
                showErrorState()
            })
    }

    private fun tokenizeAndAssociateCard(model: TokenizeAssociationModel) {
        CoroutineScope(contextProvider.Default).launch {
            lateinit var cardTokenId: String
            getCardToken(model)?.let {
                cardTokenId = it
                getCardAssociationId(cardTokenId, model)
            }?.let { cardAssociationId ->
                withContext(Dispatchers.Main) {
                    sendCardResult(cardAssociationId)
                }
            } ?: let {
            }
        }
    }

    private suspend fun getCardToken(model: TokenizeAssociationModel) = run {
        tokenizeWebCardUseCase
            .execute(
                TokenizeWebCardParam(
                    model.cardNumberId,
                    model.truncCardNumber,
                    userFullName,
                    userIdentificationNumber,
                    userIdentificationType,
                    model.expirationMonth,
                    model.expirationYear,
                    model.cardNumberLength
                )
            )
            .getOrElse { throwable ->
                flowRetryProvider.setRetryFunction {
                    showProgressBackScreen()
                    tokenizeAndAssociateCard(model)
                }
                showErrorState()
            }
    }

    private suspend fun getCardAssociationId(
        cardTokenId: String,
        model: TokenizeAssociationModel
    ) = run {
        associatedCardUseCase.execute(
            AssociatedCardParam(
                cardTokenId,
                model.paymentMethodId,
                model.paymentMethodType,
                model.issuerId
            )
        ).getOrElse { throwable ->
            flowRetryProvider.setRetryFunction {
                showProgressBackScreen()
                tokenizeAndAssociateCard(model)
            }
            showErrorState()
        }
    }

    private fun sendCardResult(cardId: String) {
        liveDataProvider.cardResultMutableLiveData.value = cardId
    }

    fun retry() {
        flowRetryProvider.retry()
    }

    fun showSuccessState() {
        liveDataProvider.webUiStateLiveData.value = WebUiState.WebSuccess
    }

    private fun showErrorState() {
        liveDataProvider.webUiStateLiveData.postValue(WebUiState.WebError)
    }

    fun showWebViewScreen() {
        liveDataProvider.screenStateMutableLiveData.value = ScreenState.WebViewState
    }

    fun showProgressBackScreen() {
        liveDataProvider.screenStateMutableLiveData.postValue(ScreenState.ProgressState)
        liveDataProvider.webUiStateLiveData.postValue(WebUiState.WebProgressBack)
    }

    fun showProgressStartScreen() {
        liveDataProvider.screenStateMutableLiveData.value = ScreenState.ProgressState
        liveDataProvider.webUiStateLiveData.value = WebUiState.WebProgressStart
    }

    fun canGoBack(canGoBack: Boolean) {
        liveDataProvider.canGoBackMutableLiveData.value = canGoBack
    }
}