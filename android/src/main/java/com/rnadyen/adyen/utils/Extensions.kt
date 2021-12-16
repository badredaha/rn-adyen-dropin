package com.apps.bridges.adyen.utils

import com.adyen.checkout.components.util.CheckoutCurrency
import com.adyen.checkout.core.exception.CheckoutException
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import org.json.JSONException
import org.json.JSONObject
import java.util.*


class JsonToReadableMap(json: JSONObject) {
    var toMap: WritableMap = Arguments.createMap()
    val map = json.keys().forEach { value ->
      toMap.putString(value, json.optString(value))
    }
}

class JsonToReadableMapWithKey(key:String, json: JSONObject) {
  var toMap: WritableMap = Arguments.createMap()

  val mapKeys = json.keys().forEach { value ->
    toMap.putString(value, json.optString(value))
  }

  var toMapObject: WritableMap = Arguments.createMap()
  val map = toMapObject.putMap(key,toMap)

}

class ArrayToReadableArray(writableMap: Array<WritableMap>) {
  var toArray = Arguments.createArray()
  val map = writableMap.forEach { value ->
    toArray.pushMap(value)
  }
}

class FormatUtils(currencyCode: String){
  companion object{
    val TAG = LogUtil.getTag()
  }

  val fractionDigits = getPowerFractionDigits(currencyCode)

  private fun getPowerFractionDigits(currencyCode: String): Double {
    val fractions = getFractionDigits(currencyCode)
    if (fractions > 1){
      return Math.pow(10.0,fractions.toDouble())
    }

    return 1.0
  }
  private fun getFractionDigits(currencyCode: String): Int{
      val normalizedCurrencyCode = currencyCode.replace("[^A-Z]".toRegex(), "").toUpperCase(Locale.ROOT)
      try {
        val checkoutCurrency = CheckoutCurrency.find(normalizedCurrencyCode)
        return checkoutCurrency.fractionDigits
      } catch (e: CheckoutException) {
        Logger.e(TAG, "$normalizedCurrencyCode is an unsupported currency. Falling back to information from java.util.Currency.", e)
      }
      return try {
        val currency: Currency = Currency.getInstance(normalizedCurrencyCode)
        Math.max(currency.getDefaultFractionDigits(), 0)
      } catch (e: IllegalArgumentException) {
        Logger.e(TAG, "Could not determine fraction digits for $normalizedCurrencyCode", e)
        0
      }
    }
}
