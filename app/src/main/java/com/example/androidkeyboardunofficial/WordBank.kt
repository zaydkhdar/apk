package com.example.androidkeyboardunofficial

/** Built-in common words so suggestions work from the very first day. */
object WordBank {
    private val EN: List<String> = (
        "the be to of and a in that have it for not on with he as you do at this but his by from they we say her she " +
        "or an will my one all would there their what so up out if about who get which go me when make can like time " +
        "no just him know take people into year your good some could them see other than then now look only come its " +
        "over think also back after use two how our work first well way even new want because any these give day most " +
        "hello hi hey thanks thank please sorry yes okay ok morning night tonight tomorrow today yesterday love friend " +
        "happy great awesome beautiful amazing welcome congratulations birthday phone message call meeting email home " +
        "school sure maybe really actually probably definitely already always never sometimes together something " +
        "anything everything nothing someone everyone anyone different important interesting problem question answer " +
        "information between through before during without another around should might must where why while still " +
        "again very much many right going little great feel life world house water food week month home name help " +
        "start stop need try ask tell find keep leave put mean let show hear play run move live believe bring happen " +
        "write read learn change follow watch talk turn open close buy send wait working tonight lunch dinner " +
        "breakfast weekend friday monday tuesday wednesday thursday saturday sunday"
        ).split(" ")

    private val FR: List<String> = (
        "je tu il elle nous vous ils elles le la les un une des de du et est que qui dans pour pas sur avec ce cette " +
        "mais ou donc car si comme bien tres tout tous plus moins merci bonjour bonsoir salut oui non super content " +
        "heureux demain aujourd hier maintenant toujours jamais ensemble quelque chose rien maison travail ecole ami " +
        "amie amour famille temps jour nuit semaine mois annee je suis avoir etre faire aller voir savoir pouvoir " +
        "vouloir venir falloir devoir prendre donner parler aimer passer trouver mettre dire manger boire dormir " +
        "pourquoi quand comment combien beaucoup peu encore deja apres avant pendant entre sans sous chez vers " +
        "desole bientot bonne journee soiree matin midi"
        ).split(" ")

    fun complete(language: String, prefix: String, limit: Int): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        val list: List<String> = when (language) {
            "FR" -> FR
            "EN" -> EN
            else -> emptyList()
        }
        return list.filter { it.startsWith(p) && it != p }.distinct().take(limit)
    }

    fun starters(language: String): List<String> = when (language) {
        "FR" -> listOf("Je", "Le", "Merci")
        "AR" -> listOf("مرحبا", "شكرا", "السلام")
        else -> listOf("I", "The", "Thanks")
    }
}

/** Small, safe typo fixes applied when you finish a word. Backspace right after undoes them. */
object SmartText {
    private val TYPOS: Map<String, String> = mapOf(
        "i" to "I", "im" to "I'm", "ive" to "I've", "teh" to "the", "adn" to "and", "taht" to "that",
        "jsut" to "just", "wich" to "which", "becuase" to "because", "recieve" to "receive",
        "thier" to "their", "definately" to "definitely", "seperate" to "separate", "occured" to "occurred",
        "untill" to "until", "alot" to "a lot", "tommorow" to "tomorrow", "tomorow" to "tomorrow",
        "wierd" to "weird", "freind" to "friend", "beleive" to "believe", "dont" to "don't",
        "cant" to "can't", "wont" to "won't", "didnt" to "didn't", "doesnt" to "doesn't",
        "isnt" to "isn't", "wasnt" to "wasn't", "arent" to "aren't", "couldnt" to "couldn't",
        "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "havent" to "haven't",
        "thats" to "that's", "whats" to "what's", "youre" to "you're", "theyre" to "they're"
    )

    fun typoFix(typed: String): String? {
        if (typed.isEmpty()) return null
        val fixed = TYPOS[typed.lowercase()] ?: return null
        return when {
            typed.length > 1 && typed.all { it.isUpperCase() } -> fixed.uppercase()
            typed[0].isUpperCase() -> fixed.replaceFirstChar { it.uppercase() }
            else -> fixed
        }
    }
}
