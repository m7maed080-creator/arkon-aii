package com.daqesh.pro;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
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

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private MediaPlayer player;
    private SharedPreferences voicePrefs;
    private TextToSpeech fallbackTts;
    private volatile boolean ttsReady = false;

    private static final String PREFS = "aws_polly_voice";
    private static final String P_ACCESS = "access_key";
    private static final String P_SECRET = "secret_key";
    private static final String P_REGION = "region";

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        voicePrefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        initFallbackTts();

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

    private void initFallbackTts() {
        fallbackTts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = fallbackTts.setLanguage(new Locale("ar", "SA"));
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                fallbackTts.setSpeechRate(0.90f);
                fallbackTts.setPitch(0.90f);
            }
        });
    }

    private void injectVoicePatch() {
        String js = "(()=>{try{" +
                "const b=document.getElementById('voiceSettingsBtn');" +
                "if(b&&!b.dataset.polly){const n=b.cloneNode(true);n.dataset.polly='1';n.textContent='🎙️ Amazon Polly · Zayd';b.replaceWith(n);n.onclick=()=>{try{AndroidBridge.openVoiceSettings()}catch(e){}}}" +
                "const rankWords=['الأول','الثاني','الثالث','الرابع','الخامس','السادس','السابع','الثامن','التاسع','العاشر'];" +
                "function balancesText(){const cards=[...document.querySelectorAll('#cards .card')];if(!cards.length)return'';let t='أرصدة اللاعبين الحالية. ';cards.forEach((c,i)=>{const name=(c.querySelector('.pname')?.textContent||'').trim();const bal=(c.querySelector('.balance')?.textContent||'').trim();if(name)t+=(rankWords[i]?'المركز '+rankWords[i]+'، ':'')+name+'، معك '+bal+'. ';});const w=document.getElementById('winner');if(w&&!w.classList.contains('hidden'))t+=' '+(w.textContent||'');return t}" +
                "const tb=document.querySelector('.toolbar');if(tb&&!document.getElementById('announceNowBtn')){const a=document.createElement('button');a.id='announceNowBtn';a.type='button';a.textContent='🔊 إعلان الأرصدة';a.className='primary';a.addEventListener('click',()=>{try{const t=balancesText();if(t)AndroidBridge.speakSmart(t)}catch(e){}});tb.appendChild(a)}" +
                "if(!window.__daqeshRoundDirect){document.addEventListener('click',e=>{const btn=e.target&&e.target.closest?e.target.closest('#saveRound'):null;if(!btn)return;setTimeout(()=>{try{const st=document.getElementById('soundToggle');if(st&&st.textContent.includes('متوقف'))return;const t=balancesText();if(t)AndroidBridge.speakSmart('خلصنا الجولة. '+t)}catch(err){}},800)},true);window.__daqeshRoundDirect=true}" +
                "window.onCloudVoiceError=(m)=>{const e=document.getElementById('toast');if(e){e.textContent=m||'تعذر الصوت السحابي';e.classList.remove('hidden');setTimeout(()=>e.classList.add('hidden'),2600)}};" +
                "}catch(e){console.log(e)}})();";
        webView.evaluateJavascript(js, null);
    }

    public class VoiceBridge {
        @JavascriptInterface
        public boolean isCloudConfigured() {
            return !voicePrefs.getString(P_ACCESS, "").isEmpty() && !voicePrefs.getString(P_SECRET, "").isEmpty();
        }

        @JavascriptInterface
        public void openVoiceSettings() {
            runOnUiThread(MainActivity.this::showVoiceSettingsDialog);
        }

        @JavascriptInterface
        public void speakSmart(String text) {
            if (text == null || text.trim().isEmpty()) return;
            String access = voicePrefs.getString(P_ACCESS, "");
            String secret = voicePrefs.getString(P_SECRET, "");
            String region = voicePrefs.getString(P_REGION, "eu-central-1");
            if (access.isEmpty() || secret.isEmpty()) {
                fallbackSpeak(text, false);
                return;
            }
            executor.execute(() -> requestPolly(text, access, secret, region));
        }

        @JavascriptInterface
        public void stopAudio() {
            runOnUiThread(() -> {
                stopPlayer();
                if (fallbackTts != null) fallbackTts.stop();
            });
        }
    }

    private void showVoiceSettingsDialog() {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView hint = new TextView(this);
        hint.setText("الصوت: Amazon Polly Zayd — رجل خليجي Neural. إذا تعذر Polly يستخدم صوت الجهاز تلقائيًا.");
        hint.setTextSize(15);
        box.addView(hint);

        EditText accessInput = new EditText(this);
        accessInput.setHint("AWS Access Key ID");
        accessInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        accessInput.setText(voicePrefs.getString(P_ACCESS, ""));
        box.addView(accessInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText secretInput = new EditText(this);
        secretInput.setHint("AWS Secret Access Key");
        secretInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        secretInput.setText(voicePrefs.getString(P_SECRET, ""));
        box.addView(secretInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Spinner regionSpinner = new Spinner(this);
        String[] regions = {"eu-central-1", "us-east-1", "eu-west-2", "us-west-2"};
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, regions);
        regionSpinner.setAdapter(regionAdapter);
        String savedRegion = voicePrefs.getString(P_REGION, "eu-central-1");
        for (int i = 0; i < regions.length; i++) if (regions[i].equals(savedRegion)) regionSpinner.setSelection(i);
        box.addView(regionSpinner);

        Button device = new Button(this);
        device.setText("استخدام صوت الجهاز فقط");
        box.addView(device);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("إعداد Amazon Polly")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ واختبار", null)
                .create();

        device.setOnClickListener(v -> {
            voicePrefs.edit().clear().apply();
            dialog.dismiss();
            fallbackSpeak("أبشر، صوت الجهاز شغال الحين.", false);
        });

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String access = accessInput.getText().toString().trim();
            String secret = secretInput.getText().toString().trim();
            String region = String.valueOf(regionSpinner.getSelectedItem());
            if (access.isEmpty() || secret.isEmpty()) {
                Toast.makeText(this, "أدخل Access Key و Secret Key", Toast.LENGTH_SHORT).show();
                return;
            }
            voicePrefs.edit().putString(P_ACCESS, access).putString(P_SECRET, secret).putString(P_REGION, region).apply();
            dialog.dismiss();
            executor.execute(() -> requestPolly("هلا والله، صوت داقش جاهز. بعد كل جولة بعطيك أرصدة اللاعبين.", access, secret, region));
        }));

        dialog.show();
    }

    private void requestPolly(String text, String accessKey, String secretKey, String region) {
        HttpURLConnection c = null;
        File temp = null;
        try {
            String service = "polly";
            String host = "polly." + region + ".amazonaws.com";
            String endpoint = "https://" + host + "/v1/speech";

            JSONObject bodyObj = new JSONObject();
            bodyObj.put("Engine", "neural");
            bodyObj.put("LanguageCode", "ar-AE");
            bodyObj.put("OutputFormat", "mp3");
            bodyObj.put("SampleRate", "24000");
            bodyObj.put("Text", text);
            bodyObj.put("TextType", "text");
            bodyObj.put("VoiceId", "Zayd");
            String body = bodyObj.toString();
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);

            Date now = new Date();
            SimpleDateFormat amzFmt = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US);
            SimpleDateFormat dateFmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
            TimeZone utc = TimeZone.getTimeZone("UTC");
            amzFmt.setTimeZone(utc);
            dateFmt.setTimeZone(utc);
            String amzDate = amzFmt.format(now);
            String dateStamp = dateFmt.format(now);

            String payloadHash = sha256Hex(payload);
            String canonicalHeaders = "content-type:application/json\n" + "host:" + host + "\n" + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "content-type;host;x-amz-date";
            String canonicalRequest = "POST\n/v1/speech\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
            String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

            byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
            byte[] kRegion = hmac(kDate, region);
            byte[] kService = hmac(kRegion, service);
            byte[] kSigning = hmac(kService, "aws4_request");
            String signature = toHex(hmac(kSigning, stringToSign));
            String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "audio/mpeg");
            c.setRequestProperty("Host", host);
            c.setRequestProperty("X-Amz-Date", amzDate);
            c.setRequestProperty("Authorization", authorization);
            try (java.io.OutputStream os = c.getOutputStream()) { os.write(payload); }

            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                String err = "HTTP " + code;
                try {
                    InputStream eis = c.getErrorStream();
                    if (eis != null) {
                        String detail = new String(readBytes(eis), StandardCharsets.UTF_8);
                        if (!detail.isEmpty()) err += " " + detail.substring(0, Math.min(detail.length(), 220));
                    }
                } catch (Exception ignored) {}
                throw new Exception(err);
            }

            byte[] audio = readBytes(c.getInputStream());
            if (audio.length < 100) throw new Exception("empty-audio");
            temp = File.createTempFile("daqesh_polly_", ".mp3", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(temp)) { out.write(audio); }
            File finalTemp = temp;
            runOnUiThread(() -> playFile(finalTemp, text));
        } catch (Exception e) {
            if (temp != null) temp.delete();
            final String msg = e.getMessage() == null ? "تعذر Amazon Polly" : e.getMessage();
            runOnUiThread(() -> {
                Toast.makeText(this, "Polly تعذر؛ تم استخدام صوت الجهاز", Toast.LENGTH_SHORT).show();
                webView.evaluateJavascript("window.onCloudVoiceError&&window.onCloudVoiceError(" + JSONObject.quote(msg) + ")", null);
            });
            fallbackSpeak(text, false);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void playFile(File file, String fallbackText) {
        try {
            stopPlayer();
            if (fallbackTts != null) fallbackTts.stop();
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> { stopPlayer(); file.delete(); });
            player.setOnErrorListener((mp, what, extra) -> {
                stopPlayer();
                file.delete();
                fallbackSpeak(fallbackText, false);
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            file.delete();
            fallbackSpeak(fallbackText, false);
        }
    }

    private void fallbackSpeak(String text, boolean notify) {
        runOnUiThread(() -> {
            if (notify) Toast.makeText(this, "استخدمت صوت الجهاز", Toast.LENGTH_SHORT).show();
            if (fallbackTts != null && ttsReady) {
                fallbackTts.stop();
                fallbackTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "daqesh_" + System.currentTimeMillis());
            } else {
                Toast.makeText(this, "فعّل محرك تحويل النص إلى كلام في إعدادات الجوال", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void stopPlayer() {
        try {
            if (player != null) {
                if (player.isPlaying()) player.stop();
                player.release();
            }
        } catch (Exception ignored) {}
        player = null;
    }

    private static byte[] hmac(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return toHex(md.digest(data));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
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
        if (fallbackTts != null) {
            fallbackTts.stop();
            fallbackTts.shutdown();
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}