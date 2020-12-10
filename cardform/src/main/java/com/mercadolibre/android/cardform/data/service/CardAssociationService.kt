package com.mercadolibre.android.cardform.data.service

import com.mercadolibre.android.cardform.BuildConfig
import com.mercadolibre.android.cardform.data.model.body.AssociatedCardBody
import com.mercadolibre.android.cardform.data.model.response.AssociatedCard
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface CardAssociationService {
    @POST("/{environment}/px_mobile/v1/card")
    suspend fun associateCard(
        @Query("access_token") accessToken : String,
        @Body associatedCardBody: AssociatedCardBody,
        @Path("environment") environment : String = BuildConfig.API_ENVIRONMENT
    ): Response<AssociatedCard>
}