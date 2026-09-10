# Sources d'ebooks gratuits — Reader's Bookshop

Langues de la série Reader's : anglais, français, allemand, espagnol, portugais, russe.
État vérifié le 10 septembre 2026 (requête HTTP depuis la Suisse). « OPDS » = catalogue
Atom/OPDS exploitable directement par une app ; « API » = interface JSON/XML documentée ;
« HTML » = site à parcourir/scraper, pas d'interface prévue pour les machines.

Légende du statut juridique :
- **DP-US** : domaine public selon la loi américaine (publié avant 1931, ou auteur mort il y
  a plus de 70 ans). Certaines œuvres ne sont *pas* DP en Suisse/UE (auteur mort après 1955).
- **DP-CA** : domaine public au Canada (auteur mort avant 1972 ; depuis 2023 vie+70 non
  rétroactif). Beaucoup d'œuvres DP-CA ne le sont pas en Suisse/UE.
- **DP-CH/UE** : vie + 70 ans, le critère pertinent pour l'utilisateur suisse ou européen.
- **Libre** : Creative Commons ou licence libre déclarée par l'auteur/éditeur.
- **PCN** : prêt contrôlé numérique (Open Library) : emprunt d'une copie numérisée, compte
  requis, pas de téléchargement définitif hors DRM. C'est le seul modèle légal proche de
  « je ne prends que ce que je possède déjà ».

Recommandation pour l'app : quand la source fournit l'année de décès de l'auteur (Gutendex,
Open Library, Wikisource, BnR), calculer soi-même « DP en Suisse/UE » et afficher un badge,
plutôt que se reposer sur le seul engagement de l'utilisateur.

---

## 0. Ce que l'app fait de cette liste (vérifié le 10 septembre 2026)

Sources **interrogées par l'app** (Reader's Bookshop 1.0.0), avec ce qui a été contrôlé :

| Source | Accès utilisé | Conditions d'utilisation | Constat |
|---|---|---|---|
| Project Gutenberg | OPDS `ebooks/search.opds/?query=…+l.fr` puis le flux du livre | La page « robot access » interdit les outils automatiques sur le site web mais l'OPDS est publié pour les apps de lecture ; une requête par recherche et par livre | Fonctionne dans les six langues ; le flux du livre donne « Author: Voltaire, 1694-1778 », d'où le badge DP |
| Standard Ebooks | Page de recherche HTML + liens `/downloads/…epub` | Les flux OPDS sont réservés aux donateurs ; le site et les fichiers (CC0) sont ouverts. Ils offrent l'accès aux flux aux projets open source sur demande : à leur écrire | Fonctionne |
| Wikisource ×6 | API MediaWiki (`list=search`, `action=parse`) avec User-Agent descriptif ; l'EPUB est assemblé par l'app | Politique User-Agent de Wikimedia respectée. **ws-export est derrière Anubis (défi JS anti-bot)**, donc inutilisable par une app : d'où l'assemblage local | Fonctionne ; 30 chapitres pour Candide/Garnier 1877 |
| Bibliothèque numérique romande | COPS `opds/index.php?page=9&query=…&scope=book`, puis `scope=author` si vide | Catalogue OPDS public ; 403 pour curl sans User-Agent de navigateur, passe avec l'UA de l'app | Fonctionne |
| Ebooks libres et gratuits | OPDS `opds/feed.php?mode=search&query=…` | OpenSearch public | Fonctionne |
| textos.info | OPDS `busqueda.atom?query=…` (le modèle OpenSearch du site est mal formé, `&` au lieu de `?`) | OpenSearch public | Fonctionne |
| Internet Archive | `advancedsearch.php` avec `NOT access-restricted-item:true`, puis `metadata/{id}/files` | API publique ; les titres en prêt contrôlé sont exclus de la requête | Fonctionne ; surtout des scans OCR |
| Anna's Archive | Recherche HTML, page md5, page slow_download, API `fast_download.json` avec clé | Bibliothèque parallèle ; **désactivée par défaut**, avertissement à l'activation. Domaines lus dans l'infobox Wikipédia (.pk, .gd + .gl en repli) et classés par HEAD. **DDoS-Guard** : défi JS résolu dans un WebView caché, hCaptcha montré à l'utilisateur quand le site l'exige | Domaines et classement vérifiés ; le défi affiche un hCaptcha sur l'émulateur, non résolu ici : parsing des résultats et téléchargement **à valider sur téléphone** |

Écartées ou laissées au navigateur, et pourquoi :
- **Feedbooks** : 403 sur tous les flux, même avec un UA de navigateur (Cloudflare). Retiré.
- **BEQ** : `/opds/` redirige vers une page d'erreur ; lien seulement.
- **Elejandría** : la recherche passe par Google CSE en JavaScript ; lien seulement.
- **Projeto Livro Livre** : blog Blogspot, HTTPS cassé ; lien seulement.
- **Gutendex** : l'API tierce est derrière Cloudflare et son auteur demande d'héberger sa propre instance ; l'OPDS de Gutenberg suffit.
- **Projekt Gutenberg-DE** : interdit le scraping ; lien seulement.
- **Luso Livros, Biblioteca Virtual Universal (biblioteca.org.ar), Biblioteca Ayacucho, Bibliothèque numérique de Lisieux** : DNS mort le 10 septembre 2026.

## 1. Sources multilingues (couvrent les six langues)

| Source | URL | Contenu | Accès machine | Statut |
|---|---|---|---|---|
| Project Gutenberg | gutenberg.org | ~75 000 titres ; EN majoritaire, FR ~3 500, DE ~2 500, ES ~1 000, PT ~700, RU ~100 | OPDS `gutenberg.org/ebooks.opds/`, API JSON **Gutendex** `gutendex.com/books?languages=fr` (donne naissance/décès de l'auteur), catalogue CSV, miroirs rsync | DP-US |
| Internet Archive | archive.org | Numérisations DP, textes DP, ebooks libres | API `archive.org/advancedsearch.php` (JSON), téléchargement direct `archive.org/download/{id}` | DP-US, Libre |
| Open Library | openlibrary.org | Métadonnées + lien vers IA ; **prêt contrôlé numérique** de livres sous droits | API `openlibrary.org/search.json`, `/api/books` ; emprunt nécessite un compte IA | DP-US, PCN |
| Wikisource (fr, en, de, es, pt, ru) | xx.wikisource.org | Transcriptions relues ; forte en FR, DE, RU | API MediaWiki ; export EPUB/PDF/MOBI via **ws-export** `ws-export.wmcloud.org/?lang=fr&title=…` | DP-CH/UE (règle vie+70 respectée) |
| HathiTrust | hathitrust.org | 17 M de volumes numérisés, « full view » pour le DP | API Bibliographic + Data API ; téléchargement PDF complet réservé aux membres, page par page libre ; bloque les robots (403) | DP-US |
| Google Livres | books.google.com | DP en « affichage complet », EPUB/PDF | Pas d'API de téléchargement exploitable | DP-US |
| Europeana | europeana.eu | Agrégateur des bibliothèques européennes, dont textes | API REST (clé gratuite) | DP-CH/UE, Libre |
| DOAB / OAPEN | doabooks.org, library.oapen.org | ~90 000 livres académiques en accès ouvert, toutes langues | API REST + OAI-PMH | Libre (CC) |
| OpenEdition Books | books.openedition.org | Sciences humaines, FR surtout, aussi EN/ES/PT/DE | OAI-PMH ; une partie en « freemium » (HTML libre, EPUB payant) | Libre / mixte |
| Unglue.it | unglue.it | Ebooks CC et « libérés » | OPDS `unglue.it/api/opds/` (clé) | Libre |
| Smashwords / Draft2Digital | smashwords.com (free) | Auteurs contemporains qui offrent des titres | HTML, pas d'API publique | Libre (gratuit sur décision de l'auteur) |
| Feedbooks | feedbooks.com | Section domaine public multilingue (EN, FR, DE, ES, PT…) | OPDS historique `feedbooks.com/publicdomain/catalog.atom` : **403 aujourd'hui**, à retester depuis l'app | DP |
| ManyBooks | manybooks.net | Reprend Gutenberg avec belle mise en page | OPDS `manybooks.net/opds/` **403 pour curl**, compte requis pour télécharger | DP-US |
| Anna's Archive | (déjà intégré via le fork Openlib eink) | Agrège Libgen, Z-Library, IA, Sci-Hub | API + scraping (voir fork) | Bibliothèque parallèle ; **pas légal en Suisse/UE pour les œuvres sous droits, case cochée ou non**. Inutile d'ajouter Libgen/Z-Library séparément : Anna's les couvre. |

## 2. Anglais

| Source | URL | Contenu | Accès machine | Statut |
|---|---|---|---|---|
| Standard Ebooks | standardebooks.org | ~1 200 classiques Gutenberg retravaillés, typographie soignée, EPUB/KEPUB/AZW3 | OPDS `standardebooks.org/feeds/opds` **réservé aux donateurs (401)** ; les pages et fichiers restent libres ; tout le corpus est sur GitHub (`github.com/standardebooks`) | DP-US |
| Faded Page (Distributed Proofreaders Canada) | fadedpage.com | ~6 000 titres DP au Canada (auteurs morts avant 1972) : Orwell, Woolf, Hemingway… | HTML, liste par auteur ; téléchargement EPUB/MOBI/PDF direct | DP-CA — **souvent pas DP en CH/UE** |
| Project Gutenberg Canada | gutenberg.ca | Idem, plus ancien | HTML | DP-CA |
| Project Gutenberg Australia | gutenberg.net.au | DP australien | HTML | DP-AU (vie+70 depuis 2005 ; anciens titres vie+50) |
| Global Grey | globalgreyebooks.com | ~2 000 classiques, EPUB/PDF/Kindle | HTML | DP-US |
| Planet eBook | planetebook.com | ~100 classiques bien mis en page | HTML | DP |
| Loyal Books | loyalbooks.com | Livres audio + ebooks Gutenberg | RSS | DP-US |
| Sacred Texts | sacred-texts.com | Textes religieux/ésotériques | HTML (403 aux robots) | DP |
| CCEL | ccel.org | Classiques chrétiens | HTML, EPUB/PDF | DP |
| Perseus | perseus.tufts.edu | Classiques gréco-latins + traductions EN | API XML | Libre |
| Baen Free Library | baen.com (free-library) | SF/fantasy offerte par l'éditeur | HTML | Libre (décision éditeur) |
| OpenStax | openstax.org | Manuels universitaires | HTML, PDF | Libre (CC-BY) |
| Open Textbook Library | open.umn.edu/opentextbooks | Manuels libres | HTML | Libre |
| National Academies Press | nap.nationalacademies.org | Rapports scientifiques, PDF gratuits (compte) | HTML | Libre |
| Bartleby | bartleby.com | Textes de référence | HTML (403 aux robots) | DP |

## 3. Français

| Source | URL | Contenu | Accès machine | Statut |
|---|---|---|---|---|
| **Bibliothèque numérique romande** | ebooks-bnr.com | ~2 000 titres, auteurs romands et classiques, EPUB/PDF/Kindle soignés ; **Suisse**, applique la règle vie+70 | OPDS `ebooks-bnr.com/opds/` renvoie 403 à curl (protection anti-robot, à tester avec un User-Agent d'app) ; sinon HTML | DP-CH/UE |
| **Ebooks libres et gratuits** | ebooksgratuits.com | ~3 000 titres, EPUB/MOBI/PDF, le doyen des sites FR | **OPDS fonctionnel** `ebooksgratuits.com/opds/` | DP-CH/UE |
| **Bibliothèque électronique du Québec** | beq.ebooksgratuits.com | ~1 800 titres, collections « À tous les vents », « Littérature québécoise » | OPDS `beq.ebooksgratuits.com/opds/` (répond 200) | DP-CA/UE (mixte, prudence) |
| **Nos Livres** | noslivres.net | **Méta-catalogue** qui agrège BnR, ELG, BEQ, Bibebook, Efele, Atramenta, Gutenberg… ~8 000 titres | HTML, fichier catalogue téléchargeable ; le point d'entrée le plus utile pour l'app | DP |
| Bibebook | bibebook.com | ~1 700 EPUB propres | HTML (l'OPDS a disparu) | DP-CH/UE |
| Efele.net/ebooks | efele.net/ebooks | Quelques centaines d'EPUB très soignés | HTML | DP |
| Atramenta | atramenta.net | DP + auteurs contemporains sous CC | HTML | DP + Libre |
| Bouquineux | bouquineux.com | ~700 classiques EPUB/Kindle | HTML | DP |
| Livres pour tous | livrespourtous.com | ~6 000 liens vers PDF gratuits | HTML | DP / Libre |
| Gallica (BnF) | gallica.bnf.fr | Millions de numérisations, EPUB pour les textes océrisés | **API SRU** `gallica.bnf.fr/SRU?…` (XML), IIIF | DP-CH/UE |
| Bibliothèque numérique TV5MONDE | bibliothequenumerique.tv5monde.com | ~600 classiques EPUB/PDF | HTML (403 aux robots) | DP |
| Les Classiques des sciences sociales (UQAC) | classiques.uqac.ca | ~7 000 textes SHS, PDF/EPUB/DOCX | HTML ; timeout depuis la Suisse ce jour, site réputé actif | DP + Libre (accord des auteurs) |
| Libre Théâtre | libretheatre.fr | Pièces DP en EPUB/PDF | HTML | DP |
| ABU (CNAM) | abu.cnam.fr | ~300 textes bruts, historique | HTML, texte brut | DP |
| Athena (Univ. de Genève) | athena.unige.ch | Textes FR, HTML/PDF, **Suisse** | HTML | DP |
| BAnQ numérique | numerique.banq.qc.ca | Patrimoine québécois numérisé | API | DP-CA |
| e-rara / e-helvetica (BN suisse) | e-rara.ch, e-helvetica.nb.admin.ch | Imprimés suisses anciens ; e-helvetica contient aussi des ebooks en libre accès | e-rara : OAI-PMH, IIIF | DP-CH |
| Wikisource FR | fr.wikisource.org | ~ 400 000 pages relues | API + ws-export | DP-CH/UE |
| Gutenberg FR (via Gutendex `languages=fr`) | — | ~3 500 titres | API | DP-US |

## 4. Allemand

| Source | URL | Contenu | Accès machine | Statut |
|---|---|---|---|---|
| Projekt Gutenberg-DE | projekt-gutenberg.org | ~10 000 œuvres, le plus grand corpus DE | HTML seulement ; EPUB vendus sur DVD ; **le site interdit le scraping et bloque les USA** — à traiter comme lien externe, pas comme source de téléchargement | DP-CH/UE |
| Zeno.org | zeno.org | Littérature, philosophie, dictionnaires | HTML | DP |
| TextGrid Repository | textgridrep.org | « Digitale Bibliothek » : ~ 100 000 textes littéraires DE, TEI/XML + EPUB | API REST, OAI-PMH, téléchargement libre | Libre (CC-BY) |
| Deutsches Textarchiv | deutschestextarchiv.de | ~ 5 000 textes 1600–1900, TEI | API, OAI-PMH | Libre (CC-BY-SA) |
| Ngiyaw eBooks | ngiyaw-ebooks.org | EPUB DP soignés, surtout auteurs oubliés | HTML | DP-CH/UE |
| Bibliotheca Augustana | hs-augsburg.de/~harsch/augustana.html | Textes DE/LA/GR/FR/IT/ES, HTML | HTML | DP |
| Deutsche Digitale Bibliothek | deutsche-digitale-bibliothek.de | Agrégateur national | API `api.deutsche-digitale-bibliothek.de` (clé gratuite) | DP-CH/UE |
| Digitale Sammlungen (BSB Munich) | digitale-sammlungen.de | Numérisations | IIIF, OAI | DP |
| Wikisource DE | de.wikisource.org | Très stricte sur les sources, bonne qualité | API + ws-export | DP-CH/UE |
| Gutenberg DE (Gutendex `languages=de`) | — | ~2 500 titres | API | DP-US |

## 5. Espagnol

| Source | URL | Contenu | Accès machine | Statut |
|---|---|---|---|---|
| Biblioteca Virtual Miguel de Cervantes | cervantesvirtual.com | ~ 200 000 registres, textes HTML/PDF, la référence hispanique | OAI-PMH, données ouvertes `data.cervantesvirtual.com` | DP-CH/UE |
| **Elejandría** | elejandria.com | ~ 4 000 EPUB/PDF/MOBI DP, bien classés | HTML | DP-CH/UE |
| **textos.info** | textos.info | ~ 3 000 EPUB DP | **OPDS fonctionnel** `textos.info/opds` | DP-CH/UE |
| Ganso y Pulpo | gansoypulpo.com | Quelques centaines d'EPUB très soignés | HTML | DP |
| Freeditorial | freeditorial.com | Classiques + auteurs contemporains gratuits, ES/EN | HTML | DP + Libre |
| Ciudad Seva | ciudadseva.com | Nouvelles et poèmes, HTML | HTML | DP (+ quelques textes sous droits, prudence) |
| El Libro Total | ellibrototal.com | ~ 50 000 titres en lecture en ligne (Colombie) | HTML, pas de téléchargement | DP |
| Biblioteca Digital Hispánica (BNE) | bdh.bne.es | Numérisations de la BNE | API/OAI (403 aux robots sur la page d'accueil) | DP |
| Memoria Chilena | memoriachilena.gob.cl | PDF d'œuvres chiliennes | HTML | DP |
| Wikisource ES | es.wikisource.org | — | API + ws-export | DP-CH/UE |
| Gutenberg ES (Gutendex `languages=es`) | — | ~1 000 titres | API | DP-US |
| ~~Biblioteca Virtual Universal (biblioteca.org.ar)~~ | — | **hors ligne** (DNS) | — | — |
| ~~Biblioteca Ayacucho~~ | — | **hors ligne** (DNS) | — | — |
| ePubLibre, Lectulandia, Espaebook | — | Bibliothèques parallèles : **à exclure** | — | illégal |

## 6. Portugais

| Source | URL | Contenu | Accès machine | Statut |
|---|---|---|---|---|
| **Domínio Público** (gouv. brésilien) | dominiopublico.gov.br | ~ 190 000 fichiers, PDF de classiques lusophones ; lent et **403 aux robots** ce jour | HTML, formulaire de recherche | DP |
| Biblioteca Nacional Digital (Portugal) | purl.pt / bndigital.bnportugal.gov.pt | Numérisations de la BN portugaise | API/OAI | DP |
| BNDigital (Brésil) | bndigital.bn.gov.br | Numérisations de la BN brésilienne (403 aux robots) | HTML | DP |
| Literatura Brasileira (NUPILL/UFSC) | literaturabrasileira.ufsc.br | ~ 2 000 textes littéraires brésiliens, HTML/PDF | HTML | DP |
| Brasiliana USP (BBM) | digital.bbm.usp.br | Numérisations de la collection Mindlin | HTML, IIIF | DP |
| Projeto Livro Livre | projetolivrolivre.com | EPUB/PDF DP soignés, PT-BR | HTML | DP |
| eBooksBrasil | ebooksbrasil.org | Site ancien, PDF/EPUB DP | HTML | DP |
| Livros Grátis | livrosgratis.com.br | Thèses, textes DP, PDF | HTML | DP + Libre |
| Biblioteca Digital Camões | cvc.instituto-camoes.pt/biblioteca-digital | Classiques portugais en PDF | HTML | DP |
| Wikisource PT | pt.wikisource.org | — | API + ws-export | DP-CH/UE |
| Gutenberg PT (Gutendex `languages=pt`) | — | ~700 titres | API | DP-US |
| ~~Luso Livros~~ | — | **hors ligne** (DNS) | — | — |
| Baixe Livros | baixelivros.com.br | Mélange DP et douteux ; 403 aux robots | — | à exclure |
| Le Livros | — | Bibliothèque parallèle : **à exclure** | — | illégal |

## 7. Russe

| Source | URL | Contenu | Accès machine | Statut |
|---|---|---|---|---|
| **Lib.ru (Библиотека Мошкова)** + az.lib.ru | lib.ru, az.lib.ru | Le plus grand corpus russe en ligne, texte brut/HTML ; az.lib.ru = classiques d'avant 1917 | HTML, structure stable et facile à parser ; FB2/TXT | Mixte : az.lib.ru est DP, le reste (lib.ru) contient des traductions sous droits |
| **Викитека (Wikisource RU)** | ru.wikisource.org | Très riche pour les classiques, relus | API + ws-export | DP-CH/UE |
| ФЭБ (Fundamental'naya elektronnaya biblioteka) | feb-web.ru | Éditions académiques : Pouchkine, Lermontov, Tolstoï, folklore | HTML | DP + éditions savantes |
| Русская виртуальная библиотека | rvb.ru | Éditions académiques annotées (XVIIIe–XXe) | HTML | DP + appareil critique sous droits |
| ilibrary.ru (Библиотека Комарова) | ilibrary.ru | Classiques russes, HTML propre | HTML | DP |
| Tolstoy.ru — 90 volumes | tolstoy.ru/creativity/90-volume-collection-of-the-works/ | Œuvres complètes de Tolstoï, EPUB/FB2/PDF libres | HTML | Libre (mis dans le DP par le musée) |
| ImWerden | imwerden.de | Numérisations d'éditions russes (émigration, samizdat), PDF | HTML | DP + tolérance des ayants droit |
| Президентская библиотека | prlib.ru | Numérisations historiques | HTML | DP |
| НЭБ (rusneb.ru) | rusneb.ru | Bibliothèque nationale électronique ; 403 aux robots, compte pour le sous-droits | HTML | DP + PCN russe |
| klassika.ru | klassika.ru | Classiques HTML | HTML | DP |
| Gutenberg RU (Gutendex `languages=ru`) | — | ~100 titres | API | DP-US |
| Флибуста, Либрусек, LitMir, Royallib | — | Bibliothèques parallèles : **à exclure** | — | illégal |

---

## Priorités d'intégration suggérées

1. **Gutendex** (6 langues, JSON, dates des auteurs) et **Open Library / Internet Archive** (JSON).
2. **OPDS fonctionnels aujourd'hui** : Gutenberg, Ebooks libres et gratuits, BEQ, textos.info.
   À tester avec le User-Agent de l'app : BnR, Feedbooks, ManyBooks.
3. **Wikisource + ws-export** : une seule intégration pour les six langues, EPUB à la volée.
4. **Nos Livres** comme index FR, **Elejandría** ES, **Projeto Livro Livre** PT,
   **Викитека + az.lib.ru** RU, **TextGrid** DE : scrapers HTML simples.
5. Projekt Gutenberg-DE, HathiTrust, Google Livres : liens sortants seulement.
