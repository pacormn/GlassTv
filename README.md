# GlassTV

## Installer sur la TV

### [⬇ Télécharger GlassTV 0.5.6.apk](https://github.com/pacormn/GlassTv/releases/download/v0.5.6/GlassTV-0.5.6.apk)

Lien direct à ouvrir avec le navigateur de la TV ou Downloader. [Voir la Release 0.5.6](https://github.com/pacormn/GlassTv/releases/tag/v0.5.6).

## Version 0.5.6 publiée

Le diaporama TMDB affiche les films en salle et prochaines sorties. Les bannières et les logos du titre sont préchargés avant affichage ; un fondu lent évite le délai de chargement. Le titre apparaît après 3 secondes, en grand avec le logo du film si disponible, puis les genres et la durée sur une même ligne. Le salut est agrandi, les cinq favoris sont centrés et une ombre légère améliore la lisibilité du titre. La clé est stockée dans les secrets GitHub Actions, pas dans le dépôt. Comme l’application interroge TMDB directement, la clé est toutefois extractible de l’APK public.

Pour les prochaines compilations, les secrets **TMDB_API_KEY** et **GLASSTV_SIGNING_JSON** doivent rester configurés dans [GitHub Actions](https://github.com/pacormn/GlassTv/settings/secrets/actions). Lancer ensuite **Actions → Build GlassTV APK → Run workflow**. Conserver la même clé de signature pour installer les mises à jour sans désinstaller GlassTV. La migration depuis les premières versions signées en debug a nécessité une dernière désinstallation, qui efface les réglages.

## Accueil et navigation

- « Bonjour Paco » entre 6 h et 18 h, puis « Bonsoir Paco », selon l'heure de la box. Le texte est placé plus haut.
- Cinq emplacements fixes de favoris. La barre défile avec les applications, sans position fixe. La bannière disparaît dans la grille.
- Défilement animé avec déplacements de focus regroupés, sans animation concurrente du ScrollView. Marges autour de la sélection et découpage des lignes hors écran.
- Bouton supérieur arrondi avec animation de focus et survol. Centre de contrôle inspiré du verre translucide, défilant indépendamment des applications.
- Appui long OK sur une application : choisir l'un des cinq favoris à remplacer. Une application déjà favorite échange sa place avec celle choisie.
- Diaporama TMDB des films en salle et prochaines sorties en France, avec possibilité de choisir une bannière locale. L’historique des vidéos regardées n’est pas connecté.

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

[Validation 0.5.6](https://github.com/pacormn/GlassTv/actions/runs/42368619306) : compilation, lint, injection TMDB confirmée sans afficher la clé, signature permanente, démarrage Android 11 et navigation vérifiés. La fluidité réelle et la lecture distante de l’économiseur restent à vérifier sur la box.

La Release n'est publiée que si les tests réussissent et que la signature permanente est configurée. Le dépôt est public par choix du propriétaire. Aucune télémétrie ni publicité. Interface inspirée de tvOS, sans affiliation à Apple.
