# Rapport de mise à jour — Intégration des Sprints (Ancien → Originale)

> **Projet** : Kaloy App Mobile  
> **Date** : 7 septembre 2026  
> **Objectif** : Intégrer les fonctionnalités développées dans l'ancienne version (Sprints 0, 1, 2) vers la version originale sous architecture Voyager/Koin.

---

## 1. Contexte

L'application Kaloy existe en deux versions :

| Version | Dossier | Architecture |
|---------|---------|-------------|
| **Originale** (v2) | `Kaloy-app-mobile-v2-main/` | Voyager + Koin + Material 3 |
| **Ancien** | `Ancien/Kaloy-app-mobile/` | Jetpack Navigation Compose |

La version **Originale** possède un système d'authentification complet (Welcome, Login, Register, OTP, Profil "Moi") mais un écran d'accueil vide (simple carte de bienvenue).

La version **Ancien** possède 3 écrans fonctionnels développés dans les Sprints 0, 1 et 2, mais utilise une architecture de navigation différente (`NavHost`/`rememberNavController`).

### Règle d'or
> Ne jamais modifier ni casser le code existant de la version Originale (authentification, configuration réseau, etc.). Tout le code ajouté est doté de variables en français.

---

## 2. Analyse préalable

Avant l'intégration, une analyse comparative a confirmé que les fichiers suivants sont **identiques** entre les deux versions :

- `data/api/KaloyApi.kt` — Client API (toutes les méthodes de requête)
- `data/model/Models.kt` — Modèles de données (375 lignes, toutes les entités)
- `ui/components/Components.kt` — Composants UI réutilisables (429 lignes)
- `ui/theme/KaloyTheme.kt` — Thème Material 3 (101 lignes)

**Conclusion** : Aucune fusion de la couche données n'est nécessaire. Seule la couche **présentation** (écrans) doit être migrée.

---

## 3. Sprint 0 — Écran d'accueil (HomeScreen)

### Fonctionnalité
L'écran d'accueil affiche les données dynamiques depuis le backend :
- **Historique d'écoute** récent (écoutes récentes de l'utilisateur)
- **Playlists éditoriales** à la une
- **Liste des artistes** populaires (carrousel horizontal)
- **Albums récents** (carrousel horizontal)
- **Chansons** (liste verticale avec durée)

### Fichier modifié
`presentation/home/HomeScreen.kt` — Réécrit pour intégrer le vrai contenu

### Architecture
```
HomeScreen (data class : Screen) — Voyager
│
├── AccueilViewModel (ViewModel)
│   ├── artistes : List<Artist>
│   ├── albums : List<Album>
│   ├── chansons : List<Song>
│   ├── playlistsEditoriales : List<EditorialPlaylist>
│   ├── ecoutesRecentes : List<ListeningHistory>
│   ├── enChargement : Boolean
│   └── erreur : String?
│
├── NavigationBar (conservée de l'original)
│   ├── Accueil → reste sur HomeScreen
│   ├── Recherche → push(EcranRechercheVoyager)
│   └── Moi → push(MoiScreen)
│
└── LazyColumn (contenu dynamique)
    ├── Header gradient + nom utilisateur
    ├── Barre de recherche → push(EcranRechercheVoyager)
    ├── Section "Récemment écouté"
    ├── Section "À la une"
    ├── Section "Artistes"
    ├── Section "Albums"
    └── Section "Chansons"
```

### Variables en français
| Anglais (Ancien) | Français (Nouveau) |
|---|---|
| `artists` | `artistes` |
| `songs` | `chansons` |
| `editorialPlaylists` | `playlistsEditoriales` |
| `recentListens` | `ecoutesRecentes` |
| `isLoading` | `enChargement` |
| `error` | `erreur` |
| `loadData()` | `chargerDonnees()` |
| `navigator` | `navigateur` |
| `displayName` | `nomAffiche` |
| `innerPadding` | `espaceInterieur` |

---

## 4. Sprint 1 — Écran de recherche (SearchScreen)

### Fonctionnalité
Permet à l'utilisateur de rechercher des artistes par nom de scène avec :
- **Debounce** de 400ms (attend la fin de la frappe)
- **Recherche minimum** : 2 caractères nécessaires
- **Résultats** : liste des artistes avec avatar, nom, type, badge certifié
- **Navigation** : clic sur un artiste → écran détail artiste

### Fichier créé
`presentation/search/EcranRechercheVoyager.kt` — Nouveau fichier Voyager

### Architecture
```
EcranRechercheVoyager (class : Screen) — Voyager
│
├── RechercheViewModel (ViewModel)
│   ├── rechercheTexte : String
│   ├── artistes : List<Artist>
│   ├── chansons : List<Song>
│   ├── enCoursDeRecherche : Boolean
│   ├── aCherche : Boolean
│   ├── surChangementTexte(nouveauTexte)
│   └── effectuerRecherche(texteRecherche) [privée]
│
├── Barre de recherche (OutlinedTextField)
│   ├── Bouton retour ← → navigateur.pop()
│   └── Champ texte avec debounce 400ms
│
└── Résultats (LazyColumn)
    ├── LigneRechercheArtiste (composant privé)
    │   ├── Avatar circulaire gradient
    │   ├── Nom + badge certifié
    │   └── Bouton "Voir" → EcranDetailArtisteVoyager
    └── Section chansons (préparé pour futur backend)
```

### Variables en français
| Anglais (Ancien) | Français (Nouveau) |
|---|---|
| `query` | `rechercheTexte` |
| `searchJob` | `tacheRecherche` |
| `isSearching` | `enCoursDeRecherche` |
| `hasSearched` | `aCherche` |
| `onQueryChange()` | `surChangementTexte()` |
| `performSearch()` | `effectuerRecherche()` |
| `ArtistSearchRow` | `LigneRechercheArtiste` |

---

## 5. Sprint 2 — Écran détail artiste (ArtistDetailScreen)

### Fonctionnalité
Affiche le profil complet d'un artiste :
- **Header** avec gradient, avatar circulaire et informations principales
- **Badge certifié** (✓) si l'artiste est vérifié
- **Année d'activité** ("Actif depuis...")
- **Biographie** complète
- **Albums** de l'artiste (carrousel horizontal)
- **Chansons** de l'artiste (liste avec numérotation et durée)

### Fichier créé
`presentation/artist/EcranDetailArtisteVoyager.kt` — Nouveau fichier Voyager

### Architecture
```
EcranDetailArtisteVoyager (data class : Screen) — Voyager
│   Paramètre : idArtiste: Long
│
├── DetailArtisteViewModel (ViewModel)
│   ├── artiste : Artist?
│   ├── albums : List<Album>
│   ├── chansons : List<Song>
│   ├── membres : List<ArtistGroupMember>
│   ├── enChargement : Boolean
│   ├── erreur : String?
│   └── chargerArtiste()
│
├── Header gradient (280dp)
│   ├── Bouton retour ← → navigateur.pop()
│   └── Row : Avatar + Nom + Type + Année
│
├── Section Biographie
├── Section Albums (LazyRow + AlbumCard)
└── Section Chansons (items + SongRow)
```

### Variables en français
| Anglais (Ancien) | Français (Nouveau) |
|---|---|
| `artistId` | `idArtiste` |
| `artist` | `artiste` / `artisteDetail` |
| `songs` | `chansons` |
| `members` | `membres` |
| `isLoading` | `enChargement` |
| `error` | `erreur` |
| `loadArtist()` | `chargerArtiste()` |
| `bio` | `biographie` |

---

## 6. Fichiers non modifiés (Originale - intacts)

Les fichiers suivants de la version originale n'ont **pas été touchés** :

| Fichier | Rôle |
|---------|------|
| `App.kt` | Point d'entrée Voyager → WelcomeScreen |
| `WelcomeScreen.kt` | Écran de bienvenue |
| `LoginScreen.kt` | Connexion |
| `RegisterStep1Screen.kt` | Inscription étape 1 |
| `RegisterClientScreen.kt` | Inscription client |
| `RegisterArtistTypeScreen.kt` | Type d'artiste |
| `RegisterArtistDetailsScreen.kt` | Détails artiste |
| `OtpScreen.kt` / `OtpViewModel.kt` | Vérification OTP |
| `MoiScreen.kt` / `MoiViewModel.kt` | Profil utilisateur |
| `AuthSessionManager.kt` | Gestion des sessions |
| `AuthRepository.kt` / `AuthRepositoryImpl.kt` | Repository auth |
| `KaloyHttpClient.kt` | Client HTTP sécurisé |
| `AppModule.kt` | Module Koin DI |
| `KaloyApi.kt` | Client API (déjà complet) |
| `Models.kt` | Modèles de données |
| `Components.kt` | Composants UI partagés |
| `KaloyTheme.kt` | Thème Material 3 |

---

## 7. Résumé des changements

| Action | Fichier | Sprint |
|--------|---------|--------|
| **MODIFIÉ** | `presentation/home/HomeScreen.kt` | Sprint 0 |
| **CRÉÉ** | `presentation/search/EcranRechercheVoyager.kt` | Sprint 1 |
| **CRÉÉ** | `presentation/artist/EcranDetailArtisteVoyager.kt` | Sprint 2 |

### Flux de navigation complet
```
WelcomeScreen
├── LoginScreen → HomeScreen
├── RegisterStep1Screen → ... → HomeScreen
└── "Visiteur" → HomeScreen(isVisitor=true)

HomeScreen
├── [Tab Recherche] → EcranRechercheVoyager
│   └── [Clic artiste] → EcranDetailArtisteVoyager
├── [Tab Moi] → MoiScreen
├── [Clic artiste] → EcranDetailArtisteVoyager
└── [Barre recherche] → EcranRechercheVoyager
```
