# GlassTV

## Installer sur la TV

### [⬇ Télécharger GlassTV 0.4.1.apk](https://github.com/pacormn/GlassTv/releases/download/v0.4.1/GlassTV-0.4.1-debug.apk)

Ouvrir ce lien dans le navigateur de la TV ou Downloader puis installer l'APK.
[Voir les fichiers de la Release](https://github.com/pacormn/GlassTv/releases/tag/v0.4.1).

Les APK utilisent actuellement des signatures debug de runners distincts. Si Android refuse la mise à jour pour incompatibilité de signature, désinstaller l'ancienne version puis installer celle-ci (les réglages seront effacés). Une clé de signature durable reste nécessaire pour garantir les mises à jour sur place entre toutes les compilations.

## Accueil épuré

- « Bonjour Paco » entre 6 h et 18 h, puis « Bonsoir Paco », selon l'heure de la box ; actualisé pendant l'utilisation.
- Aucun nom GlassTV ni sous-titre d'application dans l'en-tête.
- Cinq emplacements fixes de favoris, sans sixième tuile. Les emplacements libres sont remplis avec d'autres applications installées ; si moins de cinq applications existent, des emplacements visuels vides complètent la rangée.
- Appui long OK sur une application : choisir l'un des cinq emplacements à remplacer. Une application déjà favorite échange sa place avec celle choisie.
- Descendre révèle toutes les applications, avec un défilement animé à ralentissement progressif, un fondu de la salutation et une atténuation du fond. Les animations réduites restent disponibles.
- Bannière personnalisable depuis le centre de contrôle, via le sélecteur Android. Les suggestions cinéma et historiques vidéo ne sont pas connectés.

## Télécommande Xiaomi

| Touche | Action dans GlassTV |
| --- | --- |
| Flèches | Naviguer |
| OK | Ouvrir |
| Maintenir OK | Choisir un emplacement favori |
| Retour | Fermer le panneau ou revenir à l'accueil |
| Maintenir Retour | Ouvrir le centre de contrôle |
| Bouton en haut à droite | Ouvrir le centre de contrôle |

Les gestes Menu restent disponibles pour les télécommandes qui transmettent cette touche.

Le centre de contrôle défile et conserve le focus dans son panneau, jusqu'au dernier bouton, sans naviguer vers les applications derrière.

« Définir comme accueil » utilise le rôle Android HOME. Un firmware Xiaomi peut continuer à ouvrir Android TV via la touche Maison même si ce rôle est attribué à GlassTV. Un remappage global distinct, configuré sur la box, peut alors être nécessaire. GlassTV ne désactive pas le launcher système. Google, volume, Netflix et Prime restent gérés par Android.

## Mises à jour

Activées par défaut à la demande du propriétaire :
- Recherche des Releases du dépôt au retour dans GlassTV, au maximum une fois toutes les six heures. Aucune recherche périodique lorsque GlassTV n'est pas actif.
- Téléchargement automatique d'une version plus récente par le gestionnaire Android.
- Vérification SHA-256 avant de proposer l'installation.
- Confirmation via l'installateur Android et autorisation d'installation depuis GlassTV si nécessaire.
- Options « Mises à jour auto » et « Rechercher une mise à jour » dans le centre de contrôle.

Les préversions de ce dépôt sont incluses. Une installation silencieuse n'est pas implémentée. Les signatures debug différentes peuvent empêcher l'installation sur place. La vérification d'empreinte ne corrige pas cette incompatibilité.

## Compilation et validation

Android 9 minimum / API 28, cible API 35, compatible Android TV 11 / API 30. APK universel sans bibliothèque native.
Java 17, Android Gradle Plugin 8.6.1 et Gradle 8.10.2.

GitHub Actions compile, exécute lint, vérifie la signature, teste le démarrage et la navigation dans un émulateur Android 11, puis publie une préversion avec APK et SHA-256. Le test vérifie la sélection du dernier bouton du centre de contrôle et la visibilité de la sélection dans la grille.

La détection et l'installation d'une future mise à jour n'ont pas encore été validées de bout en bout sur la Xiaomi physique. La fluidité réelle et les particularités des touches restent à vérifier sur la box.

L'application demande Internet pour les mises à jour et l'autorisation de demander une installation. Aucune télémétrie ni publicité. Le dépôt et les Releases sont publics par choix du propriétaire. L'interface est inspirée de tvOS sans affiliation à Apple ni services Apple intégrés.
