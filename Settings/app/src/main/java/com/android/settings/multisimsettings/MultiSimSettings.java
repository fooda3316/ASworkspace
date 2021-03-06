/*
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *    Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *    Redistributions in binary form must reproduce the above
 *    copyright notice, this list of conditions and the following
 *    disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *    Neither the name of The Linux Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.settings.multisimsettings;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;

import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import android.telephony.MSimTelephonyManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Switch;

import com.android.internal.telephony.MSimConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.R;
import com.codeaurora.telephony.msim.MSimPhoneFactory;
import com.codeaurora.telephony.msim.SubscriptionManager;

public class MultiSimSettings extends PreferenceActivity {
    private static final String TAG = "MultiSimSettings";

    private static final String[] KEY_PREFERRED_SUBSCRIPTION_LIST = {"voice_list", "data_list",
        "sms_list"};

    private static final String KEY_MULTI_SIM_SETTINGS = "multi_sim_settings";
    private static final String KEY_CONFIG_SUB = "config_sub";
    private static final String CONFIG_SUB = "CONFIG_SUB";
    private static final String KEY_SIM_ONE_EBABLER = "sim_one_enabler_key";
    private static final String KEY_SIM_TWO_EBABLER = "sim_two_enabler_key";

    private PreferredSubscriptionListPreference[] mPreferredSubLists;
    private CheckBoxPreference showDurationCheckBox;
    private static final String [] KEY_PREFERRED_SUBSCRIPTION_LIST_NO_DATA = {"voice_list",
        "sms_list"};
    private static final String KEY_PREFERRED_SUBSCRIPTION_SETTINGS =
            "preferred_subscription_settings";
    private static final String  KEY_PREFERRED_SUBSCRIPTION_LIST__DATA = "data_list";
    private static final String  PREFERRED_SUB_DATA_LIST_VALUE_SLOT_ONE = "0";
    private static final String [] PREFERRED_SUB_DATA_LIST_ENTRY_SLOT_ONE = {"SLOT1"};

    private PreferenceScreen mConfigSub;
    private static MultiSimEnablerPreference mSimOneEnabler,mSimTwoEnabler;
    private boolean disableDataSub2 = false;
    private Switch mSwitchSub1,mSwitchSub2;

    private MultiSimDialog mDisableEnableProgressDialog = null;
    private MultiSimDialog mSetDataSubProgressDialog = null;
    private MultiSimDialog mDisableEnableAlertDialog = null;
    private MultiSimDialog mSetDataSubAlertDialog = null;
    private boolean isPause = false;

    private static final int RESUME = 1;
    private static final int SHOW_PROGRESSDIALOG = 2;
    private static final int DISMISS_PROGRESSDIALOG = 3;
    private static final int SHOW_ALERTDIALOG = 4;
    private static final int SHOW_DISABLE_ENABLE_RESULT_ALERTDIALOG = 5;
    private static final int SHOW_DISABLE_ENABLE_PROGRESSDIALOG = 7;
    // time out to dismiss progress dialog
    private static final int EVENT_PROGRESS_DLG_TIME_OUT = 6;
    // 30 seconds for progress dialog time out
    private static final int PROGRESS_DLG_TIME_OUT = 45000;

    private static final int SET_DATA_SUB_PROGRESSDIALOG = 0;
    private static final int DISABLE_ENABLE_PROGRESSDIALOG = 1;
    private static final int SET_DATA_SUB_ALERTDIALOG = 2;
    private static final int DISABLE_ENABLE_RESULT_ALERTDIALOG = 3;

    private static final String MSUBID_STR = "mSubId";
    private static final String MENABLED_STR = "mEnabled";
    private static final String MESSAGE_STR = "message";
    private static final String NEEDDISMISS_STR = "needDismiss";

    private static final String PROP_DISABLE_SUB2 = "persist.env.data.disable.sub2";
    private static final String MCC_CHINA = "460";
    private static final String MCC_MACAU = "455";

    //save states dialog whether need reshow.
    private static boolean mNeedReshowAlertDialog, mNeedReshowSetDataSubProDia,
        mNeedReshowDisEnableProDia;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG,"msg.what = "+msg.what);
            switch(msg.what){
                case RESUME:
                    Bundle bundle = msg.getData();
                    Boolean needDismiss = bundle.getBoolean(NEEDDISMISS_STR);
                    if(needDismiss){
                        if(mHandler.hasMessages(EVENT_PROGRESS_DLG_TIME_OUT))
                            mHandler.removeMessages(EVENT_PROGRESS_DLG_TIME_OUT);
                        dismissProgressDialog(DISABLE_ENABLE_PROGRESSDIALOG);
                    }
                    resume();
                    break;
                case SHOW_PROGRESSDIALOG:
                    showProgressDialog(SET_DATA_SUB_PROGRESSDIALOG, null);
                    break;
                case SHOW_DISABLE_ENABLE_PROGRESSDIALOG:
                    showProgressDialog(DISABLE_ENABLE_PROGRESSDIALOG, msg);
                    break;
                case DISMISS_PROGRESSDIALOG:
                    dismissProgressDialog(SET_DATA_SUB_PROGRESSDIALOG);
                    break;
                case SHOW_ALERTDIALOG:
                    String dataChangedMessage = (String)msg.obj;
                    showDialog(SET_DATA_SUB_ALERTDIALOG, dataChangedMessage, -1);
                    break;
                case SHOW_DISABLE_ENABLE_RESULT_ALERTDIALOG:
                    Bundle b = msg.getData();
                    String disEnableMessage = b.getString(MESSAGE_STR);
                    int subId = b.getInt(MSUBID_STR);
                    showDialog(DISABLE_ENABLE_RESULT_ALERTDIALOG, disEnableMessage, subId);
                    break;
                case EVENT_PROGRESS_DLG_TIME_OUT:
                    dismissProgressDialog(DISABLE_ENABLE_PROGRESSDIALOG);
                    resume();
                    break;
                default:
                    break;
            }
        }
    };

    private void initPreferences() {

        // if property persist.env.data.disable.sub2 is true , disable data
        // selection for SUB2.
        disableDataSub2 = false;
        if (SystemProperties.getBoolean(PROP_DISABLE_SUB2,false)) {
            Log.d(TAG,"prop persist.env.data.enable.sub2 true");
            boolean inChina = true;
            if (SubscriptionManager.getInstance().isSubActive(MSimConstants.SUB2)) {
                String operatorNumber = MSimPhoneFactory.getPhone(MSimConstants.SUB2).getServiceState()
                        .getOperatorNumeric();
                Log.d(TAG,"operatorNumber: " + operatorNumber);
                if (null != operatorNumber && operatorNumber.length() >= 3) {
                    String mcc = (String) operatorNumber.subSequence(0, 3);
                    // China mainland and Macau
                    if (!mcc.equals(MCC_CHINA) && !mcc.equals(MCC_MACAU)) {
                        inChina = false;
                    }
                }
            }
            if (inChina) {
                disableDataSub2 = true;
            }
        }

        if (disableDataSub2) {
            mPreferredSubLists = new PreferredSubscriptionListPreference[
                                         KEY_PREFERRED_SUBSCRIPTION_LIST_NO_DATA.length];

            for (int i = 0; i < KEY_PREFERRED_SUBSCRIPTION_LIST_NO_DATA.length; i++) {
                mPreferredSubLists[i] = (PreferredSubscriptionListPreference) findPreference(
                        KEY_PREFERRED_SUBSCRIPTION_LIST_NO_DATA[i]);
            }
            //voice type
            mPreferredSubLists[0]
                    .setType(MultiSimSettingsConstants.PREFERRED_SUBSCRIPTION_LISTS[0],mHandler);
            //sms type
            mPreferredSubLists[1]
                    .setType(MultiSimSettingsConstants.PREFERRED_SUBSCRIPTION_LISTS[2],mHandler);
            PreferredSubscriptionListPreference datalist = (PreferredSubscriptionListPreference)
                    findPreference(KEY_PREFERRED_SUBSCRIPTION_LIST__DATA);
            datalist.setValue(PREFERRED_SUB_DATA_LIST_VALUE_SLOT_ONE);
            datalist.setEntries(PREFERRED_SUB_DATA_LIST_ENTRY_SLOT_ONE);
            datalist.setSummary(Settings.System.getString(
                    this.getContentResolver(),
                    Settings.System.MULTI_SIM_NAME[0]));
            datalist.setEnabled(false);
        } else {
            mPreferredSubLists = new PreferredSubscriptionListPreference[
                                         KEY_PREFERRED_SUBSCRIPTION_LIST.length];

            for (int i = 0; i < KEY_PREFERRED_SUBSCRIPTION_LIST.length; i++) {
                 mPreferredSubLists[i] = (PreferredSubscriptionListPreference)
                         findPreference(KEY_PREFERRED_SUBSCRIPTION_LIST[i]);
                 mPreferredSubLists[i].setType(
                         MultiSimSettingsConstants.PREFERRED_SUBSCRIPTION_LISTS[i],mHandler);
            }
        }

        // combine message item, so remove.
        PreferredSubscriptionListPreference smslist = (PreferredSubscriptionListPreference)
                findPreference(KEY_PREFERRED_SUBSCRIPTION_LIST[2]);
        PreferenceCategory preferredSubSettings = (PreferenceCategory)
                findPreference(KEY_PREFERRED_SUBSCRIPTION_SETTINGS);
        preferredSubSettings.removePreference(smslist);

        // if two button style, remove voice preference.
        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            if (SystemProperties.getBoolean("persist.env.sys.btnstyle", true)) {
                PreferredSubscriptionListPreference voicelist = (PreferredSubscriptionListPreference)
                        findPreference(KEY_PREFERRED_SUBSCRIPTION_LIST[0]);
                preferredSubSettings.removePreference(voicelist);
            }
        }

    }

    //when set app for , we need to use config screen , not disable/enable
    //button to to active sub
    private boolean isNullSub() {
        SubscriptionManager subscriptionManager = SubscriptionManager.getInstance();
        boolean getNullSub =
                (MSimTelephonyManager.getDefault().hasIccCard(MSimConstants.SUB1) &&
                null == subscriptionManager.setDefaultApp(MSimConstants.SUB1)) ||
                (MSimTelephonyManager.getDefault().hasIccCard(MSimConstants.SUB2) &&
                null == subscriptionManager.setDefaultApp(MSimConstants.SUB2));
        return getNullSub;
    }

    private void initSIMEnalber(){
        mSimOneEnabler = (MultiSimEnablerPreference) findPreference(KEY_SIM_ONE_EBABLER);
        Intent sub1_intent = mSimOneEnabler.getIntent();
        sub1_intent.putExtra(MSimConstants.SUBSCRIPTION_KEY, MSimConstants.SUB1);

        mSimTwoEnabler = (MultiSimEnablerPreference) findPreference(KEY_SIM_TWO_EBABLER);
        if(SystemProperties.getBoolean("persist.env.dualsim.checkbox",false))
        {
            mSimOneEnabler.setWidgetLayoutResource(R.layout.custom_checkbox_xolo);
            mSimTwoEnabler.setWidgetLayoutResource(R.layout.custom_checkbox_xolo);
        }
        else
        {
            mSimOneEnabler.setWidgetLayoutResource(R.layout.custom_checkbox);
            mSimTwoEnabler.setWidgetLayoutResource(R.layout.custom_checkbox);
        }
        Intent sub2_intent = mSimTwoEnabler.getIntent();
        sub2_intent.putExtra(MSimConstants.SUBSCRIPTION_KEY, MSimConstants.SUB2);
        mSimOneEnabler.setSubscription(MSimConstants.SUB1, mHandler);
        mSimTwoEnabler.setSubscription(MSimConstants.SUB2, mHandler);

        //do not use button to diable/enable if get null Subscription in setDefaultApp() method
        mConfigSub = (PreferenceScreen) findPreference(KEY_CONFIG_SUB);
        if (isNullSub()) {
            // now use this config screen to enable/deactive sim card
            mConfigSub.getIntent().putExtra(CONFIG_SUB, true);
            if (isAirplaneModeOn()) {
                Log.d(TAG, "Airplane mode is ON, grayout the config subscription menu!!!");
                mConfigSub.setEnabled(false);
            }
            mSimOneEnabler.setSwitchVisibility(View.INVISIBLE);
            mSimTwoEnabler.setSwitchVisibility(View.INVISIBLE);
        } else {
            PreferenceScreen basePreferenceScreen = getPreferenceScreen();
            basePreferenceScreen.removePreference(mConfigSub);
            mSimOneEnabler.setSwitchVisibility(View.VISIBLE);
            mSimTwoEnabler.setSwitchVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.multi_sim_preference);
        initPreferences();
        initSIMEnalber();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resume();

        isPause = false;

        //you need reshow dialog which showed before and dismissed onPause.
        if (isShowDialog(SET_DATA_SUB_PROGRESSDIALOG) && mNeedReshowSetDataSubProDia) {
            mSetDataSubProgressDialog.show(getFragmentManager(), "SetDataSubProgressDialog");
            mNeedReshowSetDataSubProDia = false;
        }
        if (isShowDialog(DISABLE_ENABLE_PROGRESSDIALOG) && mNeedReshowDisEnableProDia) {
            mDisableEnableProgressDialog.show(getFragmentManager(), "DisableEnableProgressDialog");
            mNeedReshowDisEnableProDia = false;
        }
        if (isShowDialog(DISABLE_ENABLE_RESULT_ALERTDIALOG) && mNeedReshowAlertDialog) {
            mDisableEnableAlertDialog.show(getFragmentManager(), "DisableEnableAlertDialog");
            mNeedReshowAlertDialog = false;
        }

    }

    private boolean isAirplaneModeOn() {
        return Settings.Global.getInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    private void resume(){
        for (PreferredSubscriptionListPreference subPref : mPreferredSubLists) {
             subPref.resume();
        }

        if (disableDataSub2) {
            PreferredSubscriptionListPreference datalist = (PreferredSubscriptionListPreference)
                    findPreference(KEY_PREFERRED_SUBSCRIPTION_LIST__DATA);
            datalist.setSummary(Settings.System.getString(
                    this.getContentResolver(),
                    Settings.System.MULTI_SIM_NAME[0]));
        }
    }

    private boolean isShowDialog(int dialogId){
        if (dialogId == SET_DATA_SUB_PROGRESSDIALOG){
            return (mSetDataSubProgressDialog != null &&
                    mSetDataSubProgressDialog.getShowsDialog());
        } else if (dialogId == DISABLE_ENABLE_PROGRESSDIALOG){
            return ( mDisableEnableProgressDialog != null &&
                    mDisableEnableProgressDialog.getShowsDialog());
        } else if (dialogId == DISABLE_ENABLE_RESULT_ALERTDIALOG){
            return ( mDisableEnableAlertDialog != null &&
                    mDisableEnableAlertDialog.getShowsDialog());
        }
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (PreferredSubscriptionListPreference subPref : mPreferredSubLists) {
             subPref.pause();
        }
        isPause = true;

    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //dismiss  dialog for Exception'Can not perform this action after onSaveInstanceState'
        dismissProgressDialog(SET_DATA_SUB_PROGRESSDIALOG);
        dismissProgressDialog(DISABLE_ENABLE_PROGRESSDIALOG);
        dissmissAlertDialog(DISABLE_ENABLE_RESULT_ALERTDIALOG);

        //this must after dismiss dialogs.
        super.onSaveInstanceState(outState);
    }

    // When user click the home icon we need finish current activity.
    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    private void showDialog(int dialogId, String dialog_msg, int subId) {
        Log.d(TAG,"showDialog dialogId = "+dialogId+",isPause = "+isPause);
        if(dialogId ==SET_DATA_SUB_ALERTDIALOG){
            mSetDataSubAlertDialog = MultiSimDialog.newInstance(dialogId, dialog_msg);
            mSetDataSubAlertDialog.show(getFragmentManager(), "SetDataSubAlertDialog");
        }else if(dialogId == DISABLE_ENABLE_RESULT_ALERTDIALOG){
            mDisableEnableAlertDialog= MultiSimDialog.newInstance(dialogId, dialog_msg);
            mDisableEnableAlertDialog.mSubId = subId;
            if(!isPause){
                mDisableEnableAlertDialog.show(getFragmentManager(), "DisableEnableAlertDialog");
            }
            mNeedReshowAlertDialog = true;
        }
    }

    private void showProgressDialog(int dialogId, Message msg) {
        if (dialogId == SET_DATA_SUB_PROGRESSDIALOG) {
            mSetDataSubProgressDialog = MultiSimDialog.newInstance(SET_DATA_SUB_PROGRESSDIALOG, "");
            mSetDataSubProgressDialog.show(getFragmentManager(), "SetDataSubProgressDialog");
            mNeedReshowSetDataSubProDia= true;
        } else if (dialogId == DISABLE_ENABLE_PROGRESSDIALOG) {
            if( null != msg){
                Bundle b = msg.getData();
                int subId = b.getInt(MSUBID_STR);
                Boolean enabled = b.getBoolean(MENABLED_STR);
                String message = b.getString(MESSAGE_STR);
                mDisableEnableProgressDialog= MultiSimDialog.newInstance(
                        DISABLE_ENABLE_PROGRESSDIALOG, message);
                mDisableEnableProgressDialog.mEnabled = enabled;
                mDisableEnableProgressDialog.mSubId = subId;
                mDisableEnableProgressDialog.show(getFragmentManager(),
                        "DisableEnableProgressDialog");
                mHandler.sendEmptyMessageDelayed(EVENT_PROGRESS_DLG_TIME_OUT,
                        PROGRESS_DLG_TIME_OUT);
                mNeedReshowDisEnableProDia = true;
            }
        }
    }

    private void dismissProgressDialog(int dialogId) {
        if (dialogId == SET_DATA_SUB_PROGRESSDIALOG && mSetDataSubProgressDialog != null) {
            mSetDataSubProgressDialog.dismiss();
        } else if (dialogId == DISABLE_ENABLE_PROGRESSDIALOG &&
                mDisableEnableProgressDialog != null) {
            mDisableEnableProgressDialog.dismiss();
        }
    }

    private void dissmissAlertDialog(int dialogId){
        if (dialogId == DISABLE_ENABLE_RESULT_ALERTDIALOG ) {
            MultiSimDialog dialog = (MultiSimDialog)(getFragmentManager()
                    .findFragmentByTag("DisableEnableAlertDialog"));
            if (dialog != null) {
                dialog.dismiss();
                mNeedReshowAlertDialog = true;
            }
        }
    }

    public static class MultiSimDialog extends DialogFragment {
        public final static String TAG = "MultiSimDialog";

        static int mSubId = -1;
        static boolean mEnabled;

        // Argument bundle keys
        private static final String BUNDLE_KEY_DIALOG_ID = "MultiSimDialog.id";
        private static final String BUNDLE_KEY_DIALOG_MSG = "MultiSimDialog.msg";
        private static final int SLOT1 = 0;
        private static final int SLOT2 = 1;
        private DialogInterface.OnClickListener mDialogClickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                resumeEnabler();
            }
        };

        /**
                * Create the dialog with parameters
                */
        public static MultiSimDialog newInstance(int id, String message) {
            MultiSimDialog frag = new MultiSimDialog();
            Bundle bundle = new Bundle();
            bundle.putInt(BUNDLE_KEY_DIALOG_ID, id);
            bundle.putString(BUNDLE_KEY_DIALOG_MSG, message);
            frag.setArguments(bundle);
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Context context = getActivity();
            int dialog_id = getArguments().getInt(BUNDLE_KEY_DIALOG_ID);
            String dialog_msg = getArguments().getString(BUNDLE_KEY_DIALOG_MSG);
            switch(dialog_id) {
                case SET_DATA_SUB_PROGRESSDIALOG:
                    this.setCancelable(false);
                    ProgressDialog progressDialog = new ProgressDialog(getActivity());
                    progressDialog.setMessage(getString(R.string.set_data_subscription_progress));
                    progressDialog.setIndeterminate(true);
                    return progressDialog;
                case DISABLE_ENABLE_PROGRESSDIALOG:
                    String title = Settings.System.getString(getActivity().getContentResolver(),
                            Settings.System.MULTI_SIM_NAME[mSubId]);
                    String msg = getString(mEnabled?R.string.sim_enabler_enabling
                            :R.string.sim_enabler_disabling);
                    ProgressDialog progressDiallog = new ProgressDialog(context);
                    progressDiallog.setIndeterminate(true);
                    progressDiallog.setTitle(title);
                    progressDiallog.setMessage(msg);
                    progressDiallog.setCancelable(false);
                    progressDiallog.setCanceledOnTouchOutside(false);
                    return progressDiallog;
                case SET_DATA_SUB_ALERTDIALOG:
                    return new AlertDialog.Builder(getActivity()).setMessage(dialog_msg)
                        .setTitle(android.R.string.dialog_alert_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, null)
                        .create();
                case DISABLE_ENABLE_RESULT_ALERTDIALOG:
                    AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                            .setTitle(android.R.string.dialog_alert_title)
                            .setMessage(dialog_msg)
                            .setCancelable(false)
                            .setNeutralButton(R.string.close_dialog, mDialogClickListener)
                            .create();
                    alertDialog.setCanceledOnTouchOutside(false);
                    return alertDialog;
                default:
                    return null;
            }
        }


        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            Context context = getActivity();
            int dialog_id = getArguments().getInt(BUNDLE_KEY_DIALOG_ID);
            switch(dialog_id) {
                case DISABLE_ENABLE_RESULT_ALERTDIALOG:
                    MultiSimEnablerPreference.mIsShowDialog = false;
                    mNeedReshowAlertDialog = false;
                    resumeEnabler();
                    break;
                case DISABLE_ENABLE_PROGRESSDIALOG:
                    MultiSimEnablerPreference.mIsShowDialog = false;
                    mNeedReshowDisEnableProDia = false;
                    break;
                case SET_DATA_SUB_PROGRESSDIALOG:
                    mNeedReshowSetDataSubProDia= false;
                    break;
                default:
                    break;
            }
        }

        private void resumeEnabler(){
            if( SLOT1 == mSubId ){
                mSimOneEnabler.resume();
            }else if( SLOT2 == mSubId ){
                mSimTwoEnabler.resume();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if(mHandler.hasMessages(EVENT_PROGRESS_DLG_TIME_OUT)) {
            mHandler.removeMessages(EVENT_PROGRESS_DLG_TIME_OUT);
        }
        mSimOneEnabler.setSubscription(MSimConstants.SUB1,null);
        mSimTwoEnabler.setSubscription(MSimConstants.SUB2,null);
        super.onDestroy();
    }
}

