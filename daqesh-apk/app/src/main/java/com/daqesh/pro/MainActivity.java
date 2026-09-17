package com.daqesh.pro;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MediaPlayer player;
    private SharedPreferences voicePrefs;

    private static final String PREFS = "eleven_voice";
    private static final String P_KEY = "api_key";
    private static final String P_VOICE_ID = "voice_id";
    private static final String P_VOICE_NAME = "voice_name";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        voicePrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
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
                injectElevenLabsPatch();
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

    private void injectElevenLabsPatch() {
        String js = "(()=>{try{" +
                "const b=document.getElementById('voiceSettingsBtn');" +
                "if(b&&!b.dataset.eleven){const n=b.cloneNode(true);n.dataset.eleven='1';" +
                "let vn='';try{vn=AndroidBridge.getVoiceName()||''}catch(e){};" +
                "n.textContent='🎙️ '+(vn||'ElevenLabs');b.replaceWith(n);" +
                "n.onclick=()=>{try{AndroidBridge.openVoiceSettings()}catch(e){}}}" +
                "if(!window.__elevenFetchPatched){const oldFetch=window.fetch.bind(window);" +
                "const silent=new Uint8Array([82,73,70,70,36,0,0,0,87,65,86,69,102,109,116,32,16,0,0,0,1,0,1,0,68,172,0,0,136,88,1,0,2,0,16,0,100,97,116,97,0,0,0,0]);" +
                "window.fetch=async(u,o={})=>{const x=String(u||'');if(x.includes('.tts.speech.microsoft.com')){" +
                "let t='';try{t=new DOMParser().parseFromString(String(o.body||''),'application/xml').documentElement.textContent||''}catch(e){t=String(o.body||'').replace(/<[^>]+>/g,' ')};" +
                "try{AndroidBridge.speakEleven(t)}catch(e){};return new Response(silent,{status:200,headers:{'Content-Type':'audio/wav'}})}return oldFetch(u,o)};window.__elevenFetchPatched=true}" +
                "window.onElevenConfigured=()=>{localStorage.setItem('dq_voice',JSON.stringify({engine:'hamed',region:'eleven',key:'eleven'}));location.reload()};" +
                "window.onElevenDisabled=()=>{localStorage.setItem('dq_voice',JSON.stringify({engine:'device',region:'',key:''}));location.reload()};" +
                "window.onElevenSpeechError=(m)=>{const e=document.getElementById('toast');if(e){e.textContent='تعذر ElevenLabs: '+(m||'خطأ');e.classList.remove('hidden');setTimeout(()=>e.classList.add('hidden'),2200)}};" +
                "let ok=false;try{ok=AndroidBridge.isElevenConfigured()}catch(e){};" +
                "let q={};try{q=JSON.parse(localStorage.getItem('dq_voice')||'{}')}catch(e){};" +
                "if(ok&&q.engine!=='hamed'){localStorage.setItem('dq_voice',JSON.stringify({engine:'hamed',region:'eleven',key:'eleven'}));location.reload()}" +
                "}catch(e){console.log(e)}})();";
        webView.evaluateJavascript(js, null);
    }

    public class VoiceBridge {
        @JavascriptInterface
        public boolean isElevenConfigured() {
            return !voicePrefs.getString(P_KEY, "").isEmpty() && !voicePrefs.getString(P_VOICE_ID, "").isEmpty();
        }

        @JavascriptInterface
        public String getVoiceName() {
            return voicePrefs.getString(P_VOICE_NAME, "");
        }

        @JavascriptInterface
        public void openVoiceSettings() {
            runOnUiThread(MainActivity.this::showVoiceSettingsDialog);
        }

        @JavascriptInterface
        public void speakEleven(String text) {
            if (text == null || text.trim().isEmpty()) return;
            String key = voicePrefs.getString(P_KEY, "");
            String voiceId = voicePrefs.getString(P_VOICE_ID, "");
            if (key.isEmpty() || voiceId.isEmpty()) {
                jsError("الإعداد غير مكتمل");
                return;
            }
            executor.execute(() -> requestSpeech(text, key, voiceId));
        }

        @JavascriptInterface
        public void stopAudio() {
            runOnUiThread(MainActivity.this::stopPlayer);
        }
    }

    private void showVoiceSettingsDialog() {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView hint = new TextView(this);
        hint.setText("ElevenLabs: أدخل مفتاح API ثم حمّل الأصوات واختر الصوت الذي يعجبك. ما تحتاج Region.");
        hint.setTextSize(15);
        box.addView(hint);

        EditText keyInput = new EditText(this);
        keyInput.setHint("ElevenLabs API Key");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText(voicePrefs.getString(P_KEY, ""));
        box.addView(keyInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Spinner spinner = new Spinner(this);
        List<String> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        String savedName = voicePrefs.getString(P_VOICE_NAME, "");
        String savedId = voicePrefs.getString(P_VOICE_ID, "");
        if (!savedId.isEmpty()) {
            labels.add(savedName.isEmpty() ? "الصوت المحفوظ" : savedName);
            ids.add(savedId);
        } else {
            labels.add("اضغط تحميل الأصوات");
            ids.add("");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
        box.addView(spinner);

        Button load = new Button(this);
        load.setText("تحميل الأصوات المتاحة");
        box.addView(load);

        Button device = new Button(this);
        device.setText("الرجوع لصوت الجهاز");
        box.addView(device);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إعداد صوت ElevenLabs")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ واختبار", null)
                .create();

        load.setOnClickListener(v -> {
            String key = keyInput.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "أدخل API Key أولًا", Toast.LENGTH_SHORT).show();
                return;
            }
            load.setEnabled(false);
            load.setText("جاري التحميل...");
            executor.execute(() -> loadVoices(key, labels, ids, adapter, spinner, load));
        });

        device.setOnClickListener(v -> {
            voicePrefs.edit().clear().apply();
            dialog.dismiss();
            webView.evaluateJavascript("window.onElevenDisabled&&window.onElevenDisabled()", null);
        });

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String key = keyInput.getText().toString().trim();
            int pos = spinner.getSelectedItemPosition();
            String id = pos >= 0 && pos < ids.size() ? ids.get(pos) : "";
            String name = pos >= 0 && pos < labels.size() ? labels.get(pos) : "";
            if (key.isEmpty() || id.isEmpty()) {
                Toast.makeText(this, "أدخل المفتاح ثم حمّل واختر صوتًا", Toast.LENGTH_SHORT).show();
                return;
            }
            voicePrefs.edit().putString(P_KEY, key).putString(P_VOICE_ID, id).putString(P_VOICE_NAME, name).apply();
            dialog.dismiss();
            webView.evaluateJavascript("window.onElevenConfigured&&window.onElevenConfigured()", null);
            executor.execute(() -> requestSpeech("هلا والله، صوت داقش جاهز. خلنا نبدأ الجولة.", key, id));
        }));

        dialog.show();
    }

    private void loadVoices(String key, List<String> labels, List<String> ids, ArrayAdapter<String> adapter, Spinner spinner, Button load) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://api.elevenlabs.io/v2/voices?page_size=30").openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("xi-api-key", key);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            String json = readString(c.getInputStream());
            JSONArray arr = new JSONObject(json).optJSONArray("voices");
            List<String> newLabels = new ArrayList<>();
            List<String> newIds = new ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String id = o.optString("voice_id", "");
                    String name = o.optString("name", "Voice");
                    JSONObject labs = o.optJSONObject("labels");
                    String gender = labs == null ? "" : labs.optString("gender", "");
                    String accent = labs == null ? "" : labs.optString("accent", "");
                    if (!id.isEmpty()) {
                        newIds.add(id);
                        newLabels.add(name + (gender.isEmpty() ? "" : " · " + gender) + (accent.isEmpty() ? "" : " · " + accent));
                    }
                }
            }
            runOnUiThread(() -> {
                labels.clear(); ids.clear();
                labels.addAll(newLabels); ids.addAll(newIds);
                if (labels.isEmpty()) { labels.add("لم يتم العثور على أصوات"); ids.add(""); }
                adapter.notifyDataSetChanged();
                spinner.setSelection(0);
                load.setEnabled(true);
                load.setText("إعادة تحميل الأصوات");
                Toast.makeText(this, "تم تحميل " + newIds.size() + " صوت", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                load.setEnabled(true);
                load.setText("تحميل الأصوات المتاحة");
                Toast.makeText(this, "تعذر تحميل الأصوات: تأكد من المفتاح", Toast.LENGTH_LONG).show();
            });
        } finally { if (c != null) c.disconnect(); }
    }

    private void requestSpeech(String text, String key, String voiceId) {
        HttpURLConnection c = null;
        File temp = null;
        try {
            String endpoint = "https://api.elevenlabs.io/v1/text-to-speech/" + voiceId + "?output_format=mp3_44100_128";
            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("xi-api-key", key);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "audio/mpeg");
            JSONObject body = new JSONObject();
            body.put("text", text);
            body.put("model_id", "eleven_multilingual_v2");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            c.getOutputStream().write(payload);
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
            byte[] audio = readBytes(c.getInputStream());
            temp = File.createTempFile("daqesh_voice_", ".mp3", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(audio); }
            File finalTemp = temp;
            runOnUiThread(() -> playFile(finalTemp));
        } catch (Exception e) {
            jsError(e.getMessage());
            if (temp != null) temp.delete();
        } finally { if (c != null) c.disconnect(); }
    }

    private void playFile(File file) {
        try {
            stopPlayer();
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> { stopPlayer(); file.delete(); });
            player.setOnErrorListener((mp, what, extra) -> { stopPlayer(); file.delete(); return true; });
            player.prepare();
            player.start();
        } catch (Exception e) {
            file.delete();
            jsError("تشغيل الصوت");
        }
    }

    private void stopPlayer() {
        try { if (player != null) { if (player.isPlaying()) player.stop(); player.release(); } } catch (Exception ignored) {}
        player = null;
    }

    private void jsError(String msg) {
        String safe = JSONObject.quote(msg == null ? "خطأ" : msg);
        runOnUiThread(() -> webView.evaluateJavascript("window.onElevenSpeechError&&window.onElevenSpeechError(" + safe + ")", null));
    }

    private static String readString(InputStream in) throws Exception {
        return new String(readBytes(in), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(InputStream in) throws Exception {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = input.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    @Override
    protected void onDestroy() {
        stopPlayer();
        executor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}