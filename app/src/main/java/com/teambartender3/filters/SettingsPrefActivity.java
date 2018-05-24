package com.teambartender3.filters;

/**
 * Created by wilybear on 2018-03-23.
 */

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.teambartender3.filters.SettingConponents.AppCompatPreferenceActivity;
import com.teambartender3.filters.SettingConponents.VersionChecker.MarketVersionChecker;

import net.rdrei.android.dirchooser.DirectoryChooserActivity;
import net.rdrei.android.dirchooser.DirectoryChooserConfig;

import cn.pedant.SweetAlert.SweetAlertDialog;

public class SettingsPrefActivity extends AppCompatPreferenceActivity {
    private static final String TAG = SettingsPrefActivity.class.getSimpleName();
    private static String appPackageName;
    private static final int REQUEST_DIRECTORY = 0;
    private static String device_version = "";
    private static final int OPENLICENSE_CODE = 0;
    private static final int TERMS_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.title_activity_setting));
        appPackageName = getApplicationContext().getPackageName();
        // load settings fragment
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MainPreferenceFragment()).commit();
        MobileAds.initialize(this,getString(R.string.admob_id) );
        View view = View.inflate(this, R.layout.layout_ad_preference,null);
        AdView adView  = new AdView(this);
        adView.setAdUnitId(getString(R.string.banner_ad_unit_id));
        adView.setAdSize(com.google.android.gms.ads.AdSize.BANNER);

        AdRequest.Builder adRequestBuilder = new AdRequest.Builder();

        //테스트 용
        //     adRequestBuilder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);

        adView.loadAd(adRequestBuilder.build());

        ((LinearLayout) view).addView(adView);
        setListFooter(view);
    }

    public static class MainPreferenceFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preference_layout_setting);
            Preference galleryPath = (findPreference(getString(R.string.key_gallery_name)));
            Preference openLicensePref = (findPreference(getString(R.string.key_open_license)));
            Preference termsPref = (findPreference(getString(R.string.key_terms)));
            Preference faqPref = (findPreference(getString(R.string.key_faq)));
            Preference versionPref = (findPreference(getString(R.string.key_app_version)));
            Preference adsPref = (findPreference("ads"));

            SharedPreferences sp = getActivity().getSharedPreferences(getString(R.string.gallery_pref), 0);
            String path = sp.getString(getString(R.string.key_gallery_name), "Picture");
            if (path != null) {
                galleryPath.setSummary(path);
            }
            // gallery EditText change listener
            galleryPath.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final Intent chooserIntent = new Intent(
                            getActivity(),
                            DirectoryChooserActivity.class);

                    final DirectoryChooserConfig config = DirectoryChooserConfig.builder()
                            .newDirectoryName("DirChooserSample")
                            .allowReadOnlyDirectory(true)
                            .allowNewDirectoryNameModification(true)
                            .build();

                    chooserIntent.putExtra(
                            DirectoryChooserActivity.EXTRA_CONFIG,
                            config);

                    startActivityForResult(chooserIntent, REQUEST_DIRECTORY);
                    return true;
                }
            });

            try {
                device_version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            versionPref.setSummary(device_version);

            versionPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    String store_version = "1";
                    try {
                        store_version = MarketVersionChecker.getMarketVersion(getActivity().getPackageName());
                    } catch (Exception e) {
                        Log.d("MarketNotExist", e.toString());
                    }
                    if (store_version.compareTo(device_version) > 0) {
                        new SweetAlertDialog(getActivity(), SweetAlertDialog.WARNING_TYPE)
                                .setTitleText(getString(R.string.update_popup_title_new))
                                .setContentText(getString(R.string.update_message))
                                .showCancelButton(true)
                                .setCancelText(getString(R.string.update_popup_cancel))
                                .setConfirmText(getString(R.string.update_popup_confirm))
                                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                                    @Override
                                    public void onClick(SweetAlertDialog sDialog) {
                                        sDialog.dismissWithAnimation();
                                        Intent marketLaunch = new Intent(
                                                Intent.ACTION_VIEW);
                                        marketLaunch.setData(Uri
                                                .parse("https://play.google.com/store/apps/details?id=" + getActivity().getPackageName()));
                                        startActivity(marketLaunch);
                                    }
                                })
                                .show();
                    } else {
                        new SweetAlertDialog(getActivity(), SweetAlertDialog.NORMAL_TYPE)
                                .setTitleText(getString(R.string.update_popup_title))
                                .setConfirmText("Okay")
                                .show();
                    }

                    return true;
                }
            });

            openLicensePref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startDetailActivity(getActivity(), OPENLICENSE_CODE);
                    return true;
                }
            });

            termsPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startDetailActivity(getActivity(), TERMS_CODE);
                    return true;
                }
            });

            faqPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getActivity(), FaqActivity.class);
                    startActivity(intent);
                    return true;
                }
            });

            // feedback preference click listener
            Preference feedbackPref = findPreference(getString(R.string.key_send_feedback));
            feedbackPref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                public boolean onPreferenceClick(Preference preference) {
                    sendFeedback(getActivity());
                    return true;
                }
            });

            //open playstore app
            Preference review = findPreference(getString(R.string.key_review));
            review.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    openPlayStore(getActivity());
                    return true;
                }
            });
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_DIRECTORY) {
                Log.i(TAG, String.format("Return from DirChooser with result %d",
                        resultCode));

                if (resultCode == DirectoryChooserActivity.RESULT_CODE_DIR_SELECTED) {
                    SharedPreferences pref = getActivity().getSharedPreferences(getString(R.string.gallery_pref), 0);
                    SharedPreferences.Editor editor = pref.edit();
                    editor.putString(getString(R.string.key_gallery_name), data
                            .getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR));
                    editor.commit();
                    Preference galleryPath = findPreference(getString(R.string.key_gallery_name));
                    galleryPath
                            .setSummary(data
                                    .getStringExtra(DirectoryChooserActivity.RESULT_SELECTED_DIR));
                } else {

                }
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    private static void startDetailActivity(Context context, int pageCode) {
        Intent intent = new Intent(context, DetailSettingActivity.class);
        intent.putExtra("pageCode", pageCode);
        context.startActivity(intent);
    }

    /**
     * Email client intent to send support mail
     * Appends the necessary device information to email body
     * useful when providing support
     */
    private static void sendFeedback(Context context) {
        String body = null;
        try {
            body = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            body = "\n\n-----------------------------\nPlease don't remove this information\n Device OS: Android \n Device OS version: " +
                    Build.VERSION.RELEASE + "\n App Version: " + body + "\n Device Brand: " + Build.BRAND +
                    "\n Device Model: " + Build.MODEL + "\n Device Manufacturer: " + Build.MANUFACTURER;
        } catch (PackageManager.NameNotFoundException e) {
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        //팀 이메일
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"TeamBartender3@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Query from android app");
        intent.putExtra(Intent.EXTRA_TEXT, body);
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.choose_email_client)));
    }

    private static void openPlayStore(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
        } catch (android.content.ActivityNotFoundException anfe) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
        }
    }

}