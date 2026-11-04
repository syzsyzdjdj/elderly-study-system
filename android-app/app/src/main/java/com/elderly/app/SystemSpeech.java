package com.elderly.app;

import android.app.Activity;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.WebView;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.util.Collections;
import java.util.Locale;

/** Device TTS only: no model, credentials, microphone or network permission added. */
final class SystemSpeech {
    private final Activity activity;
    private final TextToSpeech engine;
    private boolean ready;
    private String activeId;
    private JavaScriptReplyProxy activeReply;

    SystemSpeech(Activity activity, WebView webView, String baseUrl) {
        this.activity = activity;
        engine = new TextToSpeech(activity, status -> ready = status == TextToSpeech.SUCCESS);
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { finish(id, "done"); }
            @Override public void onError(String id) { finish(id, "error"); }
        });
        Uri base = Uri.parse(baseUrl);
        String origin = base.getScheme() + "://" + base.getEncodedAuthority();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "ElderlySpeech", Collections.singleton(origin),
                (view, message, source, mainFrame, reply) -> {
                    if (!mainFrame) return;
                    try {
                        JSONObject data = new JSONObject(message.getData());
                        if ("stop".equals(data.optString("action"))) { stop(); return; }
                        if (!"speak".equals(data.optString("action"))) return;
                        String id = data.optString("id");
                        String text = data.optString("text");
                        stop();
                        activeId = id; activeReply = reply;
                        if (!ready || text.isEmpty() || text.length() > 20000) { finish(id, "error"); return; }
                        int language = engine.setLanguage(Locale.SIMPLIFIED_CHINESE);
                        if (language < TextToSpeech.LANG_AVAILABLE) { finish(id, "error"); return; }
                        engine.setSpeechRate((float)Math.max(0.5, Math.min(2, data.optDouble("rate", 1))));
                        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.ERROR)
                            finish(id, "error");
                    } catch (Exception ignored) { }
                });
        }
    }

    private void finish(String id, String status) {
        activity.runOnUiThread(() -> {
            if (activeReply == null || !id.equals(activeId)) return;
            JavaScriptReplyProxy reply = activeReply;
            activeId = null; activeReply = null;
            try { reply.postMessage(new JSONObject().put("id", id).put("status", status).toString()); }
            catch (Exception ignored) { }
        });
    }

    void stop() { activeId = null; activeReply = null; engine.stop(); }
    void shutdown() { stop(); engine.shutdown(); }
}
