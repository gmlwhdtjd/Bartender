package com.teambartender3.filters;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.teambartender3.filters.PreferenceSetting.PrefManager;

import io.fabric.sdk.android.Fabric;
import java.io.File;
import java.util.ArrayList;

import es.dmoral.toasty.Toasty;

public class GuideActivity extends AppCompatActivity {
    private ViewPager viewPager;
    private MyViewPagerAdapter myViewPagerAdapter;
    private LinearLayout dotsLayout;
    private TextView[] dots;
    private int[] layouts;
    private Button btnSkip, btnNext;
    private PrefManager prefManager;


    private PermissionListener permissionlistener = new PermissionListener() {
        @Override
        public void onPermissionGranted() {
            Toasty.success(GuideActivity.this, "Permission Granted", Toast.LENGTH_SHORT).show();
            prefManager.setFirstTimeLaunch(false);
            launchHomeScreen();
        }

        @Override
        public void onPermissionDenied(ArrayList<String> deniedPermissions) {
            Toasty.warning(GuideActivity.this, "Permission Denied\n" + deniedPermissions.toString(), Toast.LENGTH_SHORT).show();
            prefManager.setFirstTimeLaunch(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());

        // Checking for first time launch - before calling setContentView()
        prefManager = new PrefManager(this);
        if (!prefManager.isFirstTimeLaunch()) {
            checkPermission();
        }else{
            setDefaultSetting(this);
        }

        // Making notification bar transparent
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }

        setContentView(R.layout.activity_guide);

        viewPager = (ViewPager) findViewById(R.id.view_pager);
        dotsLayout = (LinearLayout) findViewById(R.id.layoutDots);
        btnSkip = (Button) findViewById(R.id.btn_skip);
        btnNext = (Button) findViewById(R.id.btn_next);

        // layouts of all welcome sliders
        // add few more layouts if you want
        layouts = new int[]{
                R.layout.guide_slide1,
                R.layout.guide_slide2,
                R.layout.guide_slide3,
                R.layout.guide_slide4};

        // adding bottom dots
        addBottomDots(0);

        // making notification bar transparent
        changeStatusBarColor();

        myViewPagerAdapter = new MyViewPagerAdapter();
        viewPager.setAdapter(myViewPagerAdapter);
        viewPager.addOnPageChangeListener(viewPagerPageChangeListener);

        if (!prefManager.isFirstTimeLaunch()) {
            viewPager.setCurrentItem(layouts.length);
        }
        btnSkip.setOnClickListener(
                new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewPager.setCurrentItem(layouts.length);
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // checking for last page
                // if last page home screen will be launched
                int current = getItem(+1);
                if (current < layouts.length) {
                    // move to next screen
                    viewPager.setCurrentItem(current);
                } else {
                    checkPermission();
                }
            }
        });
//
//        //기본 세팅이 안되어 있다면
//        if(!prefManager.isDefaultFilterSet()){
//            setDefaultSetting(this);
//            prefManager.setDefaultFilterSet(true);
//        }

    }

    private void addBottomDots(int currentPage) {
        dots = new TextView[layouts.length];

        int[] colorsActive = getResources().getIntArray(R.array.array_dot_active);
        int[] colorsInactive = getResources().getIntArray(R.array.array_dot_inactive);

        dotsLayout.removeAllViews();
        for (int i = 0; i < dots.length; i++) {
            dots[i] = new TextView(this);
            dots[i].setText(Html.fromHtml("&#8226;"));
            dots[i].setTextSize(35);
            dots[i].setTextColor(colorsInactive[currentPage]);
            dotsLayout.addView(dots[i]);
        }

        if (dots.length > 0)
            dots[currentPage].setTextColor(colorsActive[currentPage]);
    }

    private int getItem(int i) {
        return viewPager.getCurrentItem() + i;
    }

    private void launchHomeScreen() {
        startActivity(new Intent(GuideActivity.this, MainActivity.class));
        finish();
    }

    //  viewpager change listener
    ViewPager.OnPageChangeListener viewPagerPageChangeListener = new ViewPager.OnPageChangeListener() {

        @Override
        public void onPageSelected(int position) {
            addBottomDots(position);
            startAnimation(position);

            // changing the next button text 'NEXT' / 'GOT IT'
            if (position == layouts.length - 1) {
                // last page. make button text to GOT IT
                btnNext.setText("START");
                btnSkip.setVisibility(View.GONE);
            } else {
                // still pages are left
                btnNext.setText("NEXT");
                btnSkip.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void onPageScrolled(int arg0, float arg1, int arg2) {

        }

        @Override
        public void onPageScrollStateChanged(int arg0) {

        }
    };

    private void startAnimation(int position){
        View view = viewPager.findViewWithTag(position);
        switch (position) {
            case 0:
                ConstraintLayout guide1 = view.findViewById(R.id.guide1);
                Animation guide1_anim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.guide1_anim);
                guide1.startAnimation(guide1_anim);
                break;
            case 1:
                ImageView guide2_0 = view.findViewById(R.id.guide2_0);
                ImageView guide2_1 = view.findViewById(R.id.guide2_1);
                ImageView guide2_2 = view.findViewById(R.id.guide2_2);

                Animation guide2_0_anim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.guide2_0_anim);
                Animation guide2_1_anim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.guide2_1_anim);
                Animation guide2_2_anim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.guide2_2_anim);

                guide2_0.startAnimation(guide2_0_anim);
                guide2_1.startAnimation(guide2_1_anim);
                guide2_2.startAnimation(guide2_2_anim);
                break;
            case 2:
                ImageView guide3_0 = view.findViewById(R.id.guide3_0);
                ImageView guide3_1 = view.findViewById(R.id.guide3_1);

                Animation guide3_0_anim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.guide3_0_anim);
                Animation guide3_1_anim = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.guide3_1_anim);

                guide3_0.startAnimation(guide3_0_anim);
                guide3_1.startAnimation(guide3_1_anim);
                break;
            case 3:
                break;
            default:
        }
    }


    /**
     * Making notification bar transparent
     */
    private void changeStatusBarColor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    /**
     * View pager adapter
     */
    public class MyViewPagerAdapter extends PagerAdapter {
        private LayoutInflater layoutInflater;

        public MyViewPagerAdapter() {
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            layoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View view = layoutInflater.inflate(layouts[position], container, false);
            container.addView(view);
            view.setTag(position);
            return view;
        }

        @Override
        public int getCount() {
            return layouts.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object obj) {
            return view == obj;
        }


        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            View view = (View) object;
            container.removeView(view);
        }
    }

    private void setDefaultSetting(Context context){
        File file = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        + File.separator + getString(R.string.app_name));
        if(!file.exists()){
            file.mkdirs();
        }
        String mSaveDirectory = file.getAbsolutePath();
        SharedPreferences pref = this.getSharedPreferences(this.getString(R.string.gallery_pref),0);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(this.getString(R.string.key_gallery_name),mSaveDirectory);
        editor.commit();
    }

    private void checkPermission(){
        //Permission Check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            TedPermission.with(this)
                    .setPermissionListener(permissionlistener)
                    .setDeniedMessage(getString(R.string.permission_denied))
                    .setPermissions(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .check();
            return;
        }
        else {
            prefManager.setFirstTimeLaunch(false);
            launchHomeScreen();
        }
    }
}
