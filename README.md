# GlassTV — Android TV Launcher

## Télécharger sur la TV

### [⬇ Télécharger directement GlassTV.apk](https://github.com/pacormn/GlassTv/releases/download/v0.3.0/GlassTV-0.3.0-debug.apk)

**APK prêt à installer, sans ZIP à extraire et sans connexion GitHub.**
Depuis le navigateur de la TV ou Downloader, clique sur le lien ci-dessus, puis ouvre **GlassTV-0.3.0-debug.apk** et choisis **Installer**.

[Ouvrir la Release et ses fichiers](https://github.com/pacormn/GlassTv/releases/tag/v0.1.0)

Version de test 0.1.0 pour Android TV 11. Contrairement aux artifacts Actions, ce fichier de Release n'expire pas après 30 jours.

Prototype **original** de launcher TV, inspiré de l'esthétique translucide de tvOS 26, conçu pour Xiaomi TV Box S 2e génération (Android TV 11). Ce projet n'est pas affilié à Apple.

## Version 0.3.0

- Accueil avec favoris seuls ; descendre révèle la grille complète des applications.
- Appui long OK : ajouter ou retirer un favori. Sans favoris enregistrés, cinq applications sont proposées au premier lancement uniquement.
- Les tuiles sélectionnées sont ramenées dans la zone visible, avec une marge pour leur agrandissement.
- Centre de contrôle défilant : navigation isolée du contenu derrière, jusqu'au bouton Fermer.
- Centre de contrôle → Choisir la bannière : sélectionner une image locale avec le sélecteur Android. Un gestionnaire de fichiers compatible peut être nécessaire.
- Les suggestions cinéma et les historiques de Netflix/YouTube ne sont pas connectés dans cette version. Le fond procédural reste affiché tant qu'aucune image n'a été choisie.
- Compilation, lint et tests sur émulateur Android 11 réussis : dernier bouton du panneau et visibilité de la sélection. Validation sur la Xiaomi réelle encore nécessaire.

## Ce qui fonctionne dans cette première version

- Interface TV 16:9, contrôlable à la télécommande (flèches + OK)
- Détection et ouverture des applications TV installées, sans liste codée en dur
- Grille de tuiles translucides, focus agrandi, horloge
- Fond panoramique animé dessiné localement (aucun téléchargement, aucune vidéo sous licence)
- Défilement vertical automatique, actualisation des applications au retour à l'accueil
- Pas de permission Internet, pas de SDK analytics, pas de publicité, pas de root
- Activité HOME/LEANBACK pour pouvoir choisir GlassTV comme launcher

## Limites actuelles

- Le rendu *Liquid Glass* est une interprétation : transparence et reflets, pas un véritable flou de réfraction de tvOS.
- Le paysage est une animation procédurale, pas une vidéo 4K aérienne.
- La navigation et les ouvertures d'applications sont fonctionnelles, mais l'interface reste un **MVP** ; pas encore de réglages, favoris, réorganisation, météo ou prévisualisations vidéo.
- Les pubs et traceurs **des applications tierces et du système Xiaomi/Google** ne sont pas supprimés par ce launcher.
- Le launcher par défaut dépend du firmware ; ne pas désactiver le launcher système avant d'avoir testé GlassTV.

## Compiler sur GitHub sans PC

1. Créer un dépôt GitHub et y importer **le contenu du dossier GlassTV** (y compris `.github/workflows/build-apk.yml`).
2. Aller dans **Actions > Build GlassTV APK > Run workflow**.
3. Télécharger l'artifact `GlassTV-debug-apk` à la fin de la compilation.
4. Installer `app-debug.apk` sur la box via une application de transfert de fichiers / installateur APK compatible Android TV.
5. Ouvrir GlassTV, tester la télécommande et les applications, puis seulement ensuite essayer de le définir comme accueil par défaut.

Autre méthode : Android Studio avec JDK 17, Android SDK 35, Gradle 8.10.2, puis `gradle :app:assembleDebug`.

## Sécurité / confidentialité

L'application ne demande aucune permission réseau et ne contient pas de code de télémétrie. Elle lit uniquement les activités de lancement des applications installées. Aucune commande ADB destructive n'est requise pour tester l'APK.

## Dépannage

Si le launcher ne s'affiche pas comme accueil, ouvrir les paramètres Android TV pour vérifier les applications par défaut. Sur certains firmwares Xiaomi, le choix du launcher nécessite ADB. Ne jamais désactiver le launcher d'origine sans avoir confirmé qu'une méthode de restauration fonctionne.

## Validation de cette version

- Compilation Java 17 / Android Gradle Plugin 8.6.1 / Gradle 8.10.2.
- minSdk 28 (Android 9) : Android TV 11 / API 30 est pris en charge.
- APK universel sans bibliothèques natives : pas de restriction ARM 32/64 bits.
- Navigation dernière ligne corrigée, lancement au relâchement de OK et appui long pour actualiser.
- CI : compilation, lint, vérification de signature et manifeste, SHA-256, artifact Actions conservé 30 jours, APK également disponible en Release.
- Les essais physiques sur la Xiaomi restent à réaliser.

## Installation

Dans GitHub, ouvrir Actions, puis une exécution réussie de **Build GlassTV APK**,
et télécharger **GlassTV-debug-apk** (connexion au compte requise).
Extraire le ZIP et transférer **GlassTV-0.3.0-debug.apk** vers la box.
Autoriser temporairement l'installation depuis l'application de transfert utilisée,
puis ouvrir l'APK et lancer GlassTV depuis les applications.

Cet APK est une version de test signée avec la clé debug générée par le runner.
Une compilation sur un nouveau runner peut changer cette signature et nécessiter
une désinstallation avant réinstallation. Une clé de signature stable, conservée
dans les secrets GitHub, devra être mise en place avant les mises à jour durables.
Aucun contrôle ni installation automatique de mise à jour n'est implémenté.
Le propriétaire a rendu le dépôt public et autorisé la publication de l'APK en Release. Aucun token GitHub n'est embarqué dans l'app.
