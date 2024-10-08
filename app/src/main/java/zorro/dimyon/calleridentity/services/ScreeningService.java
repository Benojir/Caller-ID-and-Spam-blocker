package zorro.dimyon.calleridentity.services;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.atomic.AtomicBoolean;

import zorro.dimyon.calleridentity.helpers.CallsControlHelper;
import zorro.dimyon.calleridentity.helpers.ContactUtils;
import zorro.dimyon.calleridentity.helpers.NotificationHelper;

public class ScreeningService extends CallScreeningService {
    private static final String TAG = "MADARA";

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        boolean isIncoming = callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING;
        boolean isOutgoing = callDetails.getCallDirection() == Call.Details.DIRECTION_OUTGOING;

        Uri handle = callDetails.getHandle();
        String phoneNumber = handle.getSchemeSpecificPart();

        CallsControlHelper controlHelper = new CallsControlHelper(this, callDetails, phoneNumber);
        CallResponse.Builder response = new CallResponse.Builder();

        AtomicBoolean allowIncomingFloatingForContacts = new AtomicBoolean(preferences.getBoolean("is_incoming_floating_allowed_for_contacts_too", false));

        if (isIncoming) {

            if (preferences.getBoolean("reject_all_incoming_calls", false)) {
                response.setRejectCall(true);
                response.setDisallowCall(true);
                respondToCall(callDetails, response.build());

            } else {

                ContactUtils.getContactNameByPhoneNumber(this, phoneNumber, contactName -> {

                    boolean notInContacts = contactName.isEmpty();

                    if (preferences.getBoolean("reject_unknown_incoming_calls", false)) {

                        if (notInContacts) {
                            response.setRejectCall(true);
                            response.setDisallowCall(true);
                            respondToCall(callDetails, response.build());
                        } else {
                            if (allowIncomingFloatingForContacts.get()) { // showing floating window for saved contacts if allowIncomingFloatingForContacts is true
                                showFloatingCallerInfoWindow(null, phoneNumber, contactName);
                            }
                        }
                    } else {

                        if (preferences.getBoolean("block_all_spammers", false)) {

                            if (notInContacts) { // get incoming phone number's information when it is not saved in contacts

                                controlHelper.blockAllSpamCalls(response, (isSuccessful, callerInfo) -> {

                                    if (isSuccessful) {
                                        NotificationHelper.showBlockedCallNotification(this, callerInfo, phoneNumber); // showing notification for blocked spam calls
                                    } else { // if the incoming call is not a spam call then show the floating window
                                        showFloatingCallerInfoWindow(callerInfo, phoneNumber, contactName); // showing floating window for unsaved non spam calls
                                    }
                                });
                            } else { // if incoming phone number is saved in contacts then this codes block will be executed. No need to check for spam
                                if (allowIncomingFloatingForContacts.get()) { // showing floating window for saved contacts if allowIncomingFloatingForContacts is true
                                    showFloatingCallerInfoWindow(null, phoneNumber, contactName);
                                }
                            }

                        } else { // if block_all_spammers == false then this codes block will be executed

                            if (preferences.getBoolean("block_top_spammers", false)) {

                                if (notInContacts) { // get incoming phone number's information when it is not saved in contacts

                                    controlHelper.blockTopSpamCalls(response, (isSuccessful, callerInfo) -> {

                                        if (isSuccessful) {
                                            NotificationHelper.showBlockedCallNotification(this, callerInfo, phoneNumber); // showing notification for blocked spam calls
                                        } else { // if the incoming call is not a spam call then show the floating window
                                            showFloatingCallerInfoWindow(callerInfo, phoneNumber, contactName); // showing floating window for unsaved non spam calls
                                        }
                                    });

                                } else { // if incoming phone number is saved in contacts then this codes block will be executed. No need to check for spam
                                    if (allowIncomingFloatingForContacts.get()) { // showing floating window for saved contacts if allowIncomingFloatingForContacts is true
                                        showFloatingCallerInfoWindow(null, phoneNumber, contactName);
                                    }
                                }

                            } else { // showing floating window for both saved and unsaved contacts if both spam filtering options are turned off
                                if (allowIncomingFloatingForContacts.get()) {
                                    showFloatingCallerInfoWindow(null, phoneNumber, contactName);
                                } else {
                                    controlHelper.getCallerInfo(callerInfo -> showFloatingCallerInfoWindow(callerInfo, phoneNumber, contactName));
                                }
                            }
                        }
                    }
                });
            }
        }

        // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

        if (isOutgoing) {
            if (preferences.getBoolean("is_outgoing_floating_allowed_for_unknown_numbers", false)) { // showing floating window for unknown numbers if is_outgoing_floating_allowed_for_unknown_numbers == true
                ContactUtils.getContactNameByPhoneNumber(this, phoneNumber, contactName -> {
                    if (contactName.isEmpty()) {
                        controlHelper.getCallerInfo(callerInfo -> showFloatingCallerInfoWindow(callerInfo, phoneNumber, contactName));
                    }
                });
            }
        }
    }

//    ----------------------------------------------------------------------------------------------

    private void showFloatingCallerInfoWindow(JSONObject callerInfo, String phoneNumber, String contactName) {

        if (callerInfo == null) {
            String callerName = contactName;
            String callerProfileImageLink = "";
            boolean isSpamCall = false;
            String spamType = "";
            String spamScore = "";

            if (callerName.isEmpty()) {
                callerName = phoneNumber;
            }

            Intent intent = new Intent(this, PopupService.class);
            intent.putExtra("callerName", callerName);
            intent.putExtra("phoneNumber", phoneNumber);
            intent.putExtra("callerProfileImageLink", callerProfileImageLink);
            intent.putExtra("address", phoneNumber);
            intent.putExtra("isSpamCall", isSpamCall);
            intent.putExtra("spamType", spamType);
            intent.putExtra("spamScore", spamScore);
            startForegroundService(intent);

        } else {

            try {
                String callerName = callerInfo.getString("callerName");
                String callerProfileImageLink = "";
                String address = "";
                boolean isSpamCall = false;
                String spamType = "";
                String spamScore = "";

                if (callerInfo.has("callerProfileImageLink")) {
                    callerProfileImageLink = callerInfo.getString("callerProfileImageLink");
                }

                if (callerInfo.has("address")) {
                    address = callerInfo.getString("address");
                }

                if (callerInfo.has("isSpamCall")) {
                    isSpamCall = callerInfo.getBoolean("isSpamCall");
                }

                if (callerInfo.has("spamType")) {
                    spamType = callerInfo.getString("spamType");
                }

                if (callerInfo.has("spamScore")) {
                    spamScore = callerInfo.getString("spamScore");
                }

                Intent intent = new Intent(this, PopupService.class);
                intent.putExtra("callerName", callerName);
                intent.putExtra("phoneNumber", phoneNumber);
                intent.putExtra("callerProfileImageLink", callerProfileImageLink);
                intent.putExtra("address", address);
                intent.putExtra("isSpamCall", isSpamCall);
                intent.putExtra("spamType", spamType);
                intent.putExtra("spamScore", spamScore);
                startForegroundService(intent);

            } catch (JSONException e) {
                Log.e(TAG, "onScreenCall: ", e);
            }
        }
    }
}
