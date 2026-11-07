# DONALD LA D VPN — projet Android

Ce projet est une première version Android utilisant la bibliothèque officielle WireGuard Tunnel.
Il permet d'importer un fichier de configuration WireGuard `.conf`, puis de demander à Android
l'autorisation VPN et de démarrer/arrêter le tunnel.

## Important
- L'application ne fournit pas elle-même un serveur VPN.
- Il faut un serveur WireGuard actif et un fichier client `.conf` valide fourni par ce serveur.
- N'utilise jamais une configuration trouvée au hasard : elle contient une clé privée.
- Cette version est une APK de test (`debug`), non une version publiée/signée pour Play Store.

## Construire gratuitement avec GitHub Actions depuis Android
1. Crée un dépôt GitHub vide nommé `DONALD-LA-D-VPN`.
2. Téléverse le contenu de ce dossier en conservant les dossiers.
3. Ouvre l'onglet **Actions** du dépôt.
4. Sélectionne **Build DONALD LA D VPN APK**, puis **Run workflow** si le workflow ne démarre pas automatiquement.
5. Quand l'exécution est terminée avec une coche verte, ouvre-la et télécharge l'artefact **DONALD-LA-D-VPN-debug**.
6. Décompresse l'archive téléchargée : `app-debug.apk` est le fichier à installer.

## Technologie
- Android / Java 17
- Android Gradle Plugin 8.7.3
- WireGuard Tunnel 1.0.20260102 (Apache-2.0)
