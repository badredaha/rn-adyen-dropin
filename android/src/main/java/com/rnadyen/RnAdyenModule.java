package com.rnadyen;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;

import com.adyen.checkout.components.model.PaymentMethodsApiResponse;
import com.adyen.checkout.core.log.Logger;
import com.adyen.checkout.dropin.DropIn;
import com.adyen.checkout.dropin.DropInConfiguration;
import com.adyen.checkout.dropin.DropInResult;
import com.apps.bridges.adyen.errors.AdyenError;
import com.apps.bridges.adyen.errors.AdyenErrorHandler;
import com.apps.bridges.adyen.services.ACTION_SERVICE;
import com.apps.bridges.adyen.services.AdyenDropInService;
import com.apps.bridges.adyen.services.AdyenDropInServiceDelegate;
import com.apps.bridges.adyen.workers.DropInWorker;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

public class RnAdyenModule extends ReactContextBaseJavaModule implements AdyenDropInServiceDelegate, ActivityEventListener {

  private static final String TAG = "RnAdyenModule";

  private Callback didCancelChallenge = null;
  private Callback didProvideAction = null;
  private Callback didSetPaymentWithData = null;
  private Callback didSetPaymentWithError = null;
  public static RnAdyenModule adyenBridgeDelegate;

  public RnAdyenModule(@NonNull ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(this);
    //Use static instance of AdyenBridge instead of passing delegate to AdyenDropInService
    // we can't use second custon constructor for AdyenDropInService 'JobIntentService'
    adyenBridgeDelegate = this;
    //
  }

  /**
   * ShowDropIn
   */
  private void showDropIn(String clientKey, boolean isTestMode,String paymentMethods, String amount, String currencyCode) {

    Logger.setLogcatLevel(Log.DEBUG);

    Intent resultIntent = new Intent(this.getCurrentActivity(), this.getCurrentActivity().getClass());
    resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

    DropInConfiguration dropInConfiguration = DropInWorker.Companion.setupDropInComponent(clientKey,isTestMode,this.getCurrentActivity(), amount, currencyCode, resultIntent);
    try {
      PaymentMethodsApiResponse paymentMethodsApiResponse = PaymentMethodsApiResponse.SERIALIZER.deserialize(new JSONObject(paymentMethods));
      DropIn.startPayment(this.getCurrentActivity(), paymentMethodsApiResponse, dropInConfiguration, resultIntent);
    } catch (JSONException jsoe) {
      Log.d(TAG, "JSONException " + jsoe.getMessage());
    }
  }

  /**
   * Dismiss if Payment Fail or If the screen is dismissed
   */
  @ReactMethod
  void hideDropInComponent() {
    Log.d(TAG, "Need to hide DropIn");

    Intent intent  = new Intent(this.getCurrentActivity(), AdyenDropInService.class);
    intent.putExtra(ACTION_SERVICE.FINISH_DROP_IN.getValue(), true);
    bindServiceWithIntent(intent);

    resetCallbacksErrors();
  }

  /**
   * Set PaymentMethods
   */
  @ReactMethod
  void setPaymentMethods(String clientKey, boolean isTestMode,String paymentMethods, String amount, String currencyCode, Callback result, Callback failure) {

    resetCallbacksErrors();
    this.showDropIn(clientKey,isTestMode,paymentMethods, amount, currencyCode);

    this.didSetPaymentWithData = result;
    this.didSetPaymentWithError = failure;

  }

  /**
   * Set challenge received from API LHG
   */
  @ReactMethod
  void challenge(String action, Callback resultChallenge, Callback errorChallenge) {

    resetCallbacksErrors();
    setAction(action);

    this.didProvideAction = resultChallenge;
    this.didCancelChallenge = errorChallenge;

  }

  private void resetChallengeCallbacks(){
    this.didProvideAction = null;
    this.didCancelChallenge = null;
  }

  private void setAction(String action){
    Logger.d(TAG, "setAction");

    Intent intent  = new Intent(this.getCurrentActivity(), AdyenDropInService.class);
    intent.putExtra(ACTION_SERVICE.ACTION.getValue(), action);
    bindServiceWithIntent(intent);

  }

  @Override
  public void didSetPaymentWithData(@NotNull WritableArray map) {
    if (didSetPaymentWithData != null) {
      this.didSetPaymentWithData.invoke(map);
      return;
    }

    handleErrorWithMessage();
  }
  @Override
  public void didProvideAction(@NotNull WritableArray mapArray) {
    if (didProvideAction != null) {
      didProvideAction.invoke(mapArray);
    }
  }

  /**
   * Dismiss if Payment Success
   */
  @ReactMethod
  void paymentWithSuccess() {
    Log.d(TAG, "paymentWithSuccess");

    Intent intent  = new Intent(this.getCurrentActivity(), AdyenDropInService.class);
    intent.putExtra(ACTION_SERVICE.FINISH_DROP_IN.getValue(), true);
    bindServiceWithIntent(intent);

    resetCallbacksErrors();
  }

  private void resetSetPaymentMethodCallbacks(){
    this.didSetPaymentWithData = null;
    this.didSetPaymentWithError = null;
  }

  private void resetCallbacksErrors() {
    resetSetPaymentMethodCallbacks();
    resetChallengeCallbacks();
  }

  private void handleCallbacksErrors() {
    //TODO: Refacto to Listener
    Callback cancelChallenge = this.didCancelChallenge;
    if (cancelChallenge != null) {
      cancelChallenge.invoke(AdyenErrorHandler.Companion.getValue(AdyenError.challengceCanceled));
      resetCallbacksErrors();
      return;
    }

    Callback errorDropIn = this.didSetPaymentWithError;
    if (errorDropIn != null) {
      errorDropIn.invoke(AdyenErrorHandler.Companion.getValue(AdyenError.dropInCanceled));
      resetCallbacksErrors();
      return;
    }
  }

  private void handleErrorWithMessage(){
    Intent intent  = new Intent(this.getCurrentActivity(), AdyenDropInService.class);
    intent.putExtra(ACTION_SERVICE.FINISH_WITH_ERROR.getValue(),true);
    bindServiceWithIntent(intent);
  }

  private void bindServiceWithIntent(final Intent intent){
    final Activity adyenActivity = this.getCurrentActivity();
    adyenActivity.runOnUiThread(new Runnable() {
      @Override
      public void run() {
        adyenActivity.startService(intent);
      }
    });
  }

  @NonNull
  @Override
  public String getName() {
    return "RnAdyenModule";
  }


  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent intent) {
    Logger.d(TAG, "onActivityResult");

    DropInResult dropInResult = DropIn.handleActivityResult(requestCode, resultCode, intent);

    if (dropInResult == null){
      handleCallbacksErrors();
      return;
    }

    if (dropInResult instanceof DropInResult.CancelledByUser){
      hideDropInComponent();
    }

    if (dropInResult instanceof DropInResult.Error){
      handleCallbacksErrors();
    }

    if (dropInResult instanceof DropInResult.Finished){
      paymentWithSuccess();
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    this.getCurrentActivity().setIntent(intent);
  }
}
