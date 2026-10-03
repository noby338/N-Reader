package com.noby.nreader;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.noby.nreader.net.QrCodeUtil;

public class DonationActivity extends Activity {
    private static final String PAYPAL_URL = "https://www.paypal.com/ncp/payment/AKBZ3BM5368R8?item_name=Support%20N-Reader%20TV%20App&custom=n-reader";
    private static final String KOFI_URL = "https://ko-fi.com/nobytan?ref=n-reader";

    private ImageView qrImageView;
    private TextView tipTextView;
    private TextView urlTextView;

    private Button btnWeChat;
    private Button btnAlipay;
    private Button btnPaypal;
    private Button btnKofi;

    private Bitmap paypalQrBitmap;
    private Bitmap kofiQrBitmap;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AppText.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        selectChannel(AppText.isChinese() ? 0 : 2); // Default WeChat for Chinese, PayPal for English
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(32), dp(24), dp(32), dp(24));
        root.setBackgroundColor(Color.rgb(15, 23, 42)); // Deep Slate

        TextView title = new TextView(this);
        title.setText(AppText.get(R.string.donate_title));
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(AppText.get(R.string.donate_subtitle));
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(148, 163, 184));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(dp(40), dp(6), dp(40), dp(16));
        root.addView(subtitle);

        // Channels Selector Row
        LinearLayout channelsRow = new LinearLayout(this);
        channelsRow.setOrientation(LinearLayout.HORIZONTAL);
        channelsRow.setGravity(Gravity.CENTER);

        btnWeChat = createChannelButton(AppText.get(R.string.donate_wechat), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectChannel(0);
            }
        });
        channelsRow.addView(btnWeChat);

        btnAlipay = createChannelButton(AppText.get(R.string.donate_alipay), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectChannel(1);
            }
        });
        channelsRow.addView(btnAlipay);

        btnPaypal = createChannelButton(AppText.get(R.string.donate_paypal), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectChannel(2);
            }
        });
        channelsRow.addView(btnPaypal);

        btnKofi = createChannelButton(AppText.get(R.string.donate_kofi), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectChannel(3);
            }
        });
        channelsRow.addView(btnKofi);

        root.addView(channelsRow);

        // QR Code Card Container
        LinearLayout qrCard = new LinearLayout(this);
        qrCard.setOrientation(LinearLayout.VERTICAL);
        qrCard.setGravity(Gravity.CENTER);
        qrCard.setPadding(dp(20), dp(18), dp(20), dp(18));
        qrCard.setBackground(ThemeHelper.createCardDrawable(Color.rgb(30, 41, 59), 16, this));

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                dp(340), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(18), 0, 0);
        qrCard.setLayoutParams(cardParams);

        qrImageView = new ImageView(this);
        qrImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(dp(220), dp(220));
        qrCard.addView(qrImageView, imgParams);

        tipTextView = new TextView(this);
        tipTextView.setTextSize(13);
        tipTextView.setTextColor(Color.WHITE);
        tipTextView.setGravity(Gravity.CENTER);
        tipTextView.setPadding(0, dp(12), 0, dp(4));
        qrCard.addView(tipTextView);

        urlTextView = new TextView(this);
        urlTextView.setTextSize(11);
        urlTextView.setTextColor(ThemeHelper.ACCENT_CYAN);
        urlTextView.setGravity(Gravity.CENTER);
        qrCard.addView(urlTextView);

        root.addView(qrCard);

        setContentView(root);
        btnWeChat.requestFocus();
    }

    private Button createChannelButton(String label, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setTextColor(Color.WHITE);
        btn.setBackground(ThemeHelper.createButtonDrawable(Color.rgb(30, 41, 59), 8, this));
        btn.setPadding(dp(16), dp(8), dp(16), dp(8));
        btn.setFocusable(true);
        btn.setOnClickListener(listener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        params.setMargins(dp(6), 0, dp(6), 0);
        btn.setLayoutParams(params);
        return btn;
    }

    private void selectChannel(int index) {
        resetButtonStyles();
        switch (index) {
            case 0: // WeChat Pay
                btnWeChat.setSelected(true);
                qrImageView.setImageResource(R.drawable.qr_wechat);
                tipTextView.setText(AppText.get(R.string.donate_wechat_tip));
                urlTextView.setText("WeChat Pay");
                break;

            case 1: // Alipay
                btnAlipay.setSelected(true);
                qrImageView.setImageResource(R.drawable.qr_alipay);
                tipTextView.setText(AppText.get(R.string.donate_alipay_tip));
                urlTextView.setText("Alipay");
                break;

            case 2: // PayPal
                btnPaypal.setSelected(true);
                if (paypalQrBitmap == null) {
                    try {
                        paypalQrBitmap = QrCodeUtil.create(PAYPAL_URL, dp(220));
                    } catch (Exception ignored) {
                    }
                }
                if (paypalQrBitmap != null) {
                    qrImageView.setImageBitmap(paypalQrBitmap);
                }
                tipTextView.setText(AppText.get(R.string.donate_paypal_tip));
                urlTextView.setText("paypal.com (custom=n-reader)");
                break;

            case 3: // Ko-fi
                btnKofi.setSelected(true);
                if (kofiQrBitmap == null) {
                    try {
                        kofiQrBitmap = QrCodeUtil.create(KOFI_URL, dp(220));
                    } catch (Exception ignored) {
                    }
                }
                if (kofiQrBitmap != null) {
                    qrImageView.setImageBitmap(kofiQrBitmap);
                }
                tipTextView.setText(AppText.get(R.string.donate_kofi_tip));
                urlTextView.setText("ko-fi.com/nobytan?ref=n-reader");
                break;
        }
    }

    private void resetButtonStyles() {
        btnWeChat.setSelected(false);
        btnAlipay.setSelected(false);
        btnPaypal.setSelected(false);
        btnKofi.setSelected(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (paypalQrBitmap != null && !paypalQrBitmap.isRecycled()) {
            paypalQrBitmap.recycle();
            paypalQrBitmap = null;
        }
        if (kofiQrBitmap != null && !kofiQrBitmap.isRecycled()) {
            kofiQrBitmap.recycle();
            kofiQrBitmap = null;
        }
    }

    private int dp(float dp) {
        return ThemeHelper.dpToPx(dp, this);
    }
}
