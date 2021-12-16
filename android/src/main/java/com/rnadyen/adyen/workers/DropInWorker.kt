package com.apps.bridges.adyen.workers

import android.app.Activity
import android.content.Intent
import com.adyen.checkout.card.CardConfiguration
import com.adyen.checkout.components.model.payments.Amount
import com.adyen.checkout.core.api.Environment
import com.adyen.checkout.dropin.DropInConfiguration
import com.apps.bridges.adyen.services.AdyenDropInService
import com.apps.bridges.adyen.utils.FormatUtils


class DropInWorker {

  companion object {

    init {
      System.loadLibrary("keys")
    }

    fun setupDropInComponent(clientKey: String, isTestMode:Boolean,activity: Activity, amount: String, currencyCode: String, intent: Intent): DropInConfiguration {

      var adyenENV: Environment = Environment.EUROPE
      if (isTestMode) {
        adyenENV = Environment.TEST
      }

      var cardConfiguration = CardConfiguration.Builder(activity,clientKey)
        .setHolderNameRequired(true)
        .setShowStorePaymentField(true)
        .setEnvironment(adyenENV)
        .build()

      val dropInConfiguration = DropInConfiguration.Builder(activity,AdyenDropInService::class.java,clientKey)
        // When you're ready to accept live payments, change the value to one of our live environments.
        .addCardConfiguration(cardConfiguration)
        .setEnvironment(adyenENV)

      // Add Amount - Optional
      val _amount = Amount()
      // Optional. In this example, the Pay button will display 10 EUR.
      _amount.currency = currencyCode
      val amountDouble = amount.toDoubleOrNull()
      amountDouble?.let {
        var powerFractionDigits = FormatUtils(currencyCode).fractionDigits
        _amount.value = (amountDouble *  powerFractionDigits).toInt();
        dropInConfiguration.setAmount(_amount)
      }

      return dropInConfiguration.build()

    }
  }
}
