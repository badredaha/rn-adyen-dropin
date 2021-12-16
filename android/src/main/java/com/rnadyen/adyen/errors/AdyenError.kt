package com.apps.bridges.adyen.errors

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap

enum class AdyenError {
  dropInCanceled,
  challengceCanceled,
  redirectionCanceled,
  error

}

class AdyenErrorHandler{
  companion object {
    fun getValue(error:AdyenError):ReadableMap{
      var messageError = "error"
      messageError = when(error){
        AdyenError.dropInCanceled -> ""
        AdyenError.challengceCanceled -> ""
        AdyenError.redirectionCanceled -> ""
        AdyenError.error -> "Unknown Error"
      }

      val messageToMap: WritableMap = Arguments.createMap()
      messageToMap.putString("error",messageError)

      return messageToMap
    }
  }

}
