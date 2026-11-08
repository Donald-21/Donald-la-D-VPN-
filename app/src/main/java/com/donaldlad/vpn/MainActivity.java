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
            runOnUiThread(() -> status.setText(
                    state == State.UP ? "État : tunnel démarré" : "État : déconnecté"
            ));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(this);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(5, 12, 27));

        ImageView phoenix = new ImageView(this);
        phoenix.setImageResource(com.donaldlad.vpn.R.drawable.phoenix_logo6);
        phoenix.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        phoenix.setAlpha(0.20f);
        frame.addView(phoenix, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 30, 28, 36);
        scroll.addView(root);
        frame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("DONALD LA D VPN");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 8, 0, 4);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("PROTECTION • CONFIDENTIALITÉ • LIBERTÉ");
        subtitle.setTextSize(11);
        subtitle.setTextColor(0xFF69CFFF);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, 22);
        root.addView(subtitle);

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(0, 8, 0, 18);
        root.addView(menu);
        TextView menuTitle = new TextView(this);
        menuTitle.setText("MENU PRINCIPAL");
        menuTitle.setTextColor(0xFF69CFFF);
        menuTitle.setTextSize(12);
        menuTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        menuTitle.setPadding(0, 4, 0, 8);
        menu.addView(menuTitle);

        TextView homeAnchor = new TextView(this);
        homeAnchor.setText("ACCUEIL");
        homeAnchor.setTextColor(Color.WHITE);
        homeAnchor.setTextSize(18);
        homeAnchor.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(homeAnchor);

        status = new TextView(this);
        status.setText("État : aucune configuration importée");
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        status.setPadding(0, 14, 0, 18);
        root.addView(status);

        Button importButton = menuButton("Importer une configuration");
        importButton.setOnClickListener(v -> chooseConfig());
        menu.addView(importButton);

        Button connectButton = menuButton("Connexion");
        connectButton.setOnClickListener(v -> connectVpn());
        menu.addView(connectButton);

        Button disconnectButton = menuButton("Déconnexion");
        disconnectButton.setOnClickListener(v -> disconnectVpn());
        menu.addView(disconnectButton);

        Button diagnosticButton = menuButton("Diagnostic");
        diagnosticButton.setOnClickListener(v -> {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
        menu.addView(diagnosticButton);

        Button settingsButton = menuButton("Paramètres");
        settingsButton.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Paramètres")
                .setMessage("DONALD LA D VPN\n\nImporte un fichier WireGuard .conf fourni par ton service VPN. Les paramètres du serveur restent définis dans ce fichier.")
                .setPositiveButton("Fermer", null).show());
        menu.addView(settingsButton);

        TextView diagnosticTitle = new TextView(this);
        diagnosticTitle.setText("DIAGNOSTIC DE CONNEXION");
        diagnosticTitle.setTextSize(14);
        diagnosticTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        diagnosticTitle.setTextColor(0xFF69CFFF);
        diagnosticTitle.setPadding(0, 22, 0, 12);
        root.addView(diagnosticTitle);

        traffic = new TextView(this);
        traffic.setText("Reçues : —     Envoyées : —");
        traffic.setTextSize(15);
        traffic.setTextColor(Color.WHITE);
        traffic.setPadding(0, 0, 0, 12);
        root.addView(traffic);

        handshake = new TextView(this);
        handshake.setText("Proton : en attente de connexion");
        handshake.setTextSize(14);
        handshake.setTextColor(0xFFD6E7F5);
        root.addView(handshake);

        TextView note = new TextView(this);
        note.setText("« Tunnel démarré » ne signifie pas encore que le serveur répond. Vérifie ici la négociation et le trafic.");
        note.setTextSize(12);
        note.setTextColor(0xFFB7C9D9);
        note.setPadding(0, 22, 0, 0);
        root.addView(note);

        setContentView(frame);
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
