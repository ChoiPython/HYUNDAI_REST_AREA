package io.shimjari.rest;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.text.LineBreaker;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.Layout;
import android.view.Gravity;
import android.widget.Button;
import android.widget.TextView;

/** Shared neutral surfaces, Asta Sans typography and touch targets for both activities. */
final class UiStyle {
    final Context context;
    final boolean dark;
    final int background,surface,text,muted,soft,border,accent;
    private final Typeface regular,medium;

    UiStyle(Context context) {
        this.context=context;
        dark=(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        background=dark?0xFF1C1D1F:0xFFF6F7F9;
        surface=dark?0xFF303236:0xFFFFFFFF;
        text=dark?0xFFF6F7F9:0xFF111315;
        muted=dark?0xFFBCC0C8:0xFF62666F;
        soft=dark?0xFF393C42:0xFFECEEF2;
        border=dark?0xFF4B4F57:0xFFE1E4E9;
        accent=dark?0xFFE1E4EB:0xFF43464D;
        Typeface font=context.getResources().getFont(R.font.asta_sans);
        regular=Typeface.create(font,400,false);medium=Typeface.create(font,600,false);
    }
    int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    TextView text(String value,float size,int color,boolean bold){
        TextView view=new TextView(context);
        view.setText(value);view.setTextSize(size);view.setTextColor(color);
        view.setTypeface(bold?medium:regular);view.setIncludeFontPadding(false);
        view.setFontFeatureSettings("tnum");view.setGravity(Gravity.START);
        view.setTextAlignment(android.view.View.TEXT_ALIGNMENT_GRAVITY);
        balancedLines(view);
        view.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        view.setLineSpacing(dp(2),1.12f);
        return view;
    }
    GradientDrawable shape(int color,int radius){
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;
    }
    Button button(String label,boolean primary,boolean onDark,Runnable click){
        Button view=new Button(context);view.setText(label);view.setAllCaps(false);view.setTextSize(15);
        view.setTypeface(medium);view.setIncludeFontPadding(false);view.setGravity(Gravity.CENTER);
        balancedLines(view);view.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        view.setMinWidth(0);view.setMinimumWidth(0);view.setMinHeight(dp(52));view.setMinimumHeight(dp(52));
        view.setPadding(dp(14),dp(12),dp(14),dp(12));view.setStateListAnimator(null);
        boolean night=dark||onDark;
        int fill=primary?(night?0xFFF6F7F9:0xFF43464D):(night?0xFF393C42:0xFFECEEF2);
        int ink=primary?(night?0xFF111315:0xFFFFFFFF):(night?0xFFF6F7F9:0xFF303236);
        view.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{night?0xFF9A9FA9:0xFF717680,ink}));
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},shape(night?0xFF303236:0xFFE8EBF0,12));
        states.addState(new int[]{},shape(fill,12));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(night?0x33FFFFFF:0x1F000000),states,shape(0xFFFFFFFF,12)));
        view.setSelected(primary);view.setOnClickListener(v->click.run());
        return view;
    }
    // LineBreaker constants inline to the same integer values accepted by TextView since API 23.
    @SuppressLint("InlinedApi")
    private static void balancedLines(TextView view){view.setBreakStrategy(LineBreaker.BREAK_STRATEGY_BALANCED);}
    void systemBars(boolean darkScreen){
        android.view.Window window=((android.app.Activity)context).getWindow();boolean night=dark||darkScreen;
        int color=night?0xFF1C1D1F:background;window.setStatusBarColor(color);window.setNavigationBarColor(color);
        if(android.os.Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
        android.view.View decor=window.getDecorView();int legacyMask=android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        decor.setSystemUiVisibility(night?decor.getSystemUiVisibility()&~legacyMask:decor.getSystemUiVisibility()|legacyMask);
        if(android.os.Build.VERSION.SDK_INT>=30&&window.getInsetsController()!=null){
            int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            window.getInsetsController().setSystemBarsAppearance(night?0:mask,mask);
        }
    }
    void restoreScroll(android.widget.ScrollView scroll,int y){
        scroll.setFocusableInTouchMode(true);
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){
            @Override public void onGlobalLayout(){
                scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                scroll.post(()->scroll.scrollTo(0,y));
            }
        });
    }
    void compact(Button view,int size){
        view.setTextSize(size);view.setSingleLine(false);view.setMaxLines(2);view.setPadding(dp(6),dp(10),dp(6),dp(10));
        view.setMinHeight(dp(48));view.setMinimumHeight(dp(48));
    }
}
