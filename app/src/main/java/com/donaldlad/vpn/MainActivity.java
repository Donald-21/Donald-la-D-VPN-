package com.donaldlad.vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PICK_CONFIG = 1001;
    private GoBackend backend;
    private Config config;
    private final AppTunnel tunnel = new AppTunnel();
    private TextView status;

    private final class AppTunnel implements Tunnel {
        @Override
        public String getName() {
            return "donaldladvpn";
        }

        @Override
        public void onStateChange(State state) {
            runOnUiThread(() -> status.setText(
                state == State.UP ? "État : connecté" : "État : déconnecté"
            ));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 56, 40, 40);
        root.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView title = new TextView(this);
        title.setText("DONALD LA D VPN");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, 28);
        root.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("Importe un fichier de configuration WireGuard (.conf) fourni par ton serveur VPN.");
        explanation.setTextSize(16);
        explanation.setPadding(0, 0, 0, 24);
        root.addView(explanation);

        status = new TextView(this);
        status.setText("État : aucune configuration importée");
        status.setTextSize(16);
        status.setPadding(0, 0, 0, 24);
        root.addView(status);

        Button importButton = new Button(this);
        importButton.setText("Importer une configuration");
        importButton.setOnClickListener(v -> chooseConfig());
        root.addView(importButton);

        Button connectButton = new Button(this);
        connectButton.setText("Se connecter");
        connectButton.setOnClickListener(v -> connectVpn());
        root.addView(connectButton);

        Button disconnectButton = new Button(this);
        disconnectButton.setText("Se déconnecter");
        disconnectButton.setOnClickListener(v -> disconnectVpn());
        root.addView(disconnectButton);

        setContentView(root);
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
        if (requestCode != PICK_CONFIG || resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            config = Config.parse(input);
            status.setText("État : configuration importée, prête à être utilisée");
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
            startActivityForResult(permissionIntent, 1002);
            pendingConnect = true;
            return;
        }
        startTunnel();
    }

    private boolean pendingConnect = false;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data, boolean unused) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startTunnel() {
        try {
            backend.setState(tunnel, Tunnel.State.UP, config);
            status.setText("État : connexion demandée");
        } catch (Exception e) {
            status.setText("État : échec de connexion");
            Toast.makeText(this, "Connexion impossible. Vérifie le serveur et le fichier .conf.", Toast.LENGTH_LONG).show();
        }
    }

    private void disconnectVpn() {
        try {
            backend.setState(tunnel, Tunnel.State.DOWN, null);
            status.setText("État : déconnecté");
        } catch (Exception e) {
            Toast.makeText(this, "Déconnexion impossible", Toast.LENGTH_SHORT).show();
        }
    }
}
