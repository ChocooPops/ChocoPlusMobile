# ChocoPlus Mobile (Android)

Application Android native de **ChocoPlus**, plateforme de streaming de films et de séries. Écrite en Kotlin avec Jetpack Compose, elle reprend écran par écran l'ancienne application React Native (les commentaires du code y font souvent référence : « Mirrors the RN app's … »).

## Fonctionnalités

- **Compte** : connexion, inscription avec code de vérification par e-mail, écran « Bon retour » quand une session est restaurée, gestion du profil (pseudo, photo, mot de passe).
- **Navigation** : Accueil, Films, Séries, Catalogue filtrable, Ma liste, Recherche, fiches licence.
- **Fiches film et série** : synopsis, distribution, titres similaires, épisodes par saison, barre de progression de visionnage.
- **Lecteur ChocoPlayer** (libVLC) :
  - reprise à la dernière position ;
  - choix des pistes audio et sous-titres, avec mémorisation de la préférence VF/VO ;
  - vitesse de lecture, recadrage, double-tap ±10 s ;
  - liste des épisodes et diffusion Chromecast ;
  - envoi de la progression au backend à la sortie ;
  - pas de mise en veille pendant la lecture.
- **Téléchargements hors ligne** (rôles `ADMIN` et `FAMILY` uniquement) : films, épisodes ou saisons entières, avec progression, annulation, suppression et une page dédiée qui fonctionne sans réseau.
- **Mode hors ligne** : bouton « Hors ligne » en haut à gauche de tous les écrans de lancement (configuration du serveur, chargement, « Bon retour », connexion, inscription). Il donne accès aux vidéos téléchargées sans compte ni serveur.
- **Configuration du serveur dans l'app** : URL du backend et en-tête secret saisis dans un formulaire, avec test de connexion (voir plus bas).
- **Profil** : historique, statistiques de visionnage, paramètres (qualité d'image, page de démarrage, décodage vidéo, langue…), support, documentation PDF, administration des utilisateurs (`ADMIN`).

## Stack technique

| Domaine | Choix |
|---|---|
| Langage / UI | Kotlin 2.2, Jetpack Compose (BOM 2024.12), Material 3 |
| Navigation | Navigation Compose |
| Réseau | Retrofit 2 + OkHttp 4 + kotlinx.serialization |
| Images | Coil 3 |
| Vidéo | libVLC Android 3.7 (`libvlc-all`) |
| Cast | Google Cast Framework + MediaRouter |
| Persistance | DataStore Preferences (token, préférences), SharedPreferences (configuration du serveur), fichiers internes (téléchargements) |
| Build | Gradle 9.5 (wrapper), Android Gradle Plugin 9.3, JDK 17 |
| SDK | `minSdk` 24 (Android 7.0), `targetSdk` / `compileSdk` 36 |

## Prérequis

- Android Studio récent (compatible AGP 9.3), ou seulement le SDK Android et un JDK 17 pour compiler en ligne de commande
- Un appareil ou un émulateur Android 7.0 ou plus

## Configuration du serveur

L'URL du backend et l'en-tête secret de l'API se saisissent **dans l'application**. Le fichier `.env` n'est plus utilisé : le build n'en a plus besoin, et un `.env` encore présent à la racine est ignoré (il peut être supprimé). Changer de serveur ne demande donc plus de recompiler.

- **Premier lancement** : l'app affiche directement le formulaire « Communication avec le serveur ».
- **Ensuite** : le bouton engrenage, en haut à droite des écrans de chargement, de connexion et « Bon retour », rouvre le formulaire.
- **Tester** envoie une requête `GET` sur l'URL seule, sans endpoint, avec uniquement l'en-tête secret :

  | Réponse | Résultat affiché |
  | --- | --- |
  | 2xx | Connexion réussie |
  | 401 / 403 | Serveur joignable, en-tête refusé |
  | Autre 4xx | Serveur joignable, en-tête accepté |
  | 5xx | Le serveur répond avec une erreur |
  | Aucune réponse | Serveur injoignable, avec la raison |

- **Enregistrer** stocke les valeurs sur l'appareil (stockage privé de l'app) et relance l'application. Les téléchargements en cours sont interrompus, puis reprennent à la connexion suivante.
- Les champs du nom et de la clé de l'en-tête sont masqués. L'œil les affiche, et toute nouvelle saisie les masque de nouveau.
- **Serveur injoignable ou configuration erronée** : l'app ouvre la page de connexion, jamais directement le mode hors ligne. Un bandeau signale le problème et rappelle de vérifier la configuration. Le token est conservé, et la session se restaure seule quand le serveur répond de nouveau.

Valeurs habituelles :

| Champ | Valeur |
| --- | --- |
| Url principale du serveur | `https://api.chocoplus.net` |
| Nom du header de sécurité | `X-API-Secret-Key-Choco-Plus` |
| Clé du header de sécurité | secret fourni par l'équipe backend |

- **Mise à jour depuis une version qui utilisait `.env`** : la configuration est à ressaisir au premier lancement.
- **Backend local** : `localhost` désigne le téléphone lui-même. Utiliser `10.0.2.2:3000` dans l'émulateur, ou l'IP du PC sur le réseau local sur un vrai téléphone. Le manifeste bloque le HTTP non chiffré (`usesCleartextTraffic="false"`) : il faut l'autoriser pour viser un backend en `http://`.

## Compiler et lancer

Depuis Android Studio : ouvrir le dossier du projet, attendre la synchronisation Gradle, puis lancer la configuration `app`.

En ligne de commande :

```bash
# Windows
gradlew.bat assembleDebug
gradlew.bat installDebug      # installe sur l'appareil connecté

# macOS / Linux
./gradlew assembleDebug
./gradlew installDebug
```

L'APK debug est produit dans `app/build/outputs/apk/debug/`.

Pour une version release, aucune configuration de signature n'est encore définie dans `app/build.gradle.kts`. Il faut en ajouter une, et ne pas versionner le keystore ni ses mots de passe.

## Structure du projet

```
app/src/main/java/com/chocoplusmobile/
├── MainActivity.kt          Point d'entrée, monte le thème et la navigation
├── ChocoApp.kt              Application : chargement de la config serveur, chargeur d'images Coil, init des téléchargements
├── api/                     Accès au backend
│   ├── ApiService.kt        Interface Retrofit (toutes les routes non-admin + admin utilisateurs)
│   ├── NetworkModule.kt     Client OkHttp, en-têtes (secret, JWT, langue), gestion des 401, sonde du serveur
│   ├── ServerConfig.kt      Configuration du serveur (URL + en-tête secret), stockage, test de connexion
│   ├── Types.kt             Modèles @Serializable calqués sur les DTO du backend
│   ├── Stream.kt            URLs de streaming (JWT en paramètre ?token=)
│   ├── Playback.kt          Cible du lecteur + progression mémorisée localement
│   └── ContentCache.kt      Cache mémoire des écrans Accueil / Films / Séries
├── state/                   AppViewModel (auth, disponibilité du serveur, Ma liste, préférences, mode hors ligne), Prefs
├── storage/                 DataStore : token, préférences
├── navigation/              Graphe de navigation (auth, principal, hors ligne), NavCache
├── screens/                 Un dossier par écran (auth, home, browse, catalogue, detail,
│                            mylist, search, player, downloads, profile)
├── download/                DownloadManager (file, reprise, stockage) + DownloadService
├── video/                   Lecteur libVLC, contrôleur Cast
├── cast/                    Options du Google Cast Framework
├── data/                    Adaptateurs API → UI, qualité d'image, formats d'affiches
├── i18n/                    Langue de l'app et traductions des genres/métiers
└── ui/                      Composants réutilisables (dont ServerConfigForm.kt, le formulaire serveur), icônes, thème
```

## Points d'architecture

**Configuration du serveur.** `ServerConfigHolder` charge l'URL et l'en-tête secret au démarrage, dans `ChocoApp.onCreate`, avant toute requête. Retrofit, l'intercepteur, les URLs de streaming et les téléchargements lisent tous cette configuration. Une nouvelle configuration n'est jamais appliquée à chaud : l'enregistrement relance le processus, pour que le client réseau, les caches et la session repartent sur le nouveau serveur. L'en-tête secret n'est plus compilé dans l'APK.

**Authentification.** Le JWT est stocké dans DataStore et ajouté à chaque requête par un intercepteur OkHttp, avec l'en-tête secret et `Accept-Language`. Une réponse 401 déconnecte l'utilisateur. Au démarrage, le token est validé par `user/current-user`. Si le serveur est indisponible (erreur réseau, timeout ou 5xx), l'app affiche la page de connexion sans effacer le token. Elle sonde ensuite le serveur toutes les 10 s et restaure la session dès qu'il répond. Une erreur 401/403 à la connexion reste traitée comme une erreur d'identifiants.

Limite connue : le backend répond 401 aussi bien pour un token invalide que, selon sa configuration, pour un en-tête secret erroné. Dans ce second cas, l'app supprime quand même le token, et il faut se reconnecter après avoir corrigé la configuration.

**Streaming.** Les routes de streaming reçoivent le JWT en paramètre `?token=` et non en en-tête. Un lecteur ou un récepteur Cast peut ainsi lire l'URL directement.

**Lecteur.** `PlayerScreen` s'appuie sur libVLC, choisi pour sa tolérance aux codecs et la vraie sélection de pistes audio et sous-titres. Le décodage matériel ou logiciel se règle dans les paramètres. Si la vidéo est téléchargée, le fichier local est toujours prioritaire sur le streaming. La progression (`position / durée × 100`) est envoyée à `stat-user/save-watching-progress-movie|episode/:id` à la sortie et au passage en arrière-plan. Elle est aussi gardée en mémoire (`WatchProgressStore`), pour que les fiches et la reprise restent à jour sans recharger les données.

**Téléchargements.** `DownloadManager` gère une file de 2 téléchargements en parallèle et reprend un téléchargement interrompu (en-tête HTTP `Range`). Les métadonnées et images sont stockées localement, avec des URLs réécrites en `file:`. Un index JSON vit dans `filesDir/downloads`. `DownloadService`, un service au premier plan de type `dataSync`, garde le processus actif et affiche la progression en notification. Routes utilisées :
- `download/download-movie/:id` et `download/download-episode/:id` pour les vidéos ;
- `media/:id` et `series/episode/:id` pour les métadonnées.

**Rôles.**

| Rôle | Accès |
|---|---|
| `USER` | Catalogue et lecture en streaming |
| `FAMILY` | `USER` + téléchargements hors ligne |
| `ADMIN` | `FAMILY` + administration des utilisateurs |
| `NOT_ACTIVATE`, `SUSPENDED` | Gérés côté backend |

## Tests

Aucun test n'est écrit pour l'instant. Les dépendances JUnit, Espresso et Compose UI Test sont déjà déclarées, et les tests sont à placer dans `app/src/test` (tests unitaires) et `app/src/androidTest` (tests instrumentés).
