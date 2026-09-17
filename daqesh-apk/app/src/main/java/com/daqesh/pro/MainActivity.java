package com.daqesh.pro;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private float speechRate = 0.86f;
    private float speechPitch = 0.84f;
    private String presetName = "رجولي طبيعي";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initTts();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        webView.addJavascriptInterface(new VoiceBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectVoicePatch();
            }
        });

        webView.loadUrl("https://daqesh-pro-apk-ready.vercel.app");

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack();
                else finish();
            }
        });
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("ar", "SA"));
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                applyBestArabicVoice();
                applyPreset("رجولي طبيعي");
            }
        });
    }

    private void applyBestArabicVoice() {
        if (tts == null || tts.getVoices() == null) return;
        List<Voice> candidates = new ArrayList<>();
        for (Voice v : tts.getVoices()) {
            Locale l = v.getLocale();
            if (l != null && "ar".equalsIgnoreCase(l.getLanguage())) candidates.add(v);
        }
        candidates.sort(Comparator
                .comparing((Voice v) -> !"SA".equalsIgnoreCase(v.getLocale().getCountry()))
                .thenComparing(Voice::isNetworkConnectionRequired)
                .thenComparing((Voice v) -> -v.getQuality()));
        if (!candidates.isEmpty()) {
            try { tts.setVoice(candidates.get(0)); } catch (Exception ignored) {}
        }
    }

    private void applyPreset(String preset) {
        presetName = preset;
        if ("رجولي عميق".equals(preset)) {
            speechRate = 0.82f;
            speechPitch = 0.76f;
        } else if ("واضح وسريع".equals(preset)) {
            speechRate = 0.96f;
            speechPitch = 0.92f;
        } else {
            speechRate = 0.86f;
            speechPitch = 0.84f;
        }
        if (tts != null) {
            tts.setSpeechRate(speechRate);
            tts.setPitch(speechPitch);
        }
    }

    private void injectVoicePatch() {
        String js = "(()=>{try{" +
                "const b=document.getElementById('voiceSettingsBtn');" +
                "if(b&&!b.dataset.devicevoice){const n=b.cloneNode(true);n.dataset.devicevoice='1';n.textContent='🎙️ صوت الجهاز المحسّن';b.replaceWith(n);n.onclick=()=>{try{AndroidBridge.openVoiceSettings()}catch(e){}}}" +
                "const rankWords=['الأول','الثاني','الثالث','الرابع','الخامس','السادس','السابع','الثامن','التاسع','العاشر'];" +
                "function cleanBal(s){return String(s||'').replace(/,/g,'').replace(/٬/g,'').trim()}" +
                "function balancesText(){const cards=[...document.querySelectorAll('#cards .card')];if(!cards.length)return'';let t='ترتيب الجولة. ';cards.forEach((c,i)=>{const name=(c.querySelector('.pname')?.textContent||'').trim();const bal=cleanBal(c.querySelector('.balance')?.textContent||'');if(name)t+=(rankWords[i]?'المركز '+rankWords[i]+'، ':'')+name+'، رصيدك '+bal+'. ';});const w=document.getElementById('winner');if(w&&!w.classList.contains('hidden'))t+=' '+(w.textContent||'');return t}" +
                "const tb=document.querySelector('.toolbar');if(tb&&!document.getElementById('announceNowBtn')){const a=document.createElement('button');a.id='announceNowBtn';a.type='button';a.textContent='🔊 إعلان الأرصدة';a.className='primary';a.addEventListener('click',()=>{try{const t=balancesText();if(t)AndroidBridge.speak(t)}catch(e){}});tb.appendChild(a)}" +
                "if(!window.__daqeshRoundDirect){document.addEventListener('click',e=>{const btn=e.target&&e.target.closest?e.target.closest('#saveRound'):null;if(!btn)return;setTimeout(()=>{try{const st=document.getElementById('soundToggle');if(st&&st.textContent.includes('متوقف'))return;const t=balancesText();if(t)AndroidBridge.speak('خلصنا الجولة. '+t)}catch(err){}},700)},true);window.__daqeshRoundDirect=true}" +
                "if(!window.__deviceFetchPatched){const oldFetch=window.fetch.bind(window);const silent=new Uint8Array([82,73,70,70,36,0,0,0,87,65,86,69,102,109,116,32,16,0,0,0,1,0,1,0,68,172,0,0,136,88,1,0,2,0,16,0,100,97,116,97,0,0,0,0]);window.fetch=async(u,o={})=>{const x=String(u||'');if(x.includes('.tts.speech.microsoft.com')){let t='';try{t=new DOMParser().parseFromString(String(o.body||''),'application/xml').documentElement.textContent||''}catch(e){};if(t&&!t.trim().startsWith('خلصنا الجولة')){try{AndroidBridge.speak(t)}catch(e){}}return new Response(silent,{status:200,headers:{'Content-Type':'audio/wav'}})}return oldFetch(u,o)};window.__deviceFetchPatched=true}" +
                "}catch(e){console.log(e)}})();";
        webView.evaluateJavascript(js, null);
    }

    public class VoiceBridge {
        @JavascriptInterface
        public void speak(String text) {
            speakNow(text);
        }

        @JavascriptInterface
        public void openVoiceSettings() {
            runOnUiThread(MainActivity.this::showVoiceSettingsDialog);
        }

        @JavascriptInterface
        public void stopAudio() {
            runOnUiThread(() -> { if (tts != null) tts.stop(); });
        }
    }

    private void showVoiceSettingsDialog() {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView hint = new TextView(this);
        hint.setText("اختر نبرة الصوت. التطبيق يستخدم أفضل صوت عربي متوفر في جوالك تلقائيًا، ويفضّل ar-SA إذا كان موجودًا.");
        hint.setTextSize(15);
        box.addView(hint);

        Spinner preset = new Spinner(this);
        String[] options = {"رجولي طبيعي", "رجولي عميق", "واضح وسريع"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        preset.setAdapter(adapter);
        for (int i = 0; i < options.length; i++) if (options[i].equals(presetName)) preset.setSelection(i);
        box.addView(preset, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إعداد صوت داقش")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setNeutralButton("تجربة", null)
                .setPositiveButton("حفظ", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String p = String.valueOf(preset.getSelectedItem());
                applyPreset(p);
                speakNow("هلا والله، هذا صوت داقش. محمد رصيدك خمسين ألف، وأنت بالمركز الأول.");
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String p = String.valueOf(preset.getSelectedItem());
                applyPreset(p);
                Toast.makeText(this, "تم حفظ نبرة: " + p, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void speakNow(String text) {
        if (text == null || text.trim().isEmpty()) return;
        runOnUiThread(() -> {
            if (tts == null || !ttsReady) {
                Toast.makeText(this, "فعّل خدمة تحويل النص إلى كلام العربية في إعدادات الجوال", Toast.LENGTH_LONG).show();
                return;
            }
            String normalized = normalizeForArabicTts(text);
            tts.stop();
            tts.setSpeechRate(speechRate);
            tts.setPitch(speechPitch);
            tts.speak(normalized, TextToSpeech.QUEUE_FLUSH, null, "daqesh_" + System.currentTimeMillis());
        });
    }

    private String normalizeForArabicTts(String s) {
        return s
                .replace("SAR", "ريال")
                .replace("ر.س", "ريال")
                .replace("+", " زائد ")
                .replace("-", " ناقص ")
                .replace("،", ", ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
