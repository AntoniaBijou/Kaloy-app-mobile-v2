package com.kaloy.app.core.network

/**
 * Complete une adresse de media renvoyee par le serveur.
 *
 * POURQUOI CETTE FONCTION EXISTE
 *
 * Le serveur stockait des adresses absolues — « http://10.0.2.2:8087/... » —
 * ecrites en base au moment de l'envoi du fichier. Le nom d'hote devenait donc
 * une donnee persistee, alors qu'il depend du reseau : l'adresse Wi-Fi du poste
 * de developpement a change trois fois en une semaine, et chaque changement
 * rendait injoignables tous les medias deja enregistres. Le cas le plus net :
 * 10.0.2.2 est l'alias de l'hote vu depuis l'emulateur Android et ne designe
 * rien sur un telephone reel.
 *
 * Le serveur renvoie desormais un CHEMIN (« /uploads/chanson.m4a ») et c'est
 * l'application qui le prefixe avec [BASE_URL], qu'elle connait par
 * construction puisque c'est l'adresse a laquelle elle parle deja.
 *
 * Les adresses absolues restent acceptees telles quelles : les liens YouTube
 * en sont, les anciennes lignes en base aussi tant qu'elles n'ont pas ete
 * converties.
 */
fun urlMedia(valeur: String?): String? {
    if (valeur.isNullOrBlank()) return null
    val v = valeur.trim()
    return if (v.startsWith("/")) BASE_URL.trimEnd('/') + v else v
}
