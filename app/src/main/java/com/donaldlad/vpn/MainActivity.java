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
                        traffic.setText("Reçues : " + formatBytes(received)
                                + "     Envoyées : " + formatBytes(sent));
                        if (lastHandshake == 0) {
                            handshake.setText("Proton : aucune négociation confirmée pour le moment");
                        } else {
                            long seconds = Math.max(0, (System.currentTimeMillis() - lastHandshake) / 1000);
                            handshake.setText("Proton : échange confirmé (dernière négociation il y a "
                                    + seconds + " s)");
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
            runOnUiThread(() -> { if (status != null) status.setText(
                    state == State.UP ? "État : tunnel démarré" : "État : déconnecté"
            ); });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(this);
        appFrame = new FrameLayout(this);
        appFrame.setBackgroundColor(Color.rgb(5, 12, 27));
        ImageView watermark = new ImageView(this);
        watermark.setImageResource(R.drawable.phoenix_logo6);
        watermark.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        watermark.setAlpha(0.10f);
        appFrame.addView(watermark, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(20), dp(24), dp(28));
        scroll.addView(page);
        appFrame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.phoenix_logo6);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(138), dp(138));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        logoParams.bottomMargin = dp(8);
        page.addView(logo, logoParams);

        TextView title = pageText("DONALD LA D VPN", 24, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        page.addView(title);
        TextView subtitle = pageText(pageName.toUpperCase(Locale.ROOT), 12, 0xFF69CFFF);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(20));
        page.addView(subtitle);

        if ("Menu".equals(pageName)) {
            TextView welcome = pageText("Bienvenue. Choisis une rubrique :", 16, Color.WHITE);
            welcome.setGravity(Gravity.CENTER);
            page.addView(welcome);
            addPageButton(page, "Connexion", "Connexion");
            addPageButton(page, "Déconnexion", "Déconnexion");
            addPageButton(page, "Importer une configuration", "Configuration");
        } else if ("Configuration".equals(pageName)) {
            page.addView(pageText("IMPORTER UN PROFIL WIREGUARD", 16, Color.WHITE));
            page.addView(pageText("Sélectionne le fichier .conf fourni par ton service VPN.", 14, 0xFFD6E7F5));
            Button importButton = menuButton("Choisir un fichier .conf");
            importButton.setOnClickListener(v -> chooseConfig());
            page.addView(importButton);
            status = pageText(config == null ? "Aucune configuration importée." : "Configuration importée.", 14, Color.WHITE);
            page.addView(status);
        } else if ("Connexion".equals(pageName)) {
            page.addView(pageText("ÉTAT DU VPN", 16, Color.WHITE));
            status = pageText(config == null ? "Aucune configuration importée" : "Configuration prête", 15, Color.WHITE);
            page.addView(status);
            Button connect = menuButton("Se connecter");
            connect.setOnClickListener(v -> connectVpn());
            page.addView(connect);
            Button disconnect = menuButton("Se déconnecter");
            disconnect.setOnClickListener(v -> disconnectVpn());
            page.addView(disconnect);
            Button importButton = menuButton("Importer une configuration");
            importButton.setOnClickListener(v -> showPage("Configuration"));
            page.addView(importButton);
        } else if ("Log".equals(pageName)) {
            page.addView(pageText("DIAGNOSTIC DE CONNEXION", 16, 0xFF69CFFF));
            traffic = pageText("Reçues : —     Envoyées : —", 15, Color.WHITE);
            page.addView(traffic);
            handshake = pageText("Proton : en attente de connexion", 14, 0xFFD6E7F5);
            page.addView(handshake);
            page.addView(pageText("Un tunnel démarré ne confirme pas à lui seul que le serveur répond. Vérifie la négociation et le trafic.", 12, 0xFFB7C9D9));
        } else if ("More".equals(pageName)) {
            page.addView(pageText("PARAMÈTRES DE L’APPLICATION", 16, Color.WHITE));
            page.addView(pageText("Le profil serveur et les clés sont définis dans le fichier WireGuard .conf importé. N’envoie jamais ta clé privée à quelqu’un.", 14, 0xFFD6E7F5));
            page.addView(pageText("Version 1.2 • DONALD LA D VPN", 12, 0xFF69CFFF));
        }

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(0xFF0D2038);
        String[][] tabs = {{"MENU","Menu"},{"CONFIGURATION","Configuration"},{"LOG","Log"},{"MORE","More"}};
        for (String[] tab : tabs) {
            Button b = menuButton(tab[0]);
            b.setTextSize(9);
            b.setPadding(dp(2), dp(10), dp(2), dp(10));
            b.setOnClickListener(v -> showPage(tab[1]));
            nav.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        appFrame.addView(nav, navParams);
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
        button.setTextSize(15);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(0xCC10243D);
        shape.setCornerRadius(18);
        shape.setStroke(2, 0xFF168DCE);
        button.setBackground(shape);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 5, 0, 5);
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
            status.setText("État : configuration importée, prête à être utilisée");
            handshake.setText("Proton : en attente de connexion");
            traffic.setText("Reçues : —     Envoyées : —");
            Toast.makeText(this, "Configuration importée", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            config = null;
            status.setText("État : fichier de configuration invalide");
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
        status.setText("État : démarrage du VPN…");
        vpnExecutor.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.UP, config);
                monitoring = true;
                runOnUiThread(() -> {
                    status.setText("État : tunnel démarré, vérification du serveur…");
                    handshake.setText("Proton : vérification de la négociation…");
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
                    status.setText("Échec VPN : " + error);
                    handshake.setText("Proton : connexion non établie");
                    Toast.makeText(this, "Échec VPN : " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void disconnectVpn() {
        monitoring = false;
        mainHandler.removeCallbacks(statsPoll);
        status.setText("État : déconnexion…");
        vpnExecutor.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
                runOnUiThread(() -> {
                    status.setText("État : déconnecté");
                    traffic.setText("Reçues : —     Envoyées : —");
                    handshake.setText("Proton : déconnecté");
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
