package com.donaldlad.vpn;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int PICK_CONFIG = 101;
    private static final int VPN_PERMISSION = 102;

    private GoBackend backend;
    private Config config;
    private final AppTunnel tunnel = new AppTunnel();
    private TextView status;
    private TextView configLabel;
    private Button connectButton;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(this);
        prefs = getSharedPreferences("donald_vpn", MODE_PRIVATE);
        buildScreen();
        String saved = prefs.getString("wg_config", null);
        if (saved != null) loadConfig(saved, false);
    }

    private void buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(28, 48, 28, 28);
        page.setBackgroundColor(0xFF101827);

        TextView brand = new TextView(this);
        brand.setText("DONALD LA D");
        brand.setTextColor(0xFFFFFFFF);
        brand.setTextSize(30);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(null, 1);
        page.addView(brand, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("VPN  •  CONNEXION SÉCURISÉE");
        subtitle.setTextColor(0xFF32D6A0);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 8, 0, 36);
        page.addView(subtitle, matchWrap());

        status = new TextView(this);
        status.setText("DÉCONNECTÉ");
        status.setTextColor(0xFFFFC857);
        status.setTextSize(20);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 20, 0, 24);
        page.addView(status, matchWrap());

        configLabel = new TextView(this);
        configLabel.setText("Aucune configuration WireGuard importée");
        configLabel.setTextColor(0xFFD5DCE8);
        configLabel.setGravity(Gravity.CENTER);
        configLabel.setPadding(0, 0, 0, 22);
        page.addView(configLabel, matchWrap());

        Button importButton = new Button(this);
        importButton.setText("IMPORTER UN FICHIER .CONF");
        importButton.setOnClickListener(v -> chooseConfig());
        page.addView(importButton, matchWidth());

        connectButton = new Button(this);
        connectButton.setText("SE CONNECTER");
        connectButton.setOnClickListener(v -> connectOrDisconnect());
        LinearLayout.LayoutParams cp = matchWidth();
        cp.topMargin = 14;
        page.addView(connectButton, cp);

        TextView note = new TextView(this);
        note.setText("Importe le fichier WireGuard .conf fourni par ton serveur. Sans serveur actif et configuration valide, la connexion ne peut pas fonctionner.");
        note.setTextColor(0xFF9BA8BA);
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, 34, 0, 0);
        page.addView(note, matchWrap());

        setContentView(page);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }
    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void chooseConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_CONFIG);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONFIG && resultCode == RESULT_OK && data != null) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                if (in == null) throw new Exception("Fichier inaccessible");
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int count;
                while ((count = in.read(chunk)) != -1) buffer.write(chunk, 0, count);
                String text = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                loadConfig(text, true);
            } catch (Exception e) {
                showError("Lecture impossible : " + e.getMessage());
            }
        } else if (requestCode == VPN_PERMISSION && resultCode == RESULT_OK) {
            startTunnel();
        }
    }

    private void loadConfig(String raw, boolean save) {
        try {
            config = Config.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            if (save) prefs.edit().putString("wg_config", raw).apply();
            configLabel.setText("Configuration WireGuard prête");
            Toast.makeText(this, "Configuration importée", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            config = null;
            showError("Fichier .conf invalide : " + e.getMessage());
        }
    }

    private void connectOrDisconnect() {
        if (config == null) {
            showError("Importe d'abord un fichier .conf WireGuard.");
            return;
        }
        if ("UP".equals(tunnel.state.name())) {
            new Thread(() -> {
                try { backend.setState(tunnel, Tunnel.State.DOWN, null); }
                catch (Exception e) { runOnUiThread(() -> showError(e.getMessage())); }
            }).start();
            return;
        }
        Intent permission = VpnService.prepare(this);
        if (permission != null) startActivityForResult(permission, VPN_PERMISSION);
        else startTunnel();
    }

    private void startTunnel() {
        status.setText("CONNEXION EN COURS…");
        new Thread(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.UP, config);
            } catch (Exception e) {
                runOnUiThread(() -> showError("Connexion impossible : " + e.getMessage()));
            }
        }).start();
    }

    private void showError(String message) {
        Toast.makeText(this, message == null ? "Erreur inconnue" : message, Toast.LENGTH_LONG).show();
        if (status != null) status.setText("DÉCONNECTÉ");
    }

    private class AppTunnel implements Tunnel {
        volatile Tunnel.State state = Tunnel.State.DOWN;
        @Override public String getName() { return "donaldlad"; }
        @Override public void onStateChange(Tunnel.State newState) {
            state = newState;
            runOnUiThread(() -> {
                boolean up = newState == Tunnel.State.UP;
                status.setText(up ? "CONNECTÉ" : "DÉCONNECTÉ");
                status.setTextColor(up ? 0xFF32D6A0 : 0xFFFFC857);
                connectButton.setText(up ? "SE DÉCONNECTER" : "SE CONNECTER");
            });
        }
    }
}
