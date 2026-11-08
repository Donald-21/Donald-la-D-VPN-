package com.donaldlad.vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.crypto.Key;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_CONFIG = 1001;
    private static final int VPN_PERMISSION = 1002;

    private GoBackend backend;
    private Config config;
    private final AppTunnel tunnel = new AppTunnel();
    private TextView status;
    private TextView traffic;
    private TextView handshake;
    private boolean pendingConnect;
    private volatile boolean monitoring;
    private FrameLayout appFrame;
    private Button toggleButton;
    private volatile boolean tunnelActive;
    private final ExecutorService vpnExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable statsPoll = new Runnable() {
        @Override
        public void run() {
            if (!monitoring) return;
            vpnExecutor.execute(() -> {
                try {
                    Statistics stats = backend.getStatistics(tunnel);
                    long rx = 0;
                    long tx = 0;
                    long latestHandshake = 0;
                    for (Key peer : stats.peers()) {
                        Statistics.PeerStats peerStats = stats.peer(peer);
                        if (peerStats == null) continue;
                        rx += peerStats.rxBytes();
                        tx += peerStats.txBytes();
                        latestHandshake = Math.max(latestHandshake, peerStats.latestHandshakeEpochMillis());
                    }
                    final long received = rx;
                    final long sent = tx;
                    final long lastHandshake = latestHandshake;
                    runOnUiThread(() -> {
                        if (traffic != null) traffic.setText("Reçues : " + formatBytes(received)
                                + "     Envoyées : " + formatBytes(sent));
                        if (handshake != null) {
                            if (lastHandshake == 0) handshake.setText("WireGuard : aucune négociation confirmée");
                            else {
                                long seconds = Math.max(0, (System.currentTimeMillis() - lastHandshake) / 1000);
                                handshake.setText("WireGuard : échange confirmé (il y a " + seconds + " s)");
                            }
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.e("DONALD_LA_VPN", "Lecture des statistiques impossible", e);
                }
            });
            mainHandler.postDelayed(this, 3000);
        }
    };

    private final class AppTunnel implements Tunnel {
        @Override
        public String getName() {
            return "donaldladvpn";
        }

        @Override
        public void onStateChange(State state) {
            tunnelActive = state == State.UP;
            runOnUiThread(() -> {
                if (status != null) status.setText(tunnelActive ? "État : tunnel démarré" : "État : déconnecté");
                if (toggleButton != null) {
                    toggleButton.setText(tunnelActive ? "DÉCONNECTER" : "CONNECTER");
                    GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                            tunnelActive ? new int[]{0xFF0A9B87, 0xFF075B70} : new int[]{0xFF168DFF, 0xFF064A9C});
                    circle.setShape(GradientDrawable.OVAL);
                    circle.setStroke(dp(3), 0xFF79D8FF);
                    toggleButton.setBackground(circle);
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(this);
        appFrame = new FrameLayout(this);
        GradientDrawable backdrop = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF06152B, 0xFF0A3154, 0xFF030914});
        appFrame.setBackground(backdrop);
        ImageView watermark = new ImageView(this);
        watermark.setImageResource(R.drawable.phoenix_logo6);
        watermark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        watermark.setAlpha(0.24f);
        watermark.setAdjustViewBounds(true);
        FrameLayout.LayoutParams watermarkParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER);
        watermarkParams.setMargins(dp(18), dp(72), dp(18), dp(86));
        appFrame.addView(watermark, watermarkParams);
        setContentView(appFrame);
        showPage("Menu");
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView pageText(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(0, dp(8), 0, dp(8));
        return t;
    }

    private void showPage(String pageName) {
        appFrame.removeViews(1, Math.max(0, appFrame.getChildCount() - 1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(110));
        scroll.addView(page);
        appFrame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.phoenix_logo6);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable logoFrame = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1E9FFF, 0xFF071D39});
        logoFrame.setCornerRadius(dp(20));
        logoFrame.setStroke(dp(1), 0xFF62CFFF);
        logo.setBackground(logoFrame);
        logo.setClipToOutline(true);
        brand.addView(logo, new LinearLayout.LayoutParams(dp(62), dp(62)));
        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        brandText.setPadding(dp(14), 0, 0, 0);
        TextView title = pageText("DONALD LA D", 20, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(2));
        brandText.addView(title);
        TextView sub = pageText("VPN  •  PRIVATE CONNECTION", 10, 0xFF70CFFF);
        sub.setPadding(0, 0, 0, 0);
        sub.setLetterSpacing(0.08f);
        brandText.addView(sub);
        brand.addView(brandText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        page.addView(brand);

        TextView section = pageText(pageName.toUpperCase(Locale.ROOT), 11, 0xFF8EDBFF);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setLetterSpacing(0.16f);
        section.setPadding(0, dp(28), 0, dp(12));
        page.addView(section);

        if ("Menu".equals(pageName)) {
            LinearLayout hero = glassPanel();
            hero.setGravity(Gravity.CENTER);
            TextView eyebrow = pageText("PROTECTION DE VOTRE CONNEXION", 10, 0xFF8DDCFF);
            eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            eyebrow.setGravity(Gravity.CENTER);
            eyebrow.setLetterSpacing(0.10f);
            hero.addView(eyebrow);
            TextView headline = pageText("Votre univers.\nVotre connexion.", 25, Color.WHITE);
            headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            headline.setGravity(Gravity.CENTER);
            headline.setPadding(0, dp(8), 0, dp(5));
            hero.addView(headline);
            status = pageText(tunnelActive ? "●  TUNNEL ACTIF" : "●  PRÊT À SE CONNECTER", 12,
                    tunnelActive ? 0xFF5FFFD0 : 0xFF9DC9E8);
            status.setGravity(Gravity.CENTER);
            hero.addView(status);

            toggleButton = new Button(this);
            toggleButton.setText(tunnelActive ? "DÉCONNECTER" : "CONNECTER");
            toggleButton.setTextColor(Color.WHITE);
            toggleButton.setTextSize(16);
            toggleButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            toggleButton.setAllCaps(false);
            GradientDrawable circle = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    tunnelActive ? new int[]{0xFF00B894, 0xFF075B70} : new int[]{0xFF20B7FF, 0xFF0752B8});
            circle.setShape(GradientDrawable.OVAL);
            circle.setStroke(dp(2), 0xFF9BE7FF);
            toggleButton.setBackground(circle);
            toggleButton.setElevation(dp(16));
            LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(dp(164), dp(164));
            toggleParams.gravity = Gravity.CENTER_HORIZONTAL;
            toggleParams.topMargin = dp(20);
            toggleParams.bottomMargin = dp(16);
            hero.addView(toggleButton, toggleParams);
            toggleButton.setOnClickListener(v -> {
                if (tunnelActive) disconnectVpn(); else connectVpn();
            });
            TextView note = pageText(config == null ? "Importe ton profil WireGuard pour commencer." : "Profil WireGuard prêt à être utilisé.", 12, 0xFFB9D5EA);
            note.setGravity(Gravity.CENTER);
            hero.addView(note);
            page.addView(hero);

            TextView quickTitle = pageText("ACCÈS RAPIDE", 11, 0xFF8EDBFF);
            quickTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            quickTitle.setLetterSpacing(0.14f);
            quickTitle.setPadding(0, dp(22), 0, dp(8));
            page.addView(quickTitle);
            addPageButton(page, "＋   Importer une configuration", "Configuration");
            addPageButton(page, "◉   État de la connexion", "Connexion");
            addPageButton(page, "⌁   Diagnostic réseau", "Log");
            addPageButton(page, "⚙   Paramètres", "More");
        } else if ("Configuration".equals(pageName)) {
            LinearLayout card = glassPanel();
            card.addView(pageText("PROFIL WIREGUARD", 16, Color.WHITE));
            card.addView(pageText("Sélectionne le fichier .conf fourni par ton service VPN. Tes clés restent dans ton appareil.", 13, 0xFFBFD5E8));
            Button importButton = menuButton("＋   Choisir un fichier .conf");
            importButton.setOnClickListener(v -> chooseConfig());
            card.addView(importButton);
            status = pageText(config == null ? "○  Aucune configuration importée" : "✓  Configuration importée", 13, config == null ? 0xFFFFC978 : 0xFF64E6C1);
            card.addView(status);
            page.addView(card);
        } else if ("Connexion".equals(pageName)) {
            LinearLayout card = glassPanel();
            card.addView(pageText("ÉTAT DU TUNNEL", 16, Color.WHITE));
            status = pageText(tunnelActive ? "●  Tunnel démarré" : (config == null ? "○  Aucun profil chargé" : "○  Profil prêt"), 14, 0xFFBFD5E8);
            card.addView(status);
            Button connect = menuButton("CONNECTER");
            connect.setOnClickListener(v -> connectVpn());
            card.addView(connect);
            Button disconnect = menuButton("DÉCONNECTER");
            disconnect.setOnClickListener(v -> disconnectVpn());
            card.addView(disconnect);
            Button importButton = menuButton("Importer une configuration");
            importButton.setOnClickListener(v -> showPage("Configuration"));
            card.addView(importButton);
            page.addView(card);
        } else if ("Log".equals(pageName)) {
            LinearLayout card = glassPanel();
            card.addView(pageText("DIAGNOSTIC EN DIRECT", 16, Color.WHITE));
            traffic = pageText("Reçues : —     Envoyées : —", 14, 0xFFD8EAF8);
            card.addView(traffic);
            handshake = pageText("WireGuard : en attente de connexion", 13, 0xFF8EDBFF);
            card.addView(handshake);
            card.addView(pageText("Un tunnel démarré ne confirme pas à lui seul que le serveur répond. Vérifie la négociation et le trafic.", 12, 0xFF9FB8CC));
            page.addView(card);
        } else if ("More".equals(pageName)) {
            LinearLayout card = glassPanel();
            card.addView(pageText("PARAMÈTRES & SÉCURITÉ", 16, Color.WHITE));
            card.addView(pageText("Le serveur et les clés sont définis dans le fichier WireGuard .conf importé. Ne partage jamais ta clé privée.", 13, 0xFFBFD5E8));
            card.addView(pageText("Version 1.3  •  DONALD LA D VPN", 11, 0xFF70CFFF));
            page.addView(card);
        }

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable navBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xF21B3856, 0xF208172B});
        navBg.setCornerRadius(dp(24));
        navBg.setStroke(dp(1), 0x6652BFFF);
        nav.setBackground(navBg);
        nav.setPadding(dp(7), dp(6), dp(7), dp(6));
        nav.setElevation(dp(18));
        String[][] tabs = {{"⌂  ACCUEIL","Menu"},{"▣  PROFIL","Configuration"},{"⌁  TEST","Log"},{"⚙  PLUS","More"}};
        for (String[] tab : tabs) {
            Button b = menuButton(tab[0]);
            b.setTextSize(9);
            b.setPadding(dp(1), dp(11), dp(1), dp(11));
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setOnClickListener(v -> showPage(tab[1]));
            nav.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        navParams.setMargins(dp(12), 0, dp(12), dp(12));
        appFrame.addView(nav, navParams);
    }

    private LinearLayout glassPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xD9234260, 0xD90A1A30});
        glass.setCornerRadius(dp(26));
        glass.setStroke(dp(1), 0x664CBFFF);
        panel.setBackground(glass);
        panel.setElevation(dp(8));
        return panel;
    }

    private void addPageButton(LinearLayout page, String label, String destination) {
        Button button = menuButton(label);
        button.setOnClickListener(v -> showPage(destination));
        page.addView(button);
    }

    private Button menuButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xDD193B5B, 0xDD0B1D33});
        shape.setCornerRadius(dp(18));
        shape.setStroke(dp(1), 0xFF278FCC);
        button.setBackground(shape);
        button.setElevation(dp(3));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        button.setLayoutParams(params);
        return button;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " o";
        if (bytes < 1024L * 1024) return String.format(Locale.getDefault(), "%.1f Ko", bytes / 1024.0);
        return String.format(Locale.getDefault(), "%.2f Mo", bytes / (1024.0 * 1024.0));
    }

    private void chooseConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_CONFIG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VPN_PERMISSION) {
            if (resultCode == RESULT_OK && pendingConnect) {
                pendingConnect = false;
                startTunnel();
            } else {
                pendingConnect = false;
                Toast.makeText(this, "Autorisation VPN refusée", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode != PICK_CONFIG || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("Fichier inaccessible");
            config = Config.parse(input);
            if (status != null) status.setText("État : configuration importée, prête à être utilisée");
            if (handshake != null) handshake.setText("WireGuard : en attente de connexion");
            if (traffic != null) traffic.setText("Reçues : —     Envoyées : —");
            Toast.makeText(this, "Configuration importée", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            config = null;
            if (status != null) status.setText("État : fichier de configuration invalide");
            Toast.makeText(this, "Impossible de lire ce fichier WireGuard", Toast.LENGTH_LONG).show();
        }
    }

    private void connectVpn() {
        if (config == null) {
            Toast.makeText(this, "Importe d'abord un fichier .conf", Toast.LENGTH_LONG).show();
            return;
        }

        Intent permissionIntent = GoBackend.VpnService.prepare(this);
        if (permissionIntent != null) {
            pendingConnect = true;
            startActivityForResult(permissionIntent, VPN_PERMISSION);
        } else {
            startTunnel();
        }
    }

    private void startTunnel() {
        if (status != null) status.setText("État : démarrage du VPN…");
        vpnExecutor.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.UP, config);
                monitoring = true;
                runOnUiThread(() -> {
                    if (status != null) status.setText("État : tunnel démarré, vérification du serveur…");
                    if (handshake != null) handshake.setText("WireGuard : vérification de la négociation…");
                    mainHandler.removeCallbacks(statsPoll);
                    mainHandler.post(statsPoll);
                });
            } catch (Exception e) {
                monitoring = false;
                android.util.Log.e("DONALD_LA_VPN", "Échec du démarrage WireGuard", e);
                StringBuilder detail = new StringBuilder(e.getClass().getSimpleName());
                if (e.getMessage() != null && !e.getMessage().trim().isEmpty()) {
                    detail.append(": ").append(e.getMessage());
                }
                Throwable cause = e.getCause();
                int depth = 0;
                while (cause != null && cause != e && depth < 4) {
                    String message = cause.getMessage();
                    detail.append(" → ").append(cause.getClass().getSimpleName());
                    if (message != null && !message.trim().isEmpty()) {
                        detail.append(": ").append(message);
                    }
                    Throwable next = cause.getCause();
                    if (next == cause) break;
                    cause = next;
                    depth++;
                }
                final String error = detail.toString();
                runOnUiThread(() -> {
                    if (status != null) status.setText("Échec VPN : " + error);
                    if (handshake != null) handshake.setText("WireGuard : connexion non établie");
                    Toast.makeText(this, "Échec VPN : " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void disconnectVpn() {
        monitoring = false;
        mainHandler.removeCallbacks(statsPoll);
        if (status != null) status.setText("État : déconnexion…");
        vpnExecutor.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
                runOnUiThread(() -> {
                    if (status != null) status.setText("État : déconnecté");
                    if (traffic != null) traffic.setText("Reçues : —     Envoyées : —");
                    if (handshake != null) handshake.setText("WireGuard : déconnecté");
                });
            } catch (Exception e) {
                android.util.Log.e("DONALD_LA_VPN", "Échec de la déconnexion", e);
                runOnUiThread(() ->
                        Toast.makeText(this, "Déconnexion impossible", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    @Override
    protected void onDestroy() {
        monitoring = false;
        mainHandler.removeCallbacks(statsPoll);
        vpnExecutor.shutdown();
        super.onDestroy();
    }
}
