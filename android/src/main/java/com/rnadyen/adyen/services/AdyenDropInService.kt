package com.apps.bridges.adyen.services

import android.content.Intent
import com.adyen.checkout.card.CardComponentState
import com.adyen.checkout.components.ActionComponentData
import com.adyen.checkout.components.PaymentComponentState
import com.adyen.checkout.components.model.payments.request.PaymentComponentData
import com.adyen.checkout.core.log.LogUtil
import com.adyen.checkout.core.log.Logger
import com.adyen.checkout.core.model.toStringPretty
import com.adyen.checkout.dropin.service.DropInService
import com.adyen.checkout.dropin.service.DropInServiceResult
import com.apps.bridges.adyen.utils.ArrayToReadableArray
import com.apps.bridges.adyen.utils.JsonToReadableMap
import com.apps.bridges.adyen.utils.JsonToReadableMapWithKey
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.rnadyen.RnAdyenModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

interface AdyenDropInServiceDelegate {
  fun didSetPaymentWithData(map: WritableArray)
  fun didProvideAction(mapArray: WritableArray)
}

 enum class ACTION_SERVICE {
  ACTION,
   FINISH_DROP_IN,
   FINISH_WITH_ERROR
   ;

  val value: String
    get() {
      return when (this) {
        ACTION -> "action"
        FINISH_DROP_IN -> "finishDropIn"
        FINISH_WITH_ERROR -> "finishWithError"
      }
      return ""
    }
}

class AdyenDropInService : DropInService() {
  // We used static instance of AdyenBridge instead
  // -> waiting to get a solution for secod constructor for JonIntentService
  // val _delegate: AdyenDropInServiceDelegate? = delegate

  companion object {
    private val TAG = LogUtil.getTag()
  }

  override fun onPaymentsCallRequested(paymentComponentState: PaymentComponentState<*>, paymentComponentJson: JSONObject) {

    launch(Dispatchers.IO) {
      Logger.v(TAG, "onPaymentsCallRequested paymentComponentJson - ${paymentComponentJson.toStringPretty()}")
      val cardTypeValue = getCardTypeValue(paymentComponentState)
      sendDataToAPI(cardTypeValue, paymentComponentJson)
    }

  }

  override fun onDetailsCallRequested(actionComponentData: ActionComponentData, actionComponentJson: JSONObject) {
    launch(Dispatchers.IO) {
      Logger.d(TAG, "onDetailsCallRequested")
      sendDataWithActionToAPI(actionComponentJson)
    }
  }


  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

    intent?.let {
      val action = intent.getStringExtra(ACTION_SERVICE.ACTION.value)
      Logger.d(TAG, "Action from onStartCommand$action")
      action?.let {
          it1 -> setAction(it1)
      }

      //If finish with success Or
      val finishDropIn = intent.getBooleanExtra(ACTION_SERVICE.FINISH_DROP_IN.value,false)
      if(finishDropIn){
        this.finishDropIn()
      }

      val finishWithError = intent.getBooleanExtra(ACTION_SERVICE.FINISH_WITH_ERROR.value,false)

      if(finishWithError){
        handleError()
      }

      //Stop Self Service
      stopSelf()

    }
    return super.onStartCommand(intent, flags, startId)
  }


  //When I send Data from onPaymentsCallRequested
  @Throws(IOException::class)
  private fun sendDataToAPI(cardTypeValue: String = "CARTEBANCAIRE", paymentComponentDataJSON: JSONObject) {

    val paymentMethodKey = "paymentMethod"
    val paymentMethodJSO = paymentComponentDataJSON.getJSONObject(paymentMethodKey)

    val paymentMethodMap = JsonToReadableMapWithKey(paymentMethodKey, paymentMethodJSO)

    val storedPaymentMethodKey = "storePaymentMethod"
    val storedPaymentMethodJSO = paymentComponentDataJSON.getBoolean(storedPaymentMethodKey)

    //val browserInfoKey = "browserInfo"
    //val browserInfoKeyJSO = jsonObject.getString(browserInfoKey)

    var objectMap = Arguments.createMap()

    objectMap.putBoolean("storePaymentMethod", storedPaymentMethodJSO)
    objectMap.putString("cardType", cardTypeValue);
    //objectMap.putString("browserInfo",browserInfoKeyJSO)

    val dataDidSubmitData = arrayOf(paymentMethodMap.toMapObject, objectMap)

    val arrayReadableMaps = ArrayToReadableArray(dataDidSubmitData).toArray

    RnAdyenModule.adyenBridgeDelegate.didSetPaymentWithData(arrayReadableMaps)

  }

  // When I receive Action From Our API (called from Bridge)
  fun setAction(action: String) {
    Logger.d(TAG, "setAction")
    launch(Dispatchers.IO) {
      val dropInAction = DropInServiceResult.Action(action)
      dropInAction?.let {
        sendResult(dropInAction)
      }
    }
  }

  @Suppress("NestedBlockDepth")
  private fun handleError(error: String? = "Unknown Payment Provider Error") {
    launch(Dispatchers.IO) {
      sendResult(DropInServiceResult.Error(error))
    }
  }

  //When I send Data from onDetailsCallRequested
  @Throws(IOException::class)
  private fun sendDataWithActionToAPI(jsonObject: JSONObject) {
    val actionPaymentData = ActionComponentData.SERIALIZER.deserialize(jsonObject)

    val paymentData = actionPaymentData.paymentData
    val paymentDetail = actionPaymentData.details
    paymentDetail?.let {

      var provideActionWithElement = Arguments.createArray()

      // Put at Element 0 details as expected by React native project /!\
      val paymentDetailsDict: WritableMap = Arguments.createMap()
      paymentDetailsDict.putMap("details", JsonToReadableMap(paymentDetail).toMap)
      provideActionWithElement.pushMap(paymentDetailsDict)

      // Put at Element 1 paymentData as expected by React native project /!\
      val paymentDataDict: WritableMap = Arguments.createMap()
      paymentDataDict.putString("paymentData", paymentData)
      provideActionWithElement.pushMap(paymentDataDict)

      RnAdyenModule.adyenBridgeDelegate.didProvideAction(provideActionWithElement)

    }
  }

  fun finishDropIn() {
    launch(Dispatchers.IO) {
      sendResult(DropInServiceResult.Finished(ACTION_SERVICE.FINISH_DROP_IN.value))
    }
  }

  private fun getCardTypeValue(paymentComponentState: PaymentComponentState<*>): String {
    if (paymentComponentState is CardComponentState) {
      // a card payment is being made, handle accordingly
      val cardType = paymentComponentState.cardType
      cardType?.let {
        return cardType.txVariant
      }
    }

    return "";
  }

}
