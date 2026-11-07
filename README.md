# GlassTV

## Installer sur la TV

### [⬇ Télécharger la dernière version publiée](https://github.com/pacormn/GlassTv/releases/latest)

Dans la Release, ouvrir **Assets**, puis le fichier APK avec le navigateur de la TV ou Downloader.

## Version 0.5.3 préparée

Le code est compilé et testé. Le diaporama interroge TMDB en français pour les films en salle et les prochaines sorties en France ; les bannières changent automatiquement toutes les 15 secondes. La clé est injectée au moment de la compilation et n’est pas enregistrée dans le dépôt. Elle reste extractible de l’APK final, comme toute clé utilisée dans une application Android.

Pour afficher les bannières TMDB, ajoute le secret GitHub Actions **TMDB_API_KEY** avec ta clé API TMDB. Pour publier une Release installable qui pourra mettre à jour la même application, configure également **GLASSTV_SIGNING_JSON** avec le fichier privé de signature. La publication attend la configuration de la signature permanente :

1. Ouvrir [les secrets GitHub Actions](https://github.com/pacormn/GlassTv/settings/secrets/actions/new).
2. Créer le secret **GLASSTV_SIGNING_JSON**, contenant tout le contenu du fichier privé GlassTV-signing-secret.txt fourni au propriétaire.
3. Dans **Actions → Build GlassTV APK**, choisir **Run workflow** sur main. La compilation sans TMDB_API_KEY fonctionne, mais le diaporama reste désactivé. Sans GLASSTV_SIGNING_JSON, l’APK est fourni comme artefact de diagnostic et n’est pas publié en Release.

Ne jamais ajouter cette clé au dépôt. Sans ce secret, le workflow compile et teste, mais ne publie aucun APK en Release. Une ancienne installation signée avec une autre clé devra être désinstallée une fois, ce qui efface les réglages. Les versions suivantes conserveront la même signature.

## Accueil et navigation

- « Bonjour Paco » entre 6 h et 18 h, puis « Bonsoir Paco », selon l'heure de la box. Le texte est placé plus haut.
- Cinq emplacements fixes de favoris. La barre remonte en haut pendant le passage aux applications et conserve son opacité.
- Défilement animé avec déplacements de focus regroupés, sans animation concurrente du ScrollView. Marges autour de la sélection et découpage des lignes hors écran.
- Bouton supérieur arrondi avec animation de focus et survol. Centre de contrôle inspiré du verre translucide, défilant indépendamment des applications.
- Appui long OK sur une application : choisir l'un des cinq favoris à remplacer. Une application déjà favorite échange sa place avec celle choisie.
- Bannière personnalisable via le sélecteur Android. Le diaporama des dernières sorties cinéma et l'historique vidéo ne sont pas connectés.

## Économiseur vidéo

Lecteur Media3 avec quatre sources aériennes AVC/H.264, passage à la suivante en cas d'échec, délai d'attente limité et cache disque plafonné à 256 Mo. Ce cache ne garantit pas la disponibilité hors ligne de toutes les vidéos.

Le centre de contrôle permet de choisir une vidéo locale ; privilégier un MP4 H.264. Le mode développeur affiche la version en bas à droite et donne accès au bouton de test de l'économiseur. Une touche quitte la vidéo sans ouvrir une application derrière.

Attribution TMDB dans le centre de contrôle : « This product uses the TMDB API but is not endorsed or certified by TMDB. » Les erreurs de certificat HTTPS sont distinguées des autres échecs. La validation TLS reste active. L'accès aux sources distantes doit encore être confirmé sur la Xiaomi : l'environnement de test intercepte HTTPS et n'a pas permis de le valider. Le décodage d'une vidéo locale H.264 a été vérifié jusqu'à la première image réellement rendue.

## Télécommande Xiaomi

| Touche | Action dans GlassTV |
| --- | --- |
| Flèches | Naviguer |
| OK | Ouvrir |
| Maintenir OK | Choisir un emplacement favori |
| Retour | Fermer le panneau ou revenir à l'accueil |
| Maintenir Retour | Ouvrir le centre de contrôle |
| Bouton en haut à droite | Ouvrir le centre de contrôle |

Le centre de contrôle conserve le focus jusqu'au dernier bouton. Les gestes Menu restent disponibles pour les télécommandes qui transmettent cette touche.

« Définir comme accueil » utilise le rôle Android HOME. Le firmware Xiaomi peut continuer à ouvrir Android TV avec Maison. Un remappage global distinct peut être nécessaire ; GlassTV ne désactive pas le launcher système.

## Mises à jour

Recherche au retour dans GlassTV, au maximum toutes les six heures, téléchargement automatique et vérification SHA-256, du paquet, de la version et de la compatibilité de signature avant l'installateur Android. L'installation demande la confirmation Android et, si nécessaire, l'autorisation d'installer depuis GlassTV. Une installation silencieuse n'est pas implémentée.

La migration depuis une ancienne signature debug exige une réinstallation. La mise à jour sur la Xiaomi physique reste à valider de bout en bout.

## Compilation et validation

Android 9 minimum / API 28, cible API 35, compatible Android TV 11 / API 30. Java 17, Android Gradle Plugin 8.6.1 et Gradle 8.10.2.

[Validation 0.5.2](https://github.com/pacormn/GlassTv/actions/runs/42317520083) : compilation, lint, démarrage Android 11, décodage local H.264, sortie de l’économiseur, dernier bouton du panneau et visibilité de la sélection. La version 0.5.3 ajoute le diaporama TMDB ; lance le workflow après avoir ajouté le secret pour compiler l’APK avec les bannières. La fluidité réelle et la lecture distante restent à vérifier sur la box.

La Release n'est publiée que si les tests réussissent et que la signature permanente est configurée. Le dépôt est public par choix du propriétaire. Aucune télémétrie ni publicité. Interface inspirée de tvOS, sans affiliation à Apple.
